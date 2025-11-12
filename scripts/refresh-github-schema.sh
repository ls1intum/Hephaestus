#!/usr/bin/env bash

# ============================================================================
# GitHub GraphQL Schema Refresh Script
# ============================================================================
# 
# This script downloads the latest GitHub GraphQL public schema and compares
# it with the current schema to detect changes (including breaking changes).
#
# PREREQUISITES:
#   - GITHUB_TOKEN environment variable must be set with a valid GitHub PAT
#   - Node.js and npm must be installed
#   - graphql-inspector will be auto-installed via npx if not present
#
# USAGE:
#   GITHUB_TOKEN=ghp_xxxxx npm run gql:schema:update
#
# WHAT IT DOES:
#   1. Downloads the latest GitHub GraphQL schema from api.github.com
#   2. Normalizes it to .graphql SDL format (strips metadata like @possibleTypes)
#   3. Compares with the existing schema and shows all changes
#   4. Highlights breaking changes (marked with ✖) if any
#   5. Updates the schema file with the latest version
#
# OUTPUT:
#   - Displays all schema changes with symbols:
#     ✔  = Non-breaking change (addition or safe modification)
#     ✖  = Breaking change (removal or incompatible modification)
#   - Exit code 0 = Success (with or without changes)
#   - Exit code 1+ = Error during download or diff
#
# NOTE:
#   The downloaded schema (~43k lines) is shorter than the raw GitHub schema
#   (~70k lines) because graphql-inspector strips GitHub-specific metadata
#   directives like @possibleTypes. This is intentional and correct.
# ============================================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEMA_DIR="$ROOT_DIR/server/application-server/src/main/resources/github/graphql/schema"
CURRENT_SCHEMA="$SCHEMA_DIR/github-public-schema.graphql"
TEMP_DOWNLOAD="$SCHEMA_DIR/github-public-schema.download.graphql"
DIFF_OUTPUT="$(mktemp)"

cleanup() {
  rm -f "$TEMP_DOWNLOAD" "$DIFF_OUTPUT"
}
trap cleanup EXIT

if [[ -z "${GITHUB_TOKEN:-}" ]]; then
  echo "GITHUB_TOKEN is required" >&2
  exit 1
fi

echo "Downloading GitHub GraphQL schema..."
TEMP_DOWNLOAD="$SCHEMA_DIR/github-public-schema.download.graphql"
if ! npx graphql-inspector introspect https://api.github.com/graphql \
  --header "Authorization: bearer $GITHUB_TOKEN" \
  --header "X-Github-Next-Global-ID: 1" \
  --header "User-Agent: Hephaestus-GraphQL-Schema-Refresh" \
  --write "$TEMP_DOWNLOAD"; then
  echo "Schema download failed" >&2
  rm -f "$TEMP_DOWNLOAD"
  exit 1
fi

if [[ -f "$CURRENT_SCHEMA" ]]; then
  echo "Comparing with existing schema..."
  if ! npx graphql-inspector diff "$CURRENT_SCHEMA" "$TEMP_DOWNLOAD" >"$DIFF_OUTPUT" 2>&1; then
    exit_code=$?
    if [[ $exit_code -eq 1 ]]; then
      echo "" >&2
      echo "════════════════════════════════════════════════════════════════════" >&2
      echo "                    SCHEMA CHANGES DETECTED" >&2
      echo "════════════════════════════════════════════════════════════════════" >&2
      cat "$DIFF_OUTPUT" >&2
      echo "════════════════════════════════════════════════════════════════════" >&2
      echo "" >&2
      
      # Count breaking changes
      breaking_count=$(grep -c "✖" "$DIFF_OUTPUT" || true)
      if [[ $breaking_count -gt 0 ]]; then
        echo "⚠️  WARNING: $breaking_count BREAKING CHANGE(S) DETECTED!" >&2
        echo "    Review the changes above carefully before committing." >&2
      else
        echo "✅ No breaking changes detected (only additions/non-breaking changes)." >&2
      fi
      echo "" >&2
    else
      echo "Schema diff failed:" >&2
      cat "$DIFF_OUTPUT" >&2
      rm -f "$TEMP_DOWNLOAD"
      exit $exit_code
    fi
  else
    if [[ -s "$DIFF_OUTPUT" ]]; then
      cat "$DIFF_OUTPUT"
    else
      echo "✅ No schema changes detected - schema is up to date."
    fi
  fi
else
  echo "⚠️  No existing schema found. Writing new schema..."
fi

echo "Updating schema at $CURRENT_SCHEMA"
mv "$TEMP_DOWNLOAD" "$CURRENT_SCHEMA"
cleanup
trap - EXIT

echo "GitHub GraphQL schema refreshed successfully."
