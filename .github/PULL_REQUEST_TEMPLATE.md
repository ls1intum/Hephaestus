<!--
TITLE FORMAT (required):
  <type>(<scope>): <description>

  Types: feat | fix | docs | refactor | test | ci | perf | revert
  Scopes (Service): webapp | server | ai | docs
  Scopes (Infra - NO RELEASE): ci | config | deps | deps-dev | docker | scripts | security | db | no-release
  Scopes (Feature): gitprovider | leaderboard | mentor | notifications | profile | teams | workspace

  Breaking: Add ! before colon (feat!: or feat(scope)!:)

  ✓ Good: feat(leaderboard): add weekly ranking filter
  ✓ Good (No Release): fix(ci): update workflow configuration
  ✗ Bad:  Added weekly ranking filter to leaderboard

BEFORE PUSHING:
  pnpm run format && pnpm run check     # Format + lint + typecheck all services

AFTER API CHANGES:
  pnpm run generate:api                # Regenerate all OpenAPI clients

AFTER DATABASE/ENTITY CHANGES:
  pnpm run db:draft-changelog          # Generate Liquibase migration
  pnpm run db:generate-erd-docs        # Update ERD documentation
-->

## Description

<!-- Use "Copilot Summary" button in toolbar above, or write 1-2 sentences. -->

Fixes # <!-- Link issue if applicable, or delete this line -->

## How to test

<!-- Manual steps to verify, OR "CI covers this" for config/docs changes. -->

## Screenshots

<!-- For UI changes. Delete section if not applicable. -->
