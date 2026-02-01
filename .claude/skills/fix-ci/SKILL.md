---
name: fix-ci
description: |
  Diagnose and fix failing CI checks on the current PR. Use when CI is failing,
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
  version: "1.0.0"
---

# Fix CI

Diagnose and fix failing CI checks on the current PR.

## 1. Get Failing Checks

```bash
PAGER=cat gh pr view --json number,statusCheckRollup --jq '{pr: .number, failed: [.statusCheckRollup[] | select(.conclusion == "FAILURE") | .name]}'
```

## 2. Get Check Details with Links

```bash
PR_NUMBER=$(PAGER=cat gh pr view --json number -q .number)
PAGER=cat gh pr checks $PR_NUMBER --json name,state,link --jq '[.[] | select(.state == "FAILURE") | {name, link}]'
```

## 3. Find the Workflow Run ID

```bash
PAGER=cat gh run list --branch $(git branch --show-current) --limit 5 --json databaseId,name,conclusion --jq '[.[] | select(.conclusion == "failure")] | .[0]'
```

## 4. Get Failed Jobs in That Run

```bash
RUN_ID=<RUN_ID_FROM_STEP_3>
OWNER=$(PAGER=cat gh repo view --json owner -q .owner.login)
REPO=$(PAGER=cat gh repo view --json name -q .name)
PAGER=cat gh api repos/$OWNER/$REPO/actions/runs/$RUN_ID/jobs --jq '[.jobs[] | select(.conclusion == "failure" and .name != "all-ci-passed") | {id, name}]'
```

## 5. Get Job Logs (THE ACTUAL ERRORS)

```bash
JOB_ID=<JOB_ID_FROM_STEP_4>
OWNER=$(PAGER=cat gh repo view --json owner -q .owner.login)
REPO=$(PAGER=cat gh repo view --json name -q .name)
PAGER=cat gh api repos/$OWNER/$REPO/actions/jobs/$JOB_ID/logs 2>&1 | grep -E "(error|Error|ERROR|fail|FAIL|❌)" | head -30
```

Or get full logs:

```bash
PAGER=cat gh api repos/$OWNER/$REPO/actions/jobs/$JOB_ID/logs 2>&1 | tail -100
```

## 6. Common Failures Reference

| Check Name | Likely Cause | Fix Command |
|------------|--------------|-------------|
| `docs-quality` | Markdown lint errors | `npm run lint:md` in `docs/` |
| `application-server-quality` | Java compile/test failure | `mvn verify` |
| `webapp-quality` | TypeScript/lint errors | `npm run check` |
| `openapi-validation` | Stale API specs | `npm run generate:api:application-server:specs` |
| `database-*-validation` | Stale DB docs/models | `npm run db:generate-erd-docs` |

## 7. Fix Locally

Run the relevant command from step 6, then:

```bash
npm run format
npm run check
```

## 8. Commit Fix

```bash
git add -A
git commit -m "fix(<scope>): resolve ci failures"
git push
```

## 9. Watch CI

```bash
PAGER=cat gh pr checks $(PAGER=cat gh pr view --json number -q .number) --watch
```
