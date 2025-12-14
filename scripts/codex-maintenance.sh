#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=""

resolve_repo_root() {
    local script_root=""
    local git_root=""

    if [[ -n "${HEPHAESTUS_ROOT:-}" && -f "${HEPHAESTUS_ROOT}/package.json" ]]; then
        ROOT_DIR="$(cd "${HEPHAESTUS_ROOT}" && pwd)"
        return
    fi

    if [[ -n "${CODEX_REPO_ROOT:-}" && -f "${CODEX_REPO_ROOT}/package.json" ]]; then
        ROOT_DIR="$(cd "${CODEX_REPO_ROOT}" && pwd)"
        return
    fi

    script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." 2>/dev/null && pwd 2>/dev/null || true)"
    if [[ -n "$script_root" && -f "$script_root/package.json" ]]; then
        ROOT_DIR="$script_root"
        return
    fi

    if [[ -d /workspace/Hephaestus && -f /workspace/Hephaestus/package.json ]]; then
        ROOT_DIR="/workspace/Hephaestus"
        return
    fi

    if command -v git >/dev/null 2>&1; then
        git_root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
        if [[ -n "$git_root" && -f "$git_root/package.json" ]]; then
            ROOT_DIR="$git_root"
            return
        fi
    fi

    echo "❌ Failed to locate repository root. Set HEPHAESTUS_ROOT to the project directory and rerun." >&2
    exit 1
}

resolve_repo_root
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
