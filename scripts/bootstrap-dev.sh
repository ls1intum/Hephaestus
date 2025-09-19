#!/usr/bin/env bash
set -euo pipefail

# Ensures Python dev dependencies (black, flake8, etc.) are installed for services
# so that `npm run format` / `npm run lint` work out-of-the-box.

if ! command -v poetry >/dev/null 2>&1; then
  echo "❌ Poetry is not installed. Please install Poetry: https://python-poetry.org/docs/#installation" >&2
  exit 1
fi

ensure_dev() {
  local dir="$1"
  local tool="$2" # tool to check (black or flake8)
  if [ ! -d "$dir" ]; then
    echo "⚠️  Skipping missing directory $dir" >&2
    return 0
  fi
  # If the venv is missing the tool (or dev deps not yet installed) install with dev extras.
  if ! (cd "$dir" && poetry run $tool --version >/dev/null 2>&1); then
    echo "⏳ Installing dev dependencies in $dir ..."
    (cd "$dir" && poetry install --with dev --no-root >/dev/null)
  fi
}

ensure_dev server/intelligence-service black
ensure_dev server/webhook-ingest black

echo "✅ Python dev environments ready."
