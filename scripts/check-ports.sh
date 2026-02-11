#!/usr/bin/env bash
# Pre-flight port availability check for Hephaestus local development.
#
# Usage:
#   ./scripts/check-ports.sh          # Check all default ports
#   ./scripts/check-ports.sh --quiet  # Exit code only (0 = all free, 1 = conflict)
#
# Reads the same environment variables as the rest of the stack:
#   POSTGRES_PORT, KEYCLOAK_PORT, SERVER_PORT, INTELLIGENCE_SERVICE_PORT, WEBAPP_PORT

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/../server/application-server/.env"

# Source .env if it exists (same file Docker Compose and Spring Boot read)
if [[ -f "$ENV_FILE" ]]; then
    # Export only lines that look like VAR=VALUE (skip comments and blank lines)
    set -a
    # shellcheck disable=SC1090
    source <(grep -E '^\s*[A-Z_]+=.+' "$ENV_FILE" 2>/dev/null || true)
    set +a
fi

QUIET=false
if [[ "${1:-}" == "--quiet" || "${1:-}" == "-q" ]]; then
    QUIET=true
fi

# Define all ports to check as "name:port" pairs
SERVICES="PostgreSQL:${POSTGRES_PORT:-5432}
Keycloak:${KEYCLOAK_PORT:-8081}
Application server:${SERVER_PORT:-8080}
Intelligence service:${INTELLIGENCE_SERVICE_PORT:-8000}
Webapp (Vite):${WEBAPP_PORT:-4200}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

conflicts=0

check_port() {
    local name="$1"
    local port="$2"

    if lsof -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1; then
        if [[ "$QUIET" == false ]]; then
            local pids
            pids=$(lsof -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null | head -3 | tr '\n' ',' | sed 's/,$//')
            echo -e "  ${RED}OCCUPIED${NC}  :${port}  ${name}  (PIDs: ${pids})"
        fi
        conflicts=$((conflicts + 1))
    else
        if [[ "$QUIET" == false ]]; then
            echo -e "  ${GREEN}FREE${NC}      :${port}  ${name}"
        fi
    fi
}

if [[ "$QUIET" == false ]]; then
    echo ""
    echo "Hephaestus port availability check"
    echo "==================================="
    echo ""
fi

# Check for duplicate port assignments (portable — no associative arrays)
duplicates=0
seen_ports=""
seen_names=""
while IFS=':' read -r name port; do
    case "$seen_ports" in
        *":${port}:"*)
            if [[ "$QUIET" == false ]]; then
                # Find which service already claimed this port
                prev_name=""
                _sp="$seen_ports"
                _sn="$seen_names"
                while [[ -n "$_sp" ]]; do
                    _p="${_sp%%:*}"; _sp="${_sp#*:}"; _sp="${_sp#*:}"
                    _n="${_sn%%|*}"; _sn="${_sn#*|}"
                    if [[ "$_p" == "$port" ]]; then prev_name="$_n"; break; fi
                done
                echo -e "  ${YELLOW}DUPLICATE${NC} :${port}  ${name} conflicts with ${prev_name}"
            fi
            duplicates=$((duplicates + 1))
            ;;
    esac
    seen_ports="${seen_ports}:${port}:"
    seen_names="${seen_names}${name}|"
done <<< "$SERVICES"

if [[ "$duplicates" -gt 0 && "$QUIET" == false ]]; then
    echo ""
    echo -e "${YELLOW}Warning: Duplicate port assignments detected. Override one of them via environment variables.${NC}"
    echo ""
fi

while IFS=':' read -r name port; do
    check_port "$name" "$port"
done <<< "$SERVICES"

if [[ "$QUIET" == false ]]; then
    echo ""
    if [[ "$conflicts" -gt 0 ]]; then
        echo -e "${RED}${conflicts} port(s) already in use.${NC} Override with environment variables:"
        echo "  e.g., POSTGRES_PORT=15432 in server/application-server/.env"
        echo ""
    else
        echo -e "${GREEN}All ports are available.${NC}"
        echo ""
    fi
fi

exit "$conflicts"
