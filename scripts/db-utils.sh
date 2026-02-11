#!/bin/bash

# Database Utilities Script
# Provides modular database operations for development workflow
# Usage: ./db-utils.sh [command]
# Commands:
#   generate-erd                         - Generate ERD documentation only
#   draft-changelog                      - Generate changelog diff only
#   generate-models-intelligence-service - Introspect existing DB and generate Drizzle schema for intelligence service

set -eo pipefail  # Exit on any error, including pipeline failures

SCRIPT_DIR="$(dirname "$0")"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_SERVER_DIR="$PROJECT_ROOT/server/application-server"
SCRIPTS_DIR="$PROJECT_ROOT/scripts"
LOCAL_POSTGRES_SCRIPT="$SCRIPTS_DIR/local-postgres.sh"

POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"

DOCKER_AVAILABLE_CACHE=""

to_lower() {
    printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

if [[ -n "${HEPHAESTUS_DB_MODE:-}" ]]; then
    DB_MODE="$(to_lower "${HEPHAESTUS_DB_MODE}")"
else
    DB_MODE="docker"
fi

if [[ "$DB_MODE" != "docker" && "$DB_MODE" != "local" ]]; then
    echo "Unsupported database mode '$DB_MODE'. Use 'docker' or 'local'." >&2
    exit 1
fi

docker_available() {
    if [[ -n "$DOCKER_AVAILABLE_CACHE" ]]; then
        [[ "$DOCKER_AVAILABLE_CACHE" == "true" ]]
        return
    fi

    if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
        DOCKER_AVAILABLE_CACHE="true"
        return 0
    fi

    DOCKER_AVAILABLE_CACHE="false"
    return 1
}

# Color codes for better output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
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

if [[ "$DB_MODE" == "docker" ]] && ! docker_available; then
    log_warning "Docker is not available; switching database utilities to local mode."
    DB_MODE="local"
fi

ensure_local_postgres_script() {
    if [[ ! -x "$LOCAL_POSTGRES_SCRIPT" ]]; then
        log_error "Local PostgreSQL helper not found or not executable at '$LOCAL_POSTGRES_SCRIPT'. Run 'scripts/codex-setup.sh' to install prerequisites."
        exit 1
    fi
}

postgres_data_dir() {
    if [[ "$DB_MODE" == "docker" ]]; then
        echo "$APP_SERVER_DIR/postgres-data"
    else
        echo "$APP_SERVER_DIR/postgres-data-local"
    fi
}

wait_for_postgres_ready() {
    local retries=30
    local count=0

    while [ $count -lt $retries ]; do
        if command -v pg_isready >/dev/null 2>&1; then
            if pg_isready -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" >/dev/null 2>&1; then
                log_success "PostgreSQL is ready!"
                return 0
            fi
        elif [[ "$DB_MODE" == "docker" ]]; then
            # Use port 5432 inside the container — POSTGRES_PORT is the host-side mapping only
            if (cd "$APP_SERVER_DIR" && docker compose exec postgres pg_isready -h localhost -p 5432 >/dev/null 2>&1); then
                log_success "PostgreSQL is ready!"
                return 0
            fi
        fi

        count=$((count + 1))
        echo -n "."
        sleep 1
    done

    log_error "PostgreSQL failed to become ready after ${retries} seconds"
    exit 1
}

is_postgres_running() {
    if [[ "${CI:-false}" == "true" ]]; then
        pg_isready -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" >/dev/null 2>&1
        return $?
    fi

    if [[ "$DB_MODE" == "local" ]]; then
        ensure_local_postgres_script
        "$LOCAL_POSTGRES_SCRIPT" status >/dev/null 2>&1
        return $?
    fi

    # Use subshell to avoid pipefail affecting this check
    # The function is meant to return false (non-zero) when postgres is not running
    local output
    output="$(cd "$APP_SERVER_DIR" && docker compose ps postgres 2>/dev/null)" || return 1
    echo "$output" | grep -q "Up"
}

# Check if we're in the right directory
check_environment() {
    if [[ ! -f "$APP_SERVER_DIR/pom.xml" ]]; then
        log_error "Application server not found. Please run this script from the project root."
        exit 1
    fi
    
    if [[ ! -f "$SCRIPTS_DIR/generate-mermaid-erd.ts" ]]; then
        log_error "ERD generation script not found."
        exit 1
    fi

    if ! node -e "require.resolve('tsx')" >/dev/null 2>&1; then
        log_error "Missing node dependency 'tsx'. Run 'npm install' before generating the ERD."
        exit 1
    fi

    if ! node -e "require.resolve('pg')" >/dev/null 2>&1; then
        log_error "Missing node dependency 'pg'. Run 'npm install' before generating the ERD."
        exit 1
    fi
}

# Check if PostgreSQL data directory needs cleanup
check_postgres_data() {
    if [[ "$DB_MODE" != "docker" ]]; then
        return 0
    fi

    cd "$APP_SERVER_DIR"

    if [[ -d "postgres-data" ]]; then
        # Check if essential PostgreSQL files exist
        if [[ ! -f "postgres-data/PG_VERSION" ]] || [[ ! -f "postgres-data/postgresql.conf" ]]; then
            log_warning "PostgreSQL data directory exists but appears corrupted"
            log_info "Cleaning up corrupted data directory..."
            rm -rf postgres-data
            log_success "Corrupted data directory cleaned up"
        fi
    fi
}

# Start PostgreSQL and wait for it to be ready
start_postgres() {
    # In CI environments, PostgreSQL service is already running
    if [[ "${CI:-false}" == "true" ]]; then
        log_info "CI environment detected, using existing PostgreSQL service..."
        wait_for_postgres_ready
        return 0
    fi

    if [[ "$DB_MODE" == "local" ]]; then
        log_info "Starting local PostgreSQL instance..."
        ensure_local_postgres_script
        "$LOCAL_POSTGRES_SCRIPT" start
        wait_for_postgres_ready
        return 0
    fi

    # Local development environment - use Docker
    log_info "Starting PostgreSQL container..."
    cd "$APP_SERVER_DIR"

    # Check and cleanup corrupted data if needed
    check_postgres_data

    docker compose up -d postgres

    log_info "Waiting for PostgreSQL to be ready..."
    wait_for_postgres_ready
}

# Stop PostgreSQL
stop_postgres() {
    # In CI environments, don't try to stop the service
    if [[ "${CI:-false}" == "true" ]]; then
        log_info "CI environment detected, leaving PostgreSQL service running..."
        return 0
    fi

    if [[ "$DB_MODE" == "local" ]]; then
        ensure_local_postgres_script
        "$LOCAL_POSTGRES_SCRIPT" stop
        return 0
    fi

    log_info "Stopping PostgreSQL container..."
    cd "$APP_SERVER_DIR"
    docker compose down postgres
}

# Apply Liquibase migrations
apply_migrations() {
    log_info "Applying Liquibase migrations..."
    cd "$APP_SERVER_DIR"
    # Explicitly set Spring profiles to avoid specs profile (which uses H2)
    # Use local,dev profiles for database operations
    SPRING_PROFILES_ACTIVE=local,dev ./mvnw liquibase:update -Dpostgres.port="$POSTGRES_PORT"
}

# Database credentials (configurable via environment variables)
DB_NAME="${POSTGRES_DB:-hephaestus}"
DB_USER="${POSTGRES_USER:-root}"
DB_PASSWORD="${POSTGRES_PASSWORD:-root}"

# Generate ERD documentation
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

# Generate Drizzle schema for intelligence service
generate_intelligence_service_models() {
    log_info "Generating Drizzle schema for intelligence service..."
    cd "$PROJECT_ROOT"
    npm run db:generate-models:intelligence-service
    log_success "Drizzle schema generated for intelligence service (see server/intelligence-service/src/shared/db)"
}

# Generate changelog diff with database backup/restore
generate_changelog_diff() {
    log_info "Generating changelog diff..."
    cd "$APP_SERVER_DIR"
    
    # Define changelog file path
    local changelog_file="src/main/resources/db/changelog_new.xml"
    
    # Remove any existing changelog file to ensure clean state
    if [[ -f "$changelog_file" ]]; then
        rm "$changelog_file"
    fi
    
    # In CI environments with external Docker PostgreSQL, skip backup/restore
    if [[ "${CI:-false}" == "true" ]]; then
        log_info "CI environment detected - using external PostgreSQL container"
        log_info "Ensuring PostgreSQL is ready..."
        wait_for_postgres_ready
        
        # Apply migrations to fresh database
        apply_migrations
        
        # Generate changelog diff
        log_info "Generating changelog diff..."
        SPRING_PROFILES_ACTIVE=local,dev ./mvnw liquibase:diff -Dpostgres.port="$POSTGRES_PORT"
    else
        # Local development - backup and restore database state
        log_info "Backing up current database state..."
        stop_postgres
        local data_dir
        data_dir="$(postgres_data_dir)"
        # Use unique temp dir name with PID to avoid conflicts
        local temp_dir="${data_dir}-temp-$$"
        local backup_created=false
        
        # Cleanup function to restore database state on failure
        cleanup_changelog_diff() {
            if [[ "$backup_created" == "true" && -d "$temp_dir" ]]; then
                log_warning "Restoring database state after failure..."
                stop_postgres 2>/dev/null || true
                rm -rf "$data_dir" 2>/dev/null || true
                mv "$temp_dir" "$data_dir"
                log_info "Database state restored."
            fi
        }
        
        # Set trap to cleanup on error
        trap cleanup_changelog_diff ERR
        
        if [[ -d "$data_dir" ]]; then
            mv "$data_dir" "$temp_dir"
            backup_created=true
        fi

        # Start fresh database and apply migrations
        start_postgres
        apply_migrations
        
        # Generate changelog diff
        log_info "Generating changelog diff..."
        # Explicitly set Spring profiles to avoid specs profile (which uses H2)
        # Use local,dev profiles for database operations
        SPRING_PROFILES_ACTIVE=local,dev ./mvnw liquibase:diff -Dpostgres.port="$POSTGRES_PORT"
        
        # Restore original database state
        log_info "Restoring original database state..."
        stop_postgres
        rm -rf "$data_dir"
        if [[ -d "$temp_dir" ]]; then
            mv "$temp_dir" "$data_dir"
        fi
        
        # Clear the trap after successful completion
        trap - ERR
    fi
    
    # Check if changelog file was actually generated
    if [[ -f "$changelog_file" ]]; then
        log_success "Changelog diff generated at '$changelog_file'"
    else
        log_info "No database changes detected - no changelog file generated"
        log_info "This means your current schema is already up-to-date with your entity definitions"
    fi
}

# Generate ERD only (ensures database is running with up-to-date schema)
cmd_generate_erd() {
    log_info "🚀 Starting ERD generation..."
    check_environment
    
    # Ensure PostgreSQL is running and ready
    cd "$APP_SERVER_DIR"
    if [[ "${CI:-false}" == "true" ]]; then
        # In CI, PostgreSQL container is started externally
        log_info "CI environment detected - using external PostgreSQL container"
        wait_for_postgres_ready
        apply_migrations
    elif ! is_postgres_running; then
        log_warning "PostgreSQL is not running. Starting it now..."
        start_postgres
        apply_migrations
    elif [[ "$DB_MODE" == "local" ]]; then
        apply_migrations
    else
        log_info "Skipping migrations in local Docker mode (assuming DB is up-to-date)"
    fi

    generate_erd
    log_success "🎉 ERD generation completed successfully!"
}

# Generate changelog diff only
cmd_draft_changelog() {
    log_info "🚀 Starting changelog diff generation..."
    check_environment
    generate_changelog_diff
    log_success "🎉 Changelog diff process completed!"
}

# Generate Drizzle schema for intelligence service
cmd_generate_db_models_intelligence_service() {
    log_info "🚀 Starting Drizzle schema generation for intelligence service..."
    check_environment
    
    # Ensure PostgreSQL is running and ready
    cd "$APP_SERVER_DIR"
    if [[ "${CI:-false}" == "true" ]]; then
        # In CI, PostgreSQL container is started externally
        log_info "CI environment detected - using external PostgreSQL container"
        wait_for_postgres_ready
        apply_migrations
    elif ! is_postgres_running; then
        log_warning "PostgreSQL is not running. Starting it now..."
        start_postgres
        apply_migrations
    elif [[ "$DB_MODE" == "local" ]]; then
        apply_migrations
    else
        log_info "Skipping migrations in local Docker mode (assuming DB is up-to-date)"
    fi
    
    generate_intelligence_service_models
    log_success "🎉 Drizzle schema generation completed successfully!"
}

# Show usage information
show_usage() {
    cat << EOF
Database Utilities Script

Usage: $0 [command]

Commands:
  generate-erd                      Generate ERD documentation only (requires running database)
  draft-changelog                   Generate changelog diff only
  generate-models-intelligence-service  Generate Drizzle schema for intelligence service
  help                             Show this help message

Examples:
  $0 generate-erd                        # Quick ERD generation during development
  $0 draft-changelog                     # Generate migration before PR
  $0 generate-models-intelligence-service  # Generate Drizzle schema from current DB

EOF
}

# Main command dispatcher
main() {
    case "${1:-}" in
        "generate-erd")
            cmd_generate_erd
            ;;
        "draft-changelog")
            cmd_draft_changelog
            ;;
        "generate-models-intelligence-service")
            # Use the dedicated command function for consistency
            cmd_generate_db_models_intelligence_service
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

# Run main function with all arguments
main "$@"
