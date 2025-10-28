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
        echo "❌ run/setup.sh must be executed with root privileges to install system packages." >&2
        exit 1
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

install_postgres() {
    if command -v pg_ctl >/dev/null 2>&1; then
        echo "✅ PostgreSQL already installed."
        return
    fi

    echo "ℹ️  Installing PostgreSQL server and client packages..."
    $SUDO apt-get update -y
    DEBIAN_FRONTEND=noninteractive $SUDO apt-get install -y postgresql postgresql-contrib
    echo "✅ PostgreSQL installation complete."
}

install_node_dependencies() {
    echo "ℹ️  Installing npm dependencies..."
    (cd "$ROOT_DIR" && npm install)
}

bootstrap_python() {
    echo "ℹ️  Bootstrapping Python development environments..."
    (cd "$ROOT_DIR" && npm run bootstrap:py)
}

initialize_local_postgres() {
    if use_local_db; then
        echo "ℹ️  Ensuring local PostgreSQL cluster is initialized..."
        if [[ -n "$SUDO" ]]; then
            $SUDO env HEPHAESTUS_DB_MODE=local "$SCRIPTS_DIR/local-postgres.sh" start
        else
            HEPHAESTUS_DB_MODE=local "$SCRIPTS_DIR/local-postgres.sh" start
        fi
    else
        echo "ℹ️  Docker is available; skipping local PostgreSQL bootstrap."
    fi
}

install_postgres
install_node_dependencies
bootstrap_python
initialize_local_postgres

echo "✅ Setup completed successfully."
