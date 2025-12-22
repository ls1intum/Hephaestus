#!/bin/bash
# Run drizzle-kit introspect and post-processing in a Linux container for consistent output.
# This ensures schema generation produces identical results on macOS and Linux.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$(dirname "$SERVICE_DIR")")"

# Check if we're running in CI (already on Linux) or need Docker
if [[ "${CI:-false}" == "true" ]] || [[ "$(uname -s)" == "Linux" ]]; then
    # Already on Linux, run directly
    echo "Running on Linux, executing directly..."
    cd "$SERVICE_DIR"
    npx drizzle-kit introspect --dialect=postgresql --url=postgresql://root:root@localhost:5432/hephaestus
    npx tsx scripts/post-introspect.ts
else
    # On macOS/Windows, run in Docker for consistent Linux output
    echo "Running in Docker for Linux-consistent output..."
    
    # Create a temporary directory for the container's node_modules
    TEMP_NODE_MODULES=$(mktemp -d)
    trap "rm -rf $TEMP_NODE_MODULES" EXIT
    
    # Run in Docker - install deps inside container then run commands
    docker run --rm \
        --network host \
        -v "$SERVICE_DIR:/app:ro" \
        -v "$SERVICE_DIR/drizzle:/app/drizzle" \
        -v "$SERVICE_DIR/src/shared/db:/app/src/shared/db" \
        -v "$TEMP_NODE_MODULES:/app/node_modules" \
        -w /app \
        -e npm_config_cache=/tmp/npm-cache \
        node:22-slim \
        /bin/bash -c "
            echo 'Installing dependencies...'
            npm install --prefer-offline --no-audit 2>/dev/null
            echo 'Running drizzle-kit introspect...'
            npx drizzle-kit introspect --dialect=postgresql --url=postgresql://root:root@host.docker.internal:5432/hephaestus
            echo 'Running post-introspect...'
            npx tsx scripts/post-introspect.ts
        "
fi

echo "Schema generation complete!"
