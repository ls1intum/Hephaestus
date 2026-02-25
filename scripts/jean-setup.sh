#!/usr/bin/env bash
set -euo pipefail

echo "Setting up Jean worktree..."

# JEAN_ROOT_PATH is set by Jean to the main repo checkout.
# We copy local config files from there into this worktree so each
# worktree can be modified independently.
if [ -z "${JEAN_ROOT_PATH:-}" ]; then
  echo "  JEAN_ROOT_PATH is not set — skipping config file copy."
else
  copy_if_exists() {
    local src="$JEAN_ROOT_PATH/$1"
    if [ -f "$src" ]; then
      mkdir -p "$(dirname "$1")"
      cp "$src" "$1"
      echo "  copied $1"
    else
      echo "  skipped $1 (not found in root)"
    fi
  }

  echo "Copying local config files..."
  copy_if_exists "server/application-server/src/main/resources/application-local.yml"
  copy_if_exists "server/application-server/src/test/resources/application-live-local.yml"
  copy_if_exists "server/application-server/.env"
  copy_if_exists "server/intelligence-service/.env"
  copy_if_exists "server/webhook-ingest/.env"
  copy_if_exists "docker/.env"
  copy_if_exists ".claude/settings.local.json"
fi

echo "Installing npm dependencies..."
npm install

echo "✅ Jean worktree setup complete."
