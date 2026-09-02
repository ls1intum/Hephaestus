<!--
TITLE FORMAT (required):
  <type>(<scope>): <description>

  Types and scopes: CONTRIBUTING.md § Pull Request Title Guidelines (validated by commitlint.config.ts).
  Breaking changes are carried by the changeset (pre-1.0: minor + **Operators:** + .migration/<slug>.md), not the title.

  ✓ Good: feat(leaderboard): add weekly ranking filter
  ✓ Good: fix(ci): update workflow configuration
  ✗ Bad:  Added weekly ranking filter to leaderboard

BEFORE PUSHING:
  pnpm run format && pnpm run check     # Apply formatting, then run the local quality gate

AFTER API CHANGES:
  pnpm run generate:api                # Rewrites server/openapi.yaml and webapp/src/api

AFTER DATABASE/ENTITY CHANGES:
  pnpm run db:draft-changelog          # Writes this branch's changelog (needs Docker); prune it, add preconditions and rollbacks
  pnpm run db:generate-erd-docs        # After pruning
-->

## Description

<!-- 1-2 sentences: what changed, and why. -->

Fixes # <!-- Link issue if applicable, or delete this line -->

## How to test

<!-- Manual steps to verify, OR "CI covers this" for config/docs changes. -->

## Checklist

<!-- Only what CI can't check for you. Changeset presence is enforced by `verify-changesets`. -->

- [ ] My changeset summary reads as an operator/user-facing note (it becomes the changelog entry) — see `.changeset/README.md`
- [ ] If operators must act, the changeset and migration fragment state what the operator must do
- [ ] I did not commit generated-artifact changes that this PR did not cause

## Screenshots

<!-- For UI changes. Delete section if not applicable. -->
