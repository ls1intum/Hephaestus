<!--
TITLE FORMAT (required):
  <type>(<scope>): <description>

  Types: feat | fix | docs | refactor | test | ci | perf | revert
  Scopes (Service): webapp | server | docs
  Scopes (Release-triggering infra): deps | security | db | docker
  Scopes (Infra - NO RELEASE): ci | config | deps-dev | scripts | no-release
  Scopes (Feature): integration | scm | activity | practices | mentor | notifications | profile | teams | workspace

  Breaking: Add ! before colon (feat!: or feat(scope)!:)

  ✓ Good: feat(practices): add reflection card filter
  ✓ Good (No Release): fix(ci): update workflow configuration
  ✗ Bad:  Added filter to practice reflection cards

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
