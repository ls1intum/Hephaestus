#!/bin/bash

# Exit immediately if a command exits with a non-zero status,
# Treat unset variables as an error, and prevent errors in a pipeline from being masked.
set -euo pipefail

# Directory containing the build artifacts
BUILD_DIR="/usr/share/nginx/html"

# Prefix for environment variable placeholders
ENV_PREFIX="WEB_ENV_"

# Regex pattern to match WEB_ENV_ variables
ENV_VAR_REGEX="${ENV_PREFIX}[A-Z0-9_]+"

# Function to log messages with timestamps to stderr
log() {
  echo "[`date +'%Y-%m-%dT%H:%M:%S%z'`] $*" >&2
}

# Find all JS files that potentially contain environment placeholders
# Vite creates hashed filenames for JS files, so we need to find them dynamically
find_js_files() {
  log "🔍 Finding JavaScript files in build directory..."
  find "${BUILD_DIR}" -type f -name "*.js" | xargs grep -l "${ENV_PREFIX}" 2>/dev/null || echo ""
}

# Main execution function
main() {
  log "Starting environment variable substitution in ${BUILD_DIR}..."
  
  # Find all JS files that might contain environment placeholders
  js_files=$(find_js_files)
  
  if [ -z "${js_files}" ]; then
    log "⚠️ No JavaScript files found containing '${ENV_PREFIX}' placeholders."
    return 0
  fi
  
  log "Found JavaScript files with environment placeholders:"
  echo "${js_files}" | while read -r file; do
    log "  - ${file}"
  done
  
  # Find all unique placeholder variables
  log "🔍 Extracting environment variable placeholders..."
  placeholders=$(grep -hoE "${ENV_VAR_REGEX}" ${js_files} 2>/dev/null | sort | uniq)
  
  if [ -z "${placeholders}" ]; then
    log "⚠️ No environment variable placeholders found."
    return 0
  fi
  
  log "Detected the following environment placeholders:"
  echo "${placeholders}" | while read -r placeholder; do
    env_var="${placeholder#${ENV_PREFIX}}"
    log "  - ${placeholder} (mapped to ${env_var})"
  done
  
  # Process each placeholder
  echo "${placeholders}" | while IFS= read -r placeholder; do
    env_var="${placeholder#${ENV_PREFIX}}"
    log "🔄 Processing '${placeholder}' from environment variable '${env_var}'..."
    
    # Check if the environment variable is set
    if [[ -z "${!env_var+x}" ]]; then
      log "⚠️ Warning: Environment variable ${env_var} is not set. Using empty string."
      value=""
    else
      value="${!env_var}"
    fi
    
    # Special handling for boolean values
    if [[ "${placeholder}" == *"KEYCLOAK_SKIP_LOGIN"* ]]; then
      if [[ "${value}" == "true" || "${value}" == "1" || "${value}" == "yes" ]]; then
        log "Replacing boolean '${placeholder}' with true value"
        # For JS boolean values, we need to replace the string with an actual boolean
        # This assumes the placeholder is surrounded by quotes in the JS file
        sed -i "s/\"${placeholder}\"/true/g" ${js_files}
        sed -i "s/'${placeholder}'/true/g" ${js_files}
      else
        log "Replacing boolean '${placeholder}' with false value"
        sed -i "s/\"${placeholder}\"/false/g" ${js_files}
        sed -i "s/'${placeholder}'/false/g" ${js_files}
      fi
    else
      # For string values, escape for JavaScript and keep the quotes
      log "Replacing string '${placeholder}' with '${value}'"
      escaped_value=$(echo "${value}" | sed 's/\\/\\\\/g; s/"/\\"/g; s/'"'"'/\\'"'"'/g')
      # Use # as delimiter for sed instead of / to avoid issues with URLs
      sed -i "s#\"${placeholder}\"#\"${escaped_value}\"#g" ${js_files}
      sed -i "s#'${placeholder}'#'${escaped_value}'#g" ${js_files}
    fi
    
    log "✅ Replaced all occurrences of '${placeholder}'"
  done
  
  log "🎉 Environment variable substitution completed successfully."
}

# Execute the main function
main