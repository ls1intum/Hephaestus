#!/bin/bash
set -euo pipefail

readonly HTML_DIR="/usr/share/nginx/html"
readonly INDEX_HTML="${HTML_DIR}/index.html"
readonly ENV_TS="/app/src/environment/index.ts"

log() { echo "[$(date +'%Y-%m-%dT%H:%M:%S%z')] $*" >&2; }

escape_for_js() {
  local input="${1:-}" output="" i char
  for ((i=0; i<${#input}; i++)); do
    char="${input:i:1}"
    case "$char" in
      $'\n') output+="\\n" ;; $'\r') output+="\\r" ;; $'\t') output+="\\t" ;;
      '"') output+="\\\"" ;; '\\') output+="\\\\" ;; '/') output+="\\/" ;; *) output+="$char" ;;
    esac
  done
  printf '%s' "$output"
}

# Extract variable names from TypeScript interface (single source of truth)
# Parses: "VAR_NAME?: string;" patterns from RuntimeEnvVars interface
extract_env_vars() {
  if [[ ! -f "$ENV_TS" ]]; then
    log "ERROR: TypeScript source not found: $ENV_TS"
    exit 1
  fi
  grep -oE '[A-Z_]+\?:' "$ENV_TS" | sed 's/\?:$//' | grep -v '^__'
}

main() {
  log "Generating runtime config..."

  # Extract all env var names from TypeScript (single source of truth)
  local -a all_vars
  mapfile -t all_vars < <(extract_env_vars)
  
  if [[ ${#all_vars[@]} -eq 0 ]]; then
    log "ERROR: No environment variables found in $ENV_TS"
    exit 1
  fi
  log "Found ${#all_vars[@]} vars: ${all_vars[*]}"

  # Backfill git metadata from files embedded during build (if available)
  if [[ -f "${HTML_DIR}/git_commit" ]]; then
    export GIT_COMMIT=$(cat "${HTML_DIR}/git_commit")
    log "Loaded GIT_COMMIT from build artifact: ${GIT_COMMIT}"
    rm "${HTML_DIR}/git_commit"
  fi
  if [[ -f "${HTML_DIR}/git_branch" ]]; then
    export GIT_BRANCH=$(cat "${HTML_DIR}/git_branch")
    log "Loaded GIT_BRANCH from build artifact: ${GIT_BRANCH}"
    rm "${HTML_DIR}/git_branch"
  fi

  # Auto-generate deployment timestamp for preview environments
  if [[ -n "${GIT_BRANCH:-}" && -z "${DEPLOYED_AT:-}" ]]; then
    export DEPLOYED_AT=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    log "Generated DEPLOYED_AT: ${DEPLOYED_AT}"
  fi

  # Log which vars are set/unset
  local -a present=() missing=()
  for var in "${all_vars[@]}"; do
    [[ -n "${!var:-}" ]] && present+=("$var") || missing+=("$var")
  done
  [[ ${#present[@]} -gt 0 ]] && log "Set: ${present[*]}"
  [[ ${#missing[@]} -gt 0 ]] && log "Unset: ${missing[*]}"

  # Determine which vars to include in cache hash
  # Preview/dev (GIT_BRANCH set): all vars are cacheable (same deployment = same hash)
  # Production (no GIT_BRANCH): exclude DEPLOYED_AT (auto-generated timestamp would bust cache)
  local -a hash_vars=()
  for var in "${all_vars[@]}"; do
    if [[ "$var" == "DEPLOYED_AT" && -z "${GIT_BRANCH:-}" ]]; then
      continue  # Exclude DEPLOYED_AT from hash in production
    fi
    hash_vars+=("$var")
  done

  # Compute content hash from cacheable vars
  local hash_input=""
  for var in "${hash_vars[@]}"; do
    hash_input+="${var}=${!var:-}"$'\n'
  done
  local hash
  hash=$(printf '%s' "$hash_input" | sha256sum | cut -c1-8)

  # Generate JavaScript config
  local filename="env-config.${hash}.js"
  {
    echo "window.__ENV__ = {"
    local first=true
    for var in "${all_vars[@]}"; do
      $first && first=false || echo ","
      printf '  %s: "%s"' "$var" "$(escape_for_js "${!var:-}")"
    done
    echo -e "\n};"
  } > "${HTML_DIR}/${filename}"

  # Remove old config files and update index.html
  find "${HTML_DIR}" -maxdepth 1 -name 'env-config.*.js' ! -name "$filename" -delete 2>/dev/null || true
  log "Created ${filename}"

  if [[ -f "$INDEX_HTML" ]]; then
    sed -Ei "s|/env-config(\.[a-f0-9]{8})?\.js|/${filename}|g" "$INDEX_HTML"
    grep -q "$filename" "$INDEX_HTML" || { log "ERROR: Failed to update index.html"; exit 1; }
    log "Updated index.html"
  fi

  log "Done (hash: ${hash})"
}

main
