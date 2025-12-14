<!--
TITLE FORMAT (required):
  <type>(<scope>): <description>

  Types: feat | fix | docs | refactor | test | chore | ci | perf | revert
  Scopes: webapp | server | ai | webhooks | docs | ci | deps | docker | db
          leaderboard | mentor | profile | workspace | teams | github | notifications
  Breaking: Add ! before colon (feat!: or feat(scope)!:)

  ✓ Good: feat(leaderboard): add weekly ranking filter
  ✗ Bad:  Added weekly ranking filter to leaderboard

BEFORE PUSHING:
  npm run format && npm run check     # Format + lint + typecheck all services

AFTER API CHANGES:
  npm run generate:api                # Regenerate all OpenAPI clients

AFTER DATABASE/ENTITY CHANGES:
  npm run db:draft-changelog          # Generate Liquibase migration
  npm run db:generate-erd-docs        # Update ERD documentation  
  npm run db:generate-models:intelligence-service  # Sync SQLAlchemy models
-->

## Description

<!-- Use "Copilot Summary" button in toolbar above, or write 1-2 sentences. -->

Fixes # <!-- Link issue if applicable, or delete this line -->

## How to test

<!-- Manual steps to verify, OR "CI covers this" for config/docs changes. -->

## Screenshots

<!-- For UI changes. Delete section if not applicable. -->
