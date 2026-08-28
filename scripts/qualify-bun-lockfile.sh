#!/usr/bin/env bash
set -euo pipefail
iterations="${1:-25}"
[[ "$iterations" =~ ^[1-9][0-9]*$ ]] || { echo "iterations must be a positive integer" >&2; exit 2; }
expected="$(sha256sum bun.lock | cut -d' ' -f1)"
for ((i = 1; i <= iterations; i++)); do
  rm -rf node_modules webapp/node_modules docs/node_modules
  bun install --frozen-lockfile --no-progress
  actual="$(sha256sum bun.lock | cut -d' ' -f1)"
  [[ "$actual" == "$expected" ]] || { echo "bun.lock changed on iteration $i" >&2; exit 1; }
  bun run check:package-manager
done
