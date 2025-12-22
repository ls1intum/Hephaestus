#!/bin/bash
# Run drizzle-kit introspect and post-processing.
# Biome handles consistent formatting and import organization.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$(dirname "$SCRIPT_DIR")"

cd "$SERVICE_DIR"

echo "Running drizzle-kit introspect..."
npx drizzle-kit introspect --dialect=postgresql --url=postgresql://root:root@localhost:5432/hephaestus

echo "Running post-introspect..."
npx tsx scripts/post-introspect.ts

echo "Schema generation complete!"
