# ADR 0030: The runtime used TypeScript on Bun

**Status:** Superseded by [0036](0036-agent-runtime-runs-on-node-24.md)
**Date:** 2026-10-14
**Authors:** Hephaestus maintainers

## Context

The runtime moved to TypeScript to share a checked language across its execution code.
The selected JavaScript runtime was Bun at that time.

## Decision

[ADR 0036](0036-agent-runtime-runs-on-node-24.md) supersedes the runtime choice.
[ADR 0055](0055-practice-scripts-call-models-through-the-runner.md) owns the current script boundary.

## Consequences

Use the current Node runtime and script contract.
Git history retains the original source inventory.
