---
name: land-pr
description: |
  Validate, branch, commit, and create PR following Hephaestus conventions.
  Runs local CI mirror before pushing to prevent remote CI failures.
disable-model-invocation: true
allowed-tools:
  - Bash(gh *)
  - Bash(git *)
  - Bash(pnpm *)
  - Bash(./mvnw *)
  - Read
  - Grep
  - Glob
metadata:
  source: internal
  version: "2.0.0"
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
- `server/**` OR `scripts/db-utils.sh` → app-server changed
- `package.json` OR `package-lock.json` OR `.node-version` → webapp changed
- `docs/**` → docs-only (skip all validation if nothing else changed)

## 3. Format

```bash
pnpm run format
```

Formatting must NEVER be a reason for remote CI failure. This applies formatting
in write mode, not just check mode.

## 4. Check (Lint + Typecheck)

```bash
pnpm run check
```

Must pass. Fix issues before continuing.

## 5. Regenerate if Needed

**API endpoints changed (app-server controllers/DTOs):**

```bash
pnpm run generate:api:application-server:specs
pnpm run generate:api:application-server:client
```

**Database entities changed:**

```bash
pnpm run db:draft-changelog
pnpm run db:generate-erd-docs
```

## 6. Unit Tests for Affected Components

Run ONLY tests for changed components. Order: fastest first.

If webapp changed:

```bash
pnpm run test:webapp
```

If app-server changed (and mvn available):

```bash
cd server && ./mvnw test -Dsurefire.includedGroups="unit" -Dmaven.test.skip=false -T 2C --batch-mode -q && cd ../..
```

ALL tests must pass before proceeding.

## 7. OpenAPI Sync Check

If app-server changed:

```bash
pnpm run generate:api
git diff --quiet || echo "WARNING: OpenAPI specs were out of sync - staging changes"
```

Stage any drift that was caught.

## 8. Final Validation Pass

Regeneration can produce unformatted code. Run one final pass:

```bash
pnpm run format
pnpm run check
```

Both must pass.

## 9. Changeset (required for shipped-code changes)

If this PR changes `server/`, `webapp/`, or `docker/`, it must carry a changeset or CI (`verify-changesets`) fails:

```bash
pnpm changeset          # user-facing change: pick the bump, describe it for operators
pnpm changeset --empty  # no user-facing effect (refactor/test/docs-only)
```

Pre-1.0: never pick `major`. Stage the generated `.changeset/*.md` before committing. `pnpm changeset` is
interactive — with no TTY, write `.changeset/<slug>.md` by hand (`.changeset/README.md` shows the format).

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

- Service: `webapp`, `server`, `docs`
- Infra: `ci`, `config`, `deps`, `deps-dev`, `docker`, `scripts`, `security`, `db`, `release`
- Feature: `auth`, `integration`, `scm`, `leaderboard`, `mentor`, `notifications`, `profile`, `teams`, `workspace`

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
