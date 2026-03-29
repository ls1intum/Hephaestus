#!/usr/bin/env bash
# Renders all .hyl files in docs/diagrams/ to SVG using HyLiMo CLI
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CLI="$REPO_DIR/node_modules/.bin/cli"

if [ ! -f "$CLI" ]; then
  echo "Error: @hylimo/cli not found. Run 'npm install' first."
  exit 1
fi

STATIC_DIR="$SCRIPT_DIR/../static/img/diagrams"
mkdir -p "$STATIC_DIR"

failed=0
for hyl in "$SCRIPT_DIR"/*.hyl; do
  [ -f "$hyl" ] || continue
  svg="${hyl%.hyl}.svg"
  name="$(basename "$hyl")"
  if "$CLI" -f "$hyl" -o "$svg" 2>&1; then
    cp "$svg" "$STATIC_DIR/"
    echo "$name → $(basename "$svg")"
  else
    echo "FAILED: $name"
    failed=1
  fi
done

exit $failed
