#!/bin/bash
# Update GitHub GraphQL schema from official docs
# This script downloads the latest schema and triggers code generation
#
# Usage: ./update-github-schema.sh [--generate]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCHEMA_DIR="${SCRIPT_DIR}/../server/application-server/src/main/resources/graphql/github"
SCHEMA_FILE="${SCHEMA_DIR}/schema.github.graphql"
SCHEMA_URL="https://docs.github.com/public/fpt/schema.docs.graphql"

echo "📥 Downloading GitHub GraphQL schema..."
curl -sL "${SCHEMA_URL}" -o "${SCHEMA_FILE}.tmp"

# Verify download was successful (file should be > 1MB)
# Use portable stat syntax for macOS/Linux compatibility
if [[ "$OSTYPE" == "darwin"* ]]; then
    FILE_SIZE=$(stat -f%z "${SCHEMA_FILE}.tmp")
else
    FILE_SIZE=$(stat -c%s "${SCHEMA_FILE}.tmp")
fi

if [ "${FILE_SIZE}" -lt 1000000 ]; then
    echo "❌ Downloaded file is too small (${FILE_SIZE} bytes). Schema should be ~1.4MB"
    rm -f "${SCHEMA_FILE}.tmp"
    exit 1
fi

mv "${SCHEMA_FILE}.tmp" "${SCHEMA_FILE}"

# Format file size (portable)
if [ "${FILE_SIZE}" -gt 1048576 ]; then
    SIZE_STR="$((FILE_SIZE / 1048576))MB"
else
    SIZE_STR="$((FILE_SIZE / 1024))KB"
fi
echo "✅ Schema downloaded successfully (${SIZE_STR})"

# Optionally regenerate code
if [ "${1:-}" = "--generate" ]; then
    echo ""
    echo "🔨 Generating GraphQL client code..."
    cd "${SCRIPT_DIR}/../server/application-server"
    ./mvnw graphql-codegen:generate -q
    echo "✅ Code generation complete"
    echo "   Output: target/generated-sources/graphql"
fi

echo ""
echo "📁 Schema location: ${SCHEMA_FILE}"
echo ""
echo "To regenerate client code, run:"
echo "  cd server/application-server && ./mvnw graphql-codegen:generate"
