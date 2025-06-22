#!/bin/bash

# Database Utilities Script
# Provides modular database operations for development workflow
# Usage: ./db-utils.sh [command]
# Commands:
#   generate-erd                         - Generate ERD documentation only
#   draft-changelog                      - Generate changelog diff only
#   generate-models-intelligence-service - Generate SQLAlchemy models for intelligence service

set -e  # Exit on any error

SCRIPT_DIR="$(dirname "$0")"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_SERVER_DIR="$PROJECT_ROOT/server/application-server"
SCRIPTS_DIR="$PROJECT_ROOT/scripts"

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

# Check if we're in the right directory
check_environment() {
    if [[ ! -f "$APP_SERVER_DIR/pom.xml" ]]; then
        log_error "Application server not found. Please run this script from the project root."
        exit 1
    fi
    
    if [[ ! -f "$SCRIPTS_DIR/generate_mermaid_erd.py" ]]; then
        log_error "ERD generation script not found."
        exit 1
    fi
}

# Check if PostgreSQL data directory needs cleanup
check_postgres_data() {
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
        
        # Just verify PostgreSQL is ready on localhost
        local retries=30
        local count=0
        
        while [ $count -lt $retries ]; do
            if pg_isready -h localhost -p 5432 >/dev/null 2>&1; then
                log_success "PostgreSQL is ready!"
                return 0
            fi
            
            count=$((count + 1))
            echo -n "."
            sleep 1
        done
        
        log_error "PostgreSQL failed to become ready after ${retries} seconds"
        exit 1
    fi
    
    # Local development environment - use Docker
    log_info "Starting PostgreSQL container..."
    cd "$APP_SERVER_DIR"
    
    # Check and cleanup corrupted data if needed
    check_postgres_data
    
    docker compose up -d postgres
    
    log_info "Waiting for PostgreSQL to be ready..."
    local retries=30
    local count=0
    
    while [ $count -lt $retries ]; do
        if docker compose exec postgres pg_isready -h localhost -p 5432 >/dev/null 2>&1; then
            log_success "PostgreSQL is ready!"
            return 0
        fi
        
        count=$((count + 1))
        echo -n "."
        sleep 1
    done
    
    log_error "PostgreSQL failed to become ready after ${retries} seconds"
    log_info "Checking container status..."
    docker compose ps postgres
    log_info "Recent logs:"
    docker compose logs --tail=10 postgres
    exit 1
}

# Stop PostgreSQL
stop_postgres() {
    # In CI environments, don't try to stop the service
    if [[ "${CI:-false}" == "true" ]]; then
        log_info "CI environment detected, leaving PostgreSQL service running..."
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
    mvn liquibase:update
}

# Generate ERD documentation
generate_erd() {
    log_info "Generating ERD documentation..."
    cd "$SCRIPTS_DIR"
    
    # Check if Python dependencies are available, install if needed
    if ! python3 -c "import psycopg" 2>/dev/null; then
        log_info "Installing Python dependencies..."
        python3 -m pip install -r requirements.txt --break-system-packages --quiet
    fi
    
    python3 generate_mermaid_erd.py \
        jdbc:postgresql://localhost:5432/hephaestus \
        root \
        root \
        ../docs/dev/database/schema.mmd
    
    log_success "ERD documentation updated at 'docs/dev/database/schema.mmd'"
}

# Generate SQLAlchemy models for intelligence service
generate_intelligence_service_models() {
    log_info "Generating SQLAlchemy models for intelligence service..."
    
    local intelligence_service_dir="$PROJECT_ROOT/server/intelligence-service"
    local generate_script="$intelligence_service_dir/scripts/generate_db_models.py"
    
    # Check if intelligence service directory exists
    if [[ ! -d "$intelligence_service_dir" ]]; then
        log_error "Intelligence service directory not found at: $intelligence_service_dir"
        exit 1
    fi
    
    # Check if generation script exists
    if [[ ! -f "$generate_script" ]]; then
        log_error "Model generation script not found at: $generate_script"
        exit 1
    fi
    
    cd "$intelligence_service_dir"
    
    # Use poetry to run the generation script
    poetry run python scripts/generate_db_models.py
    poetry run black app/db/models_gen.py
    
    log_success "SQLAlchemy models generated for intelligence service"
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
    
    # Backup current database state
    log_info "Backing up current database state..."
    stop_postgres
    if [[ -d "postgres-data" ]]; then
        mv postgres-data postgres-data-temp
    fi
    
    # Start fresh database and apply migrations
    start_postgres
    apply_migrations
    
    # Generate changelog diff
    log_info "Generating changelog diff..."
    mvn liquibase:diff
    
    # Restore original database state
    log_info "Restoring original database state..."
    stop_postgres
    rm -rf postgres-data
    if [[ -d "postgres-data-temp" ]]; then
        mv postgres-data-temp postgres-data
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
    
    # Ensure PostgreSQL is running
    cd "$APP_SERVER_DIR"
    if ! docker compose ps postgres | grep -q "Up"; then
        log_warning "PostgreSQL is not running. Starting it now..."
        start_postgres
    fi
    
    # Always ensure schema is up-to-date
    apply_migrations
    
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

# Generate SQLAlchemy models for intelligence service
cmd_generate_db_models_intelligence_service() {
    log_info "🚀 Starting SQLAlchemy model generation for intelligence service..."
    check_environment
    
    # Ensure PostgreSQL is running
    cd "$APP_SERVER_DIR"
    if ! docker compose ps postgres | grep -q "Up"; then
        log_warning "PostgreSQL is not running. Starting it now..."
        start_postgres
    fi
    
    # Always ensure schema is up-to-date
    apply_migrations
    
    generate_intelligence_service_models
    log_success "🎉 SQLAlchemy model generation completed successfully!"
}

# Show usage information
show_usage() {
    cat << EOF
Database Utilities Script

Usage: $0 [command]

Commands:
  generate-erd                      Generate ERD documentation only (requires running database)
  draft-changelog                   Generate changelog diff only
  generate-models-intelligence-service  Generate SQLAlchemy models for intelligence service
  help                             Show this help message

Examples:
  $0 generate-erd                        # Quick ERD generation during development
  $0 draft-changelog                     # Generate migration before PR
  $0 generate-models-intelligence-service  # Generate SQLAlchemy models from current schema

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
