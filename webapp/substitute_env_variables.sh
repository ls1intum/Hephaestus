#!/bin/bash

# Exit immediately if a command exits with a non-zero status,
# Treat unset variables as an error, and prevent errors in a pipeline from being masked.
set -euo pipefail

# Uncomment the following line to enable debug mode for troubleshooting
# set -x

# Directory containing the build artifacts
BUILD_DIR="dist/webapp/browser"

# Prefix for environment variables
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
  # Exclude binary files and specified directories to prevent corruption and unnecessary scanning
  grep -rhoE "${ENV_VAR_REGEX}" "${BUILD_DIR}" --exclude-dir={.git,node_modules} --binary-files=without-match | sort | uniq
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
  local var="$1"
  local value="${!var}"

  # Log the substitution process
  log "🔄 Replacing placeholder '${var}' with its actual value..."

  # Escape characters that might interfere with sed/perl
  local escaped_value
  escaped_value=$(printf '%s' "$value" | sed 's/[&/\]/\\&/g')

  # Use Perl for in-place substitution to handle special characters gracefully
  # Only process text files to avoid binary corruption
  find "${BUILD_DIR}" -type f \( -name "*.js" -o -name "*.html" -o -name "*.css" \) -exec grep -Il "${var}" {} \; | while read -r file; do
    perl -pi -e "s/${var}/${escaped_value}/g" "$file"
    log "✅ Replaced '${var}' in '${file}'"
  done
}

# Main execution flow
main() {
  if [ ! -d "${BUILD_DIR}" ]; then
    log "❌ Error: Build directory '${BUILD_DIR}' does not exist."
    exit 1
  fi

  # Step 1: Find all required environment variables
  env_vars=$(find_env_vars)

  if [ -z "${env_vars}" ]; then
    log "ℹ️ No environment variables with prefix '${ENV_PREFIX}' found to replace."
    exit 0
  fi

  # Convert env_vars string to an array without using mapfile
  env_vars_array=()
  while IFS= read -r var; do
      env_vars_array+=("$var")
  done <<< "$env_vars"

  # Step 2: Validate that all required environment variables are set
  validate_env_vars "${env_vars_array[@]}"

  # Step 3: Replace placeholders with actual values
  for var in "${env_vars_array[@]}"; do
    replace_vars "$var"
  done

  log "🎉 Environment variable substitution completed successfully."
}

# Invoke the main function
main
