---
description: Validate, branch, commit, and create PR following Hephaestus conventions
subtask: true
---

# Land PR

## 1. Check for Changes

```bash
git status --short
```

If empty, nothing to commit - stop.

## 2. Format and Validate

```bash
npm run format
npm run check
```

Both must pass. Fix issues before continuing.

## 3. Regenerate if Needed

**API endpoints changed:**

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
MODEL_NAME=fake:model DETECTION_MODEL_NAME=fake:model npm run generate:api:intelligence-service:specs
npm run generate:api:intelligence-service:client
```

## 4. Stage Beads

```bash
git add .beads/issues.jsonl 2>/dev/null || true
```

## 5. Create Branch (if on main)

```bash
git branch --show-current
```

If `main`, create branch:

```bash
git checkout -b <type>/<description>
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `ci`, `chore`

## 6. Commit

```bash
git add -A
git commit -m "<type>(<scope>): <description>"
```

**Scopes:**

- Service: `webapp`, `server`, `ai`, `webhooks`, `docs`
- Infra (no release): `ci`, `config`, `deps`, `deps-dev`, `docker`, `scripts`, `security`, `db`, `no-release`
- Feature: `gitprovider`, `leaderboard`, `mentor`, `notifications`, `profile`, `teams`, `workspace`

## 7. Push

```bash
git push -u origin HEAD
```

## 8. Check if PR Exists

```bash
PAGER=cat gh pr view --json number,url 2>/dev/null && echo "PR exists - skip creation" || echo "No PR - create one"
```

## 9. Create PR (if needed)

Skip if step 8 showed "PR exists".

```bash
PAGER=cat gh pr create --base main \
  --title "<type>(<scope>): <description>" \
  --body "## Description

<1-2 sentences describing what and why>

## How to Test

<steps to verify, or 'CI covers this'>"
```

## 10. Open in Browser

```bash
PAGER=cat gh pr view --web
```

## 11. Verify

```bash
PAGER=cat gh pr view --json url,title -q '"PR: \(.title)\nURL: \(.url)"'
```

## 12. Close Beads Issue

```bash
PR_NUM=$(PAGER=cat gh pr view --json number -q .number)
bd list --status open
bd close <id> --reason "PR #$PR_NUM"
```
