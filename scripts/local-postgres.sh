#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_SERVER_DIR="$PROJECT_ROOT/server/application-server"
DATA_DIR="${APP_SERVER_DIR}/postgres-data-local"
LOG_FILE="${DATA_DIR}/postgres.log"
PG_HOST="${POSTGRES_HOST:-127.0.0.1}"
PG_PORT="${POSTGRES_PORT:-5432}"
PG_DATABASE="${POSTGRES_DB:-hephaestus}"
PG_USER="${POSTGRES_USER:-root}"
PG_PASSWORD="${POSTGRES_PASSWORD:-root}"

TEST_DB_NAME="${HEPHAESTUS_TEST_DB:-hephaestus_test}"
TEST_DB_USER="${HEPHAESTUS_TEST_DB_USER:-test}"
TEST_DB_PASSWORD="${HEPHAESTUS_TEST_DB_PASSWORD:-test}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}" >&2
}

if sed --version >/dev/null 2>&1; then
    sed_inplace() {
        sed -i "$@"
    }
else
    sed_inplace() {
        # BSD sed requires an explicit (possibly empty) backup suffix
        sed -i '' "$@"
    }
fi

ensure_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        log_error "Required command '$1' is not available. Run 'scripts/codex-setup.sh' to install prerequisites."
        exit 1
    fi
}

ensure_runner() {
    if [ "$USE_SYSTEM_POSTGRES_USER" -eq 0 ]; then
        return 0
    fi

    if command -v runuser >/dev/null 2>&1 || command -v su >/dev/null 2>&1; then
        return 0
    fi

    log_error "Neither 'runuser' nor 'su' is available to manage the postgres user."
    exit 1
}

find_pg_bindir() {
    if command -v pg_ctl >/dev/null 2>&1; then
        dirname "$(command -v pg_ctl)"
        return
    fi

    if [ -d /usr/lib/postgresql ]; then
        local found
        found="$(find /usr/lib/postgresql -maxdepth 3 -type f -name pg_ctl -print -quit)"
        if [ -n "$found" ]; then
            dirname "$found"
            return
        fi
    fi

    echo ""
}

PG_BINDIR="$(find_pg_bindir)"
if [ -z "$PG_BINDIR" ]; then
    log_error "Could not locate PostgreSQL binaries. Ensure PostgreSQL is installed (run 'scripts/codex-setup.sh')."
    exit 1
fi

PATH_WITH_PG="$PG_BINDIR:$PATH"

if id -u postgres >/dev/null 2>&1; then
    USE_SYSTEM_POSTGRES_USER=1
else
    USE_SYSTEM_POSTGRES_USER=0
fi

run_as_postgres() {
    if [ "$USE_SYSTEM_POSTGRES_USER" -eq 0 ]; then
        env PATH="$PATH_WITH_PG" "$@"
        return
    fi

    if command -v runuser >/dev/null 2>&1; then
        runuser -u postgres -- env PATH="$PATH_WITH_PG" "$@"
        return
    fi

    local quoted=( )
    local arg
    for arg in "$@"; do
        quoted+=("$(printf '%q' "$arg")")
    done

    # shellcheck disable=SC2086 # intentional splitting for composed command string
    su - postgres -c "PATH=$PATH_WITH_PG ${quoted[*]}"
}

pg_isready_cmd() {
    if command -v pg_isready >/dev/null 2>&1; then
        pg_isready "$@"
    else
        "${PG_BINDIR}/pg_isready" "$@"
    fi
}

configure_postgresql_conf() {
    local conf_file="$DATA_DIR/postgresql.conf"

    if grep -q "^#\?listen_addresses" "$conf_file"; then
        sed_inplace "s/^#\?listen_addresses.*/listen_addresses = '127.0.0.1,localhost'/" "$conf_file"
    else
        echo "listen_addresses = '127.0.0.1,localhost'" >>"$conf_file"
    fi

    if grep -q "^#\?port" "$conf_file"; then
        sed_inplace "s/^#\?port.*/port = ${PG_PORT}/" "$conf_file"
    else
        echo "port = ${PG_PORT}" >>"$conf_file"
    fi
}

configure_pg_hba() {
    cat >"$DATA_DIR/pg_hba.conf" <<EOF
# TYPE  DATABASE        USER            ADDRESS                 METHOD
local   all             postgres                                peer
local   all             all                                     trust
host    all             all             127.0.0.1/32            scram-sha-256
host    all             all             ::1/128                 scram-sha-256
EOF
}

ensure_data_directory() {
    if [ ! -f "$DATA_DIR/PG_VERSION" ]; then
        log_info "Initializing local PostgreSQL cluster in '$DATA_DIR'..."
        mkdir -p "$DATA_DIR"
        if [ "$USE_SYSTEM_POSTGRES_USER" -eq 1 ]; then
            chown postgres:postgres "$DATA_DIR"
        fi
        run_as_postgres "$PG_BINDIR/initdb" -D "$DATA_DIR" --encoding=UTF8 --locale=C.UTF-8 >/dev/null
        configure_postgresql_conf
        configure_pg_hba
        log_success "Local PostgreSQL cluster initialized."
    fi
}

is_running() {
    pg_isready_cmd -h "$PG_HOST" -p "$PG_PORT" >/dev/null 2>&1
}

