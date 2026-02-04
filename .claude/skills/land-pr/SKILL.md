---
name: land-pr
description: |
  Validate, branch, commit, and create PR following Hephaestus conventions.
  Use when ready to ship changes, create a pull request, or push work.
disable-model-invocation: true
allowed-tools:
  - Bash(gh *)
  - Bash(git *)
  - Bash(npm *)
  - Read
  - Grep
  - Glob
metadata:
  source: internal
  version: "1.0.0"
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

## 4. Create Branch (if on main)

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
git commit -m "<type>(<scope>): <description>"
```

**Scopes:**

- Service: `webapp`, `server`, `ai`, `webhooks`, `docs`
- Infra (no release): `ci`, `config`, `deps`, `deps-dev`, `docker`, `scripts`, `security`, `db`, `no-release`
- Feature: `gitprovider`, `leaderboard`, `mentor`, `notifications`, `profile`, `teams`, `workspace`

## 6. Push

```bash
git push -u origin HEAD
```

## 7. Check if PR Exists

```bash
PAGER=cat gh pr view --json number,url 2>/dev/null && echo "PR exists - skip creation" || echo "No PR - create one"
```

## 8. Create PR (if needed)

Skip if step 7 showed "PR exists".

```bash
PAGER=cat gh pr create --base main \
  --title "<type>(<scope>): <description>" \
  --body "## Description

<1-2 sentences describing what and why>

## How to Test

<steps to verify, or 'CI covers this'>"
```

## 9. Open in Browser

```bash
PAGER=cat gh pr view --web
```

## 10. Verify

```bash
PAGER=cat gh pr view --json url,title -q '"PR: \(.title)\nURL: \(.url)"'
```
