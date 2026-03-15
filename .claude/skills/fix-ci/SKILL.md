---
name: fix-ci
description: |
  Diagnose and fix ALL failing CI checks on the current PR in a single pass.
  Collects all failures first, then prescribes fixes. Use when CI is failing,
  tests are broken, GitHub Actions report errors, or PR checks show red.
disable-model-invocation: true
allowed-tools:
  - Bash(gh *)
  - Bash(git *)
  - Bash(npm *)
  - Bash(mvn *)
  - Read
  - Grep
  - Glob
metadata:
  source: internal
  version: "2.0.0"
---

# Fix CI

Diagnose and fix ALL failing CI checks in ONE pass. Never push until all
known issues are resolved.

## 1. Ensure All Checks Are Complete

```bash
PAGER=cat gh pr view --json number,statusCheckRollup --jq '{
  pr: .number,
  failed: [.statusCheckRollup[] | select(.conclusion == "FAILURE") | .name],
  pending: [.statusCheckRollup[] | select(.status == "IN_PROGRESS" or .status == "QUEUED") | .name]
}'
```

If there are pending checks, wait for all checks to complete before diagnosing.
Fixing 2 of 5 failures wastes a push cycle.

## 2. Get ALL Failed Job IDs At Once

```bash
RUN_ID=$(PAGER=cat gh run list --branch $(git branch --show-current) --limit 1 --json databaseId,conclusion --jq '[.[] | select(.conclusion == "failure")][0].databaseId')
echo "Run ID: $RUN_ID"
```

Then get all failed jobs:

```bash
PAGER=cat gh api repos/{owner}/{repo}/actions/runs/$RUN_ID/jobs --jq '[.jobs[] | select(.conclusion == "failure" and (.name | test("CI Status|all-ci") | not)) | {id, name}]'
```

## 3. Get ALL Job Logs At Once

For EACH failed job ID from step 2, get logs in a single loop:

```bash
for JOB_ID in <SPACE_SEPARATED_JOB_IDS>; do
  echo "=== JOB $JOB_ID ==="
  PAGER=cat gh api repos/{owner}/{repo}/actions/jobs/$JOB_ID/logs 2>&1 | tail -80
  echo ""
done
```

Read ALL output before making any fixes.

## 4. Classify ALL Failures

Before fixing anything, categorize every failure into this table.
**Fix in this order** (earlier fixes often resolve later issues):

| Priority | Category | Symptoms | Fix Command |
|----------|----------|----------|-------------|
| 1 | Formatting | "Formatting failed", biome/prettier diff | `npm run format` |
| 2 | Lint | Biome lint errors | `npm run check:fix` |
| 3 | TypeScript | TS2xxx errors, type mismatch | Fix the type error in source |
| 4 | Build failure | Compilation errors, missing exports | Fix imports/exports, verify with `npm run build:intelligence-service` or `npm run build:webhook-ingest` |
| 5 | Webapp tests | "FAIL" in webapp test output | Fix test or source, verify with `npm run test:webapp` |
| 5 | App server tests | Maven test failures, assertion errors | Fix test or source, verify with `cd server/application-server && ./mvnw test -Dsurefire.includedGroups="unit" -Dmaven.test.skip=false -T 2C --batch-mode -q` |
| 5 | Intelligence tests | Vitest failures in intelligence-service | Fix test or source, verify with `npm run test:intelligence-service:unit` |
| 5 | Webhook tests | Vitest failures in webhook-ingest | Fix test or source, verify with `npm run test:webhook-ingest` |
| 6 | OpenAPI sync | "OpenAPI out of sync" | `npm run generate:api` |
| 6 | DB schema | "Schema drift detected" | `npm run db:draft-changelog` |
| 6 | DB ERD | "ERD outdated" | `npm run db:generate-erd-docs` |
| 6 | DB models | "DB models out of sync" | `npm run db:generate-models:intelligence-service` |

## 5. Fix ALL Issues

Work through the entire list. Fix root causes, not symptoms.

IMPORTANT: Do NOT push after fixing only one failure if multiple exist.
Fix everything first.

## 6. Validate Locally Before Pushing

After ALL fixes are applied, run local validation:

```bash
npm run format
npm run check
```

Then run tests for ALL components that had failures:

```bash
# If webapp tests failed:
npm run test:webapp

# If webhook-ingest tests failed:
npm run test:webhook-ingest

# If intelligence-service tests failed:
npm run test:intelligence-service:unit

# If app-server tests failed:
cd server/application-server && ./mvnw test -Dsurefire.includedGroups="unit" -Dmaven.test.skip=false -T 2C --batch-mode -q && cd ../..
```

ALL must pass locally before pushing.

## 7. Regenerate If Needed

If any OpenAPI or DB validation failed:

```bash
npm run generate:api
npm run db:generate-erd-docs
```

Run format + check again after regeneration:

```bash
npm run format
npm run check
```

## 8. Commit and Push (ONCE)

```bash
git add -A
git commit -m "fix(<scope>): resolve ci failures"
git push
```

## 9. Monitor

```bash
PAGER=cat gh pr checks $(PAGER=cat gh pr view --json number -q .number) --watch
```

## Rules

1. NEVER push after fixing only one failure if multiple failures exist
2. ALWAYS run local validation (format + check + affected tests) before pushing
3. If step 1 shows pending checks, wait for all checks to complete first
4. If the same CI check fails twice in a row, investigate deeper - do not retry the same approach
