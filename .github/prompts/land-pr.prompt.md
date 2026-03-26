---
mode: agent
description: Validate, branch, commit, and create PR following Hephaestus conventions
---

# Land PR

## 1. Check for Changes

```bash
git status --short
```

If empty, nothing to commit - stop.

## 2. Detect Changed Components

```bash
git diff --name-only HEAD
```

Map paths to components (mirrors CI's dorny/paths-filter config):
- `webapp/**` → webapp changed
- `server/application-server/**` OR `scripts/db-utils.sh` → app-server changed
- `server/intelligence-service/**` OR `scripts/install-platform-binaries.mjs` OR `scripts/generate-mermaid-erd.ts` OR `scripts/postprocess-openapi-java.ts` → intelligence changed
- `server/webhook-ingest/**` → webhook changed
- `package.json` OR `package-lock.json` OR `.node-version` → webapp + intelligence + webhook changed
- `docs/**` → docs-only (skip all validation if nothing else changed)

**Transitive dependency**: If app-server changed, also mark intelligence as changed
(intelligence-service depends on app-server schema for DB models).

## 3. Format

```bash
npm run format
```

Formatting must NEVER be a reason for remote CI failure. This applies formatting
in write mode, not just check mode.

## 4. Check (Lint + Typecheck)

```bash
npm run check
```

Must pass. Fix issues before continuing.

## 5. Regenerate if Needed

**API endpoints changed (app-server controllers/DTOs):**

```bash
npm run generate:api:application-server:specs
npm run generate:api:application-server:client
```

**Database entities changed:**

```bash
npm run db:draft-changelog
npm run db:generate-erd-docs
```

**Intelligence-service API changed:**

```bash
MODEL_NAME=fake:model npm run generate:api:intelligence-service:specs
npm run generate:api:intelligence-service:client
```

## 6. Build Affected TS Services

If intelligence changed:

```bash
npm run build:intelligence-service
```

If webhook changed:

```bash
npm run build:webhook-ingest
```

Build failures catch path alias and import issues that typecheck alone misses.

## 7. Unit Tests for Affected Components

Run ONLY tests for changed components. Order: fastest first.

If webhook changed:

```bash
npm run test:webhook-ingest
```

If webapp changed:

```bash
npm run test:webapp
```

If intelligence changed:

```bash
npm run test:intelligence-service:unit
```

If app-server changed (and mvn available):

```bash
cd server/application-server && ./mvnw test -Dsurefire.includedGroups="unit" -Dmaven.test.skip=false -T 2C --batch-mode -q && cd ../..
```

ALL tests must pass before proceeding.

## 8. OpenAPI Sync Check

If app-server or intelligence changed:

```bash
npm run generate:api
git diff --quiet || echo "WARNING: OpenAPI specs were out of sync - staging changes"
```

Stage any drift that was caught.

## 9. Final Validation Pass

Regeneration can produce unformatted code. Run one final pass:

```bash
npm run format
npm run check
```

Both must pass.

## 10. Create Branch (if on main)

```bash
git branch --show-current
```

If `main`, create branch:

```bash
git checkout -b <type>/<description>
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `ci`, `chore`

## 11. Commit

```bash
git add -A
git commit -m "<type>(<scope>): <description>"
```

**Scopes:**

- Service: `webapp`, `server`, `ai`, `webhooks`, `docs`
- Infra (no release): `ci`, `config`, `deps`, `deps-dev`, `docker`, `scripts`, `security`, `db`, `no-release`
- Feature: `gitprovider`, `leaderboard`, `mentor`, `notifications`, `profile`, `teams`, `workspace`

## 12. Push

```bash
git push -u origin HEAD
```

## 13. Check if PR Exists

```bash
PAGER=cat gh pr view --json number,url 2>/dev/null && echo "PR exists - skip creation" || echo "No PR - create one"
```

## 14. Create PR (if needed)

Skip if step 13 showed "PR exists".

```bash
PAGER=cat gh pr create --base main \
  --title "<type>(<scope>): <description>" \
  --body "## Description

<1-2 sentences describing what and why>

## How to Test

<steps to verify, or 'CI covers this'>"
```

## 15. Verify

```bash
PAGER=cat gh pr view --json url,title -q '"PR: \(.title)\nURL: \(.url)"'
```
