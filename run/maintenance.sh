#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$ROOT_DIR/scripts"
DOCKER_AVAILABLE_CACHE=""
to_lower() {
    printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}
if [[ $EUID -ne 0 ]]; then
    if command -v sudo >/dev/null 2>&1; then
        SUDO="sudo"
    else
        SUDO=""
        echo "⚠️  Local PostgreSQL management requires root privileges. Install sudo or run this script as root if startup fails." >&2
    fi
else
    SUDO=""
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

use_local_db() {
    if [[ "$(to_lower "${HEPHAESTUS_DB_MODE:-}")" == "local" ]]; then
        return 0
    fi

    if ! docker_available; then
        return 0
    fi

    return 1
}

if use_local_db; then
    echo "ℹ️  Ensuring local PostgreSQL instance is running..."
    if [[ -n "$SUDO" ]]; then
        $SUDO env HEPHAESTUS_DB_MODE=local "$SCRIPTS_DIR/local-postgres.sh" start
    else
        HEPHAESTUS_DB_MODE=local "$SCRIPTS_DIR/local-postgres.sh" start
    fi
else
    echo "ℹ️  Docker is available; skipping local PostgreSQL startup."
fi

if [[ -d "$ROOT_DIR" ]]; then
    echo "ℹ️  Refreshing npm dependencies..."
    (cd "$ROOT_DIR" && npm install --prefer-offline --no-fund --no-audit >/dev/null)
fi

echo "✅ Maintenance tasks finished."
