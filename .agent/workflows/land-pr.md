---
description: Validate, branch, commit, and create PR following Hephaestus conventions
---

# Land PR

// turbo-all

## Pre-flight

```bash
PAGER=cat gh auth status
```

```bash
git status --short
```

If empty, nothing to commit - stop.

## 1. Format and Validate

```bash
npm run format
```

```bash
npm run check
```

Must pass. Fix issues before continuing.

## 2. Regenerate if needed

If API endpoints changed:

```bash
npm run generate:api:application-server:specs
npm run generate:api:application-server:client
```

If database entities changed:

```bash
npm run db:draft-changelog
npm run db:generate-erd-docs
```

If intelligence-service API changed:

```bash
MODEL_NAME=fake:model DETECTION_MODEL_NAME=fake:model npm run generate:api:intelligence-service:specs
npm run generate:api:intelligence-service:client
```

## 3. Stage beads

```bash
git add .beads/issues.jsonl 2>/dev/null || true
```

## 4. Branch check

```bash
git branch --show-current
```

If `main`, create branch:

```bash
git checkout -b <type>/<description>
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `ci`, `chore`

## 5. Commit

```bash
git add -A
git status --short
```

```bash
git commit -m "<type>(<scope>): <description>"
```

Scopes:
- Service: `webapp`, `server`, `ai`, `webhooks`, `docs`
- Infra (no release): `ci`, `deps`, `deps-dev`, `docker`, `scripts`, `security`, `db`, `no-release`
- Feature: `gitprovider`, `leaderboard`, `mentor`, `notifications`, `profile`, `teams`, `workspace`

## 6. Push

```bash
git push -u origin HEAD
```

## 7. Create PR

```bash
PAGER=cat gh pr create --base main --fill --web
```

## 8. Verify

```bash
PAGER=cat gh pr view --json url,title -q '"PR: \(.title)\nURL: \(.url)"'
```

## 9. Close beads

```bash
PR_NUM=$(PAGER=cat gh pr view --json number -q .number)
bd list --status open
```

```bash
bd close <id> --reason "PR #$PR_NUM"
```
