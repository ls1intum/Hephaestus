<!--
TITLE FORMAT (required):
  <type>(<scope>): <description>

  Types: feat | fix | docs | style | refactor | perf | test | build | ci | chore | revert
  Scopes (Service): webapp | server | docs
  Scopes (Infra): ci | config | deps | deps-dev | docker | scripts | security | db | release
  Scopes (Feature): auth | integration | scm | leaderboard | mentor | notifications | profile | teams | workspace

  Breaking changes: carried by the changeset (pre-1.0 = minor + MIGRATION.md), not the title.

  ✓ Good: feat(leaderboard): add weekly ranking filter
  ✓ Good: fix(ci): update workflow configuration
  ✗ Bad:  Added weekly ranking filter to leaderboard

BEFORE PUSHING:
  bun run format && bun run check     # Format + lint + typecheck all services

AFTER API CHANGES:
  bun run generate:api                # Regenerate all OpenAPI clients

AFTER DATABASE/ENTITY CHANGES:
  bun run db:draft-changelog          # Generate Liquibase migration
  bun run db:generate-erd-docs        # Update ERD documentation
-->

## Description

<!-- 1-2 sentences: what changed, and why. -->

Fixes # <!-- Link issue if applicable, or delete this line -->

## How to test

<!-- Manual steps to verify, OR "CI covers this" for config/docs changes. -->

## Checklist

<!-- Only what CI can't check for you. Changeset presence is enforced by `verify-changesets`. -->

- [ ] My changeset summary reads as an operator/user-facing note (it becomes the changelog entry) — see `.changeset/README.md`
- [ ] If the operator must act on this change (new required env var, manual migration step), the changeset summary says how (`**Operators:** …`) and `MIGRATION.md` is updated

## Screenshots

<!-- For UI changes. Delete section if not applicable. -->
