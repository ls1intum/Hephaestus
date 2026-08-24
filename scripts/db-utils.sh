#!/bin/bash

set -eo pipefail

SCRIPT_DIR="$(dirname "$0")"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_SERVER_DIR="$PROJECT_ROOT/server"
SCRIPTS_DIR="$PROJECT_ROOT/scripts"

ENV_FILE="$APP_SERVER_DIR/.env"
if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"

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
    echo -e "${RED}❌ $1${NC}"
}

require_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        log_error "Docker is required for local database utilities. Install Docker, then retry."
        exit 1
    fi

    if ! docker info >/dev/null 2>&1; then
        log_error "Docker is installed but unavailable. Start the Docker daemon, then retry."
        exit 1
    fi

    if ! docker compose version >/dev/null 2>&1; then
        log_error "Docker Compose is required for local database utilities. Install the Compose plugin, then retry."
        exit 1
    fi
}

wait_for_postgres_ready() {
    local retries=30
    local count=0

    while [ $count -lt $retries ]; do
        if pg_isready -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" >/dev/null 2>&1; then
            log_success "PostgreSQL is ready!"
            return 0
        fi

        count=$((count + 1))
        echo -n "."
        sleep 1
    done

    log_error "PostgreSQL failed to become ready after ${retries} seconds"
    exit 1
}

check_environment() {
    if [[ ! -f "$APP_SERVER_DIR/pom.xml" ]]; then
        log_error "Application server not found at '$APP_SERVER_DIR'."
        exit 1
    fi
    
    if [[ ! -f "$SCRIPTS_DIR/generate-mermaid-erd.ts" ]]; then
        log_error "ERD generation script not found."
        exit 1
    fi

    if [[ "${CI:-false}" != "true" ]]; then
        require_docker
    elif ! command -v pg_isready >/dev/null 2>&1; then
        log_error "CI database utilities require 'pg_isready'."
        exit 1
    fi
}

check_erd_dependencies() {
    if ! node -e "require.resolve('tsx')" >/dev/null 2>&1; then
        log_error "Missing node dependency 'tsx'. Run 'pnpm install' before generating the ERD."
        exit 1
    fi

    if ! node -e "require.resolve('pg')" >/dev/null 2>&1; then
        log_error "Missing node dependency 'pg'. Run 'pnpm install' before generating the ERD."
        exit 1
    fi

}

start_postgres() {
    if [[ "${CI:-false}" == "true" ]]; then
        log_info "CI environment detected, using existing PostgreSQL service..."
        wait_for_postgres_ready
        return 0
    fi

    log_info "Starting PostgreSQL container..."
    cd "$APP_SERVER_DIR"
    docker compose up -d --wait postgres
}

stop_postgres() {
    if [[ "${CI:-false}" == "true" ]]; then
        log_info "CI environment detected, leaving PostgreSQL service running..."
        return 0
    fi

    log_info "Stopping PostgreSQL container..."
    cd "$APP_SERVER_DIR"
    docker compose down postgres
}

apply_migrations() {
    log_info "Applying Liquibase migrations..."
    cd "$APP_SERVER_DIR"
    SPRING_PROFILES_ACTIVE=local,dev ./mvnw liquibase:update -P'!quick' -Dpostgres.port="$POSTGRES_PORT"
}

apply_migrations_and_generate_diff() {
    log_info "Applying Liquibase migrations and generating changelog diff..."
    cd "$APP_SERVER_DIR"
    SPRING_PROFILES_ACTIVE=local,dev ./mvnw liquibase:update liquibase:diff -P'!quick' -Dpostgres.port="$POSTGRES_PORT"
}

DB_NAME="${POSTGRES_DB:-hephaestus}"
DB_USER="${POSTGRES_USER:-root}"
DB_PASSWORD="${POSTGRES_PASSWORD:-root}"

generate_erd() {
    log_info "Generating ERD documentation..."
    cd "$SCRIPTS_DIR"

    node --import tsx generate-mermaid-erd.ts \
        "jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${DB_NAME}" \
        "$DB_USER" \
        "$DB_PASSWORD" \
        ../docs/contributor/erd/schema.mmd
    
    log_success "ERD documentation updated at 'docs/contributor/erd/schema.mmd'"
}

generate_changelog_diff() {
    log_info "Generating changelog diff..."
    cd "$APP_SERVER_DIR"
    
    local changelog_file="src/main/resources/db/changelog_new.xml"
    
    if [[ -f "$changelog_file" ]]; then
        rm "$changelog_file"
    fi
    
    if [[ "${CI:-false}" == "true" ]]; then
        log_info "CI environment detected - using external PostgreSQL container"
        log_info "Ensuring PostgreSQL is ready..."
        wait_for_postgres_ready
        
        apply_migrations_and_generate_diff
    else
        log_info "Backing up current database state..."
        stop_postgres
        local data_dir
        data_dir="$APP_SERVER_DIR/postgres-data"
        local temp_dir="${data_dir}-temp-$$"
        local backup_created=false
        
        cleanup_changelog_diff() {
            if [[ "$backup_created" == "true" && -d "$temp_dir" ]]; then
                log_warning "Restoring database state after failure..."
                stop_postgres 2>/dev/null || true
                rm -rf "$data_dir" 2>/dev/null || true
                mv "$temp_dir" "$data_dir"
                log_info "Database state restored."
            fi
        }
        
        trap cleanup_changelog_diff ERR
        
        if [[ -d "$data_dir" ]]; then
            mv "$data_dir" "$temp_dir"
            backup_created=true
        fi

        start_postgres
        apply_migrations_and_generate_diff
        
        log_info "Restoring original database state..."
        stop_postgres
        rm -rf "$data_dir"
        if [[ -d "$temp_dir" ]]; then
            mv "$temp_dir" "$data_dir"
        fi
        
        trap - ERR
    fi
    
    if [[ -f "$changelog_file" ]]; then
        log_success "Changelog diff generated at '$changelog_file'"
    else
        log_info "No database changes detected - no changelog file generated"
        log_info "This means your current schema is already up-to-date with your entity definitions"
    fi
}

cmd_generate_erd() {
    log_info "🚀 Starting ERD generation..."
    check_environment
    check_erd_dependencies
    
    cd "$APP_SERVER_DIR"
    start_postgres
    apply_migrations

    generate_erd
    log_success "🎉 ERD generation completed successfully!"
}

cmd_draft_changelog() {
    log_info "🚀 Starting changelog diff generation..."
    check_environment
    generate_changelog_diff
    log_success "🎉 Changelog diff process completed!"
}

show_usage() {
    cat << EOF
Database Utilities Script

Usage: $0 [command]

Commands:
  generate-erd                      Generate ERD documentation only (requires running database)
  draft-changelog                   Generate changelog diff only
  help                             Show this help message

Examples:
  $0 generate-erd                        # Quick ERD generation during development
  $0 draft-changelog                     # Generate migration before PR

EOF
}

main() {
    case "${1:-}" in
        "generate-erd")
            cmd_generate_erd
            ;;
        "draft-changelog")
            cmd_draft_changelog
            ;;
        "help"|"-h"|"--help")
            show_usage
            ;;
        "")
            log_error "No command specified."
            show_usage
            exit 1
            ;;
        *)
            log_error "Unknown command: $1"
            show_usage
            exit 1
            ;;
    esac
}

main "$@"
