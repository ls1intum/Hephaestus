#!/usr/bin/env bash
# Renders all .hyl files in docs/diagrams/ to SVG using HyLiMo CLI
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
HYLIMO_DIR="$(cd "$SCRIPT_DIR/../../.context/hylimo" && pwd)"
CLI="$HYLIMO_DIR/packages/cli/lib/index.js"

if [ ! -f "$CLI" ]; then
  echo "Error: HyLiMo CLI not found at $CLI"
  echo "Clone hylimo into .context/hylimo first."
  exit 1
fi

STATIC_DIR="$SCRIPT_DIR/../static/img/diagrams"
mkdir -p "$STATIC_DIR"

failed=0
for hyl in "$SCRIPT_DIR"/*.hyl; do
  [ -f "$hyl" ] || continue
  svg="${hyl%.hyl}.svg"
  name="$(basename "$hyl")"
  if (cd "$HYLIMO_DIR" && node "$CLI" -f "$hyl" -o "$svg" 2>&1); then
    cp "$svg" "$STATIC_DIR/"
    echo "$name → $(basename "$svg")"
  else
    echo "FAILED: $name"
    failed=1
  fi
done

exit $failed
