#!/usr/bin/env bash
# Pre-flight port availability check for Hephaestus local development.
#
# Usage:
#   ./scripts/check-ports.sh          # Check all default ports
#   ./scripts/check-ports.sh --quiet  # Exit code only (0 = all free, 1 = conflicts found)
#
# Reads the same environment variables as the rest of the stack:
#   POSTGRES_PORT, KEYCLOAK_PORT, SERVER_PORT, INTELLIGENCE_SERVICE_PORT, WEBAPP_PORT

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/../server/application-server/.env"

# Source .env if it exists (same file Docker Compose and Spring Boot read).
# Uses eval instead of process substitution because `source <(...)` does not
# retain variables in Bash 3.2 (macOS default).
if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    eval "$(grep -E '^\s*[A-Z_]+=.+' "$ENV_FILE" 2>/dev/null || true)"
    set +a
fi

QUIET=false
if [[ "${1:-}" == "--quiet" || "${1:-}" == "-q" ]]; then
    QUIET=true
fi

# Define all ports to check as "name|port" pairs (pipe-delimited to avoid
# issues with colons in service names or port values)
SERVICES="PostgreSQL|${POSTGRES_PORT:-5432}
Keycloak|${KEYCLOAK_PORT:-8081}
Application server|${SERVER_PORT:-8080}
Intelligence service|${INTELLIGENCE_SERVICE_PORT:-8000}
Webapp (Vite)|${WEBAPP_PORT:-4200}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

conflicts=0

check_port() {
    local name="$1"
    local port="$2"
    local pids

    pids=$(lsof -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null | head -3 | tr '\n' ',' | sed 's/,$//' || true)
    if [[ -n "$pids" ]]; then
        if [[ "$QUIET" == false ]]; then
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

# Check for duplicate port assignments.
# Uses parallel arrays (space-separated) — portable to Bash 3.2 (no associative arrays).
duplicates=0
all_ports=""
all_names=""
while IFS='|' read -r name port; do
    # Walk previously seen ports to find duplicates
    _remaining_ports="$all_ports"
    _remaining_names="$all_names"
    while [[ -n "$_remaining_ports" ]]; do
        _p="${_remaining_ports%% *}"
        _n="${_remaining_names%%|*}"
        _remaining_ports="${_remaining_ports#* }"
        _remaining_names="${_remaining_names#*|}"
        # Stop when we've exhausted the list (last element repeats after strip)
        if [[ "$_p" == "$port" ]]; then
            if [[ "$QUIET" == false ]]; then
                echo -e "  ${YELLOW}DUPLICATE${NC} :${port}  ${name} conflicts with ${_n}"
            fi
            duplicates=$((duplicates + 1))
            break
        fi
        # Break if we've consumed everything (no more spaces means last item)
        [[ "$_remaining_ports" == "$_p" ]] && break
    done
    all_ports="${all_ports:+$all_ports }${port}"
    all_names="${all_names:+$all_names|}${name}"
done <<< "$SERVICES"

if [[ "$duplicates" -gt 0 && "$QUIET" == false ]]; then
    echo ""
    echo -e "${YELLOW}Warning: Duplicate port assignments detected. Override one of them via environment variables.${NC}"
    echo ""
fi

while IFS='|' read -r name port; do
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

if [[ "$conflicts" -gt 0 ]]; then
    exit 1
else
    exit 0
fi
