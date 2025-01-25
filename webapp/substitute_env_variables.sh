#!/bin/bash

# Exit immediately if a command exits with a non-zero status,
# Treat unset variables as an error, and prevent errors in a pipeline from being masked.
set -euo pipefail

# Uncomment the following line to enable debug mode for troubleshooting
# set -x

# Directory containing the build artifacts (Angular's output directory)
BUILD_DIR="/usr/share/nginx/html"

# Prefix for environment variable placeholders in Angular code
ENV_PREFIX="WEB_ENV_"

# Regex pattern to match WEB_ENV_ variables (e.g., WEB_ENV_APPLICATION_CLIENT_URL)
ENV_VAR_REGEX="${ENV_PREFIX}[A-Z0-9_]+"

# Function to log messages with timestamps to stderr
log() {
  echo "[`date +'%Y-%m-%dT%H:%M:%S%z'`] $*" >&2
}

# Function to find all unique WEB_ENV_ variables in the build artifacts
find_env_vars() {
  log "🔍 Scanning build artifacts for environment variables with prefix '${ENV_PREFIX}'..."

  # Use grep to find all occurrences of WEB_ENV_* in text files within BUILD_DIR
  # Exclude binary files to prevent corruption
  grep -rhoE "${ENV_VAR_REGEX}" "${BUILD_DIR}" --binary-files=without-match | sort | uniq
}

# Function to validate that all required environment variables are set
validate_env_vars() {
  local missing_vars=()

  for var in "$@"; do
    if [[ -z "${!var:-}" ]]; then
      missing_vars+=("$var")
    fi
  done

  if [ ${#missing_vars[@]} -ne 0 ]; then
    log "❌ Error: The following environment variables are not set:"
    for var in "${missing_vars[@]}"; do
      echo "  - $var" >&2
    done
    exit 1
  fi
}

# Function to replace placeholders with actual environment variable values
replace_vars() {
  local placeholder="$1"
  local env_var="${placeholder#${ENV_PREFIX}}"
  local value="${!env_var}"

  # Log the substitution process
  log "🔄 Replacing placeholder '${placeholder}' with environment variable '${env_var}' value..."

  # Escape characters that might interfere with sed/perl
  local escaped_value
  escaped_value=$(printf '%s' "$value" | sed 's/[&/\]/\\&/g')

  # Use Perl for in-place substitution to handle special characters gracefully
  # Only process text files to avoid binary corruption
  find "${BUILD_DIR}" -type f \( -name "*.js" -o -name "*.html" -o -name "*.css" \) -exec grep -Il "${placeholder}" {} \; | while read -r file; do
    perl -pi -e "s/${placeholder}/${escaped_value}/g" "$file"
    log "✅ Replaced '${placeholder}' in '${file}'"
  done
}

# Main execution flow
main() {
  # Step 1: Find all required environment variables
  placeholders=$(find_env_vars)

  if [ -z "${placeholders}" ]; then
    log "ℹ️ No environment variables with prefix '${ENV_PREFIX}' found to replace."
    exit 0
  fi

  # Convert placeholders string to an array without using mapfile
  placeholders_array=()
  while IFS= read -r placeholder; do
      placeholders_array+=("$placeholder")
  done <<< "$placeholders"

  # Step 2: Map placeholders to environment variables and validate
  # Collect corresponding environment variable names
  env_vars=()
  for placeholder in "${placeholders_array[@]}"; do
    env_var="${placeholder#${ENV_PREFIX}}"
    env_vars+=("$env_var")
  done

  # Validate that all required environment variables are set
  validate_env_vars "${env_vars[@]}"

  # Step 3: Replace placeholders with actual values
  for placeholder in "${placeholders_array[@]}"; do
    replace_vars "$placeholder"
  done

  log "🎉 Environment variable substitution completed successfully."
}

# Invoke the main function
main
