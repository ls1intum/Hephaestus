#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$ROOT_DIR/scripts"
CODEX_VALUE="${CODEX:-false}"
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

if [[ "${CODEX_VALUE,,}" == "true" || "${HEPHAESTUS_DB_MODE:-}" == "local" ]]; then
    echo "ℹ️  Ensuring local PostgreSQL instance is running..."
    if [[ -n "$SUDO" ]]; then
        $SUDO env HEPHAESTUS_DB_MODE=local CODEX="$CODEX_VALUE" "$SCRIPTS_DIR/local-postgres.sh" start
    else
        HEPHAESTUS_DB_MODE=local CODEX="$CODEX_VALUE" "$SCRIPTS_DIR/local-postgres.sh" start
    fi
else
    echo "ℹ️  Skipping local PostgreSQL startup (HEPHAESTUS_DB_MODE=${HEPHAESTUS_DB_MODE:-docker})."
fi

if [[ -d "$ROOT_DIR" ]]; then
    echo "ℹ️  Refreshing npm dependencies..."
    (cd "$ROOT_DIR" && npm install --prefer-offline --no-fund --no-audit >/dev/null)
fi

echo "✅ Maintenance tasks finished."