wait_for_ready() {
    local retries=30
    local attempt=0

    while [ $attempt -lt $retries ]; do
        if is_running; then
            return 0
        fi
        sleep 1
        attempt=$((attempt + 1))
    done

    log_error "PostgreSQL did not become ready on ${PG_HOST}:${PG_PORT}"
    exit 1
}

ensure_role() {
    local role_name="$1"
    local password="$2"
    local superuser_flag="$3"

    local privilege_clause
    if [[ "$superuser_flag" == "superuser" ]]; then
        privilege_clause="SUPERUSER"
    else
        privilege_clause="NOSUPERUSER"
    fi

    local escaped_role_literal
    local escaped_password
    local escaped_role_ident
    escaped_role_literal="${role_name//\'/\'\'}"
    escaped_password="${password//\'/\'\'}"
    escaped_role_ident="${role_name//\"/\"\"}"

    local role_exists
    role_exists="$(run_as_postgres "$PG_BINDIR/psql" -p "$PG_PORT" -d postgres -Atqc "SELECT 1 FROM pg_roles WHERE rolname='${escaped_role_literal}'")"

    if [[ "$role_exists" != "1" ]]; then
        run_as_postgres "$PG_BINDIR/psql" -p "$PG_PORT" -d postgres <<SQL >/dev/null
CREATE ROLE "${escaped_role_ident}" WITH LOGIN ${privilege_clause} PASSWORD '${escaped_password}';
SQL
        log_info "Created PostgreSQL role '${role_name}'."
    else
        run_as_postgres "$PG_BINDIR/psql" -p "$PG_PORT" -d postgres <<SQL >/dev/null
ALTER ROLE "${escaped_role_ident}" WITH LOGIN ${privilege_clause} PASSWORD '${escaped_password}';
SQL
    fi
}

ensure_database() {
    local database_name="$1"
    local owner_name="$2"

    local escaped_db_literal="${database_name//\'/\'\'}"
    local escaped_db_ident="${database_name//\"/\"\"}"
    local escaped_owner_ident="${owner_name//\"/\"\"}"

    local db_exists
    db_exists="$(run_as_postgres "$PG_BINDIR/psql" -p "$PG_PORT" -d postgres -Atqc "SELECT 1 FROM pg_database WHERE datname='${escaped_db_literal}'")"

    if [[ "$db_exists" != "1" ]]; then
        run_as_postgres "$PG_BINDIR/psql" -p "$PG_PORT" -d postgres <<SQL >/dev/null
CREATE DATABASE "${escaped_db_ident}" OWNER "${escaped_owner_ident}";
SQL
        log_info "Created PostgreSQL database '${database_name}'."
    else
        run_as_postgres "$PG_BINDIR/psql" -p "$PG_PORT" -d postgres <<SQL >/dev/null
ALTER DATABASE "${escaped_db_ident}" OWNER TO "${escaped_owner_ident}";
SQL
    fi
}

ensure_default_databases() {
    ensure_role "$PG_USER" "$PG_PASSWORD" superuser
    log_info "Role '${PG_USER}' is present."
    ensure_database "$PG_DATABASE" "$PG_USER"
    log_info "Database '${PG_DATABASE}' owned by '${PG_USER}'."

    ensure_role "$TEST_DB_USER" "$TEST_DB_PASSWORD" superuser
    log_info "Role '${TEST_DB_USER}' is present."
    ensure_database "$TEST_DB_NAME" "$TEST_DB_USER"
    log_info "Database '${TEST_DB_NAME}' owned by '${TEST_DB_USER}'."
}

start_postgres() {
    ensure_runner
    ensure_data_directory

    if is_running; then
        log_info "Local PostgreSQL already running on ${PG_HOST}:${PG_PORT}."
        return 0
    fi

    log_info "Starting local PostgreSQL on ${PG_HOST}:${PG_PORT}..."
    run_as_postgres "$PG_BINDIR/pg_ctl" -D "$DATA_DIR" -l "$LOG_FILE" start >/dev/null
    wait_for_ready
    ensure_default_databases
    log_success "Local PostgreSQL started."
}

stop_postgres() {
    ensure_runner
    if ! is_running; then
        log_info "Local PostgreSQL is not running."
        return 0
    fi

    log_info "Stopping local PostgreSQL..."
    run_as_postgres "$PG_BINDIR/pg_ctl" -D "$DATA_DIR" stop -m fast >/dev/null
    log_success "Local PostgreSQL stopped."
}

status_postgres() {
    if is_running; then
        log_success "Local PostgreSQL is running on ${PG_HOST}:${PG_PORT}."
        return 0
    else
        log_warning "Local PostgreSQL is not running."
        return 1
    fi
}

restart_postgres() {
    log_info "Restarting local PostgreSQL..."
    stop_postgres
    start_postgres
}

usage() {
    cat <<EOF
Local PostgreSQL helper

Usage: $0 <command>

Commands:
  start      Start the local PostgreSQL instance
  stop       Stop the local PostgreSQL instance
  restart    Restart the local PostgreSQL instance
  status     Print whether PostgreSQL is running
EOF
}

case "${1:-}" in
    start)
        start_postgres
        ;;
    stop)
        stop_postgres
        ;;
    restart)
        restart_postgres
        ;;
    status)
        status_postgres
        ;;
    ""|-h|--help|help)
        usage
        ;;
    *)
        log_error "Unknown command: $1"
        usage
        exit 1
        ;;
esac
