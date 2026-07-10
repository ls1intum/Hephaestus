#!/usr/bin/env bash
set -euo pipefail

echo "Setting up Jean worktree..."

# JEAN_ROOT_PATH is set by Jean to the main repo checkout.
# We copy local config files from there into this worktree so each
# worktree can be modified independently.
if [ -z "${JEAN_ROOT_PATH:-}" ]; then
  echo "  JEAN_ROOT_PATH is not set — skipping config file copy."
else
  # First arg = destination path in this worktree. Subsequent args are candidate
  # source paths under $JEAN_ROOT_PATH; first hit wins. Lets us pick up files
  # that the main worktree may still carry under a legacy directory layout
  # (e.g. server/application-server/... before the #1097 module rename).
  copy_first_hit() {
    local dest="$1"; shift
    local idx=0
    for src_rel in "$@"; do
      local src="$JEAN_ROOT_PATH/$src_rel"
      if [ -f "$src" ]; then
        mkdir -p "$(dirname "$dest")"
        cp "$src" "$dest"
        if [ "$idx" -gt 0 ]; then
          echo "  WARN: legacy layout — copied $dest (from $src_rel)"
        else
          echo "  copied $dest (from $src_rel)"
        fi
        return
      fi
      idx=$((idx + 1))
    done
    echo "  skipped $dest (no candidate found in root: $*)"
  }

  echo "Copying local config files..."
  copy_first_hit "server/src/main/resources/application-local.yml" \
    "server/src/main/resources/application-local.yml" \
    "server/application-server/src/main/resources/application-local.yml"
  copy_first_hit "server/src/test/resources/application-live-local.yml" \
    "server/src/test/resources/application-live-local.yml" \
    "server/application-server/src/test/resources/application-live-local.yml"
  copy_first_hit "server/.env" "server/.env" "server/application-server/.env"
  copy_first_hit "docker/.env" "docker/.env"
  copy_first_hit ".claude/settings.local.json" ".claude/settings.local.json"
fi

if [ -f "server/.env" ] && grep -Eq '^GITLAB_PAT=.' "server/.env" && grep -Eq '^GITLAB_GROUP_PATH=.' "server/.env"; then
  set_env_default() {
    local key="$1"
    local value="$2"
    if grep -Eq "^${key}=" "server/.env"; then
      sed -i -E "s|^${key}=.*|${key}=${value}|" "server/.env"
    else
      printf '%s=%s\n' "$key" "$value" >> "server/.env"
    fi
  }
  set_env_if_missing() {
    local key="$1"
    local value="$2"
    if ! grep -Eq "^${key}=" "server/.env"; then
      printf '%s=%s\n' "$key" "$value" >> "server/.env"
    fi
  }
  set_env_default "GITLAB_WORKSPACE_INIT_DEFAULT" "true"
  set_env_default "GITLAB_ENABLED" "true"
  set_env_if_missing "GITLAB_SERVER_URL" "https://gitlab.lrz.de"
  echo "  enabled GitLab default workspace bootstrap from local server/.env"
fi

echo "Installing npm dependencies..."
pnpm install --frozen-lockfile

echo "✅ Jean worktree setup complete."
