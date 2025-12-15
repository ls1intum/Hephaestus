---
mode: agent
description: Validate, branch, commit, and create PR following Hephaestus conventions
---

# Land PR

Finalize changes and open a pull request following project conventions.

## Pre-flight

```bash
# Verify gh CLI is authenticated
PAGER=cat gh auth status

# Verify there are changes to commit
git status --short
```

If no output from git status, stop - nothing to commit.

## 1. Format and Validate

From AGENTS.md section 3 & 10:

```bash
npm run format
npm run check
```

Both must pass. If check fails, fix issues before continuing.

## 2. Regenerate if needed

**If you modified API endpoints:**
```bash
npm run generate:api:application-server:specs
npm run generate:api:application-server:client
```

**If you modified database entities:**
```bash
npm run db:draft-changelog
npm run db:generate-erd-docs
```

**If you modified intelligence-service API:**
```bash
MODEL_NAME=fake:model DETECTION_MODEL_NAME=fake:model npm run generate:api:intelligence-service:specs
npm run generate:api:intelligence-service:client
```

## 3. Stage beads issues

```bash
git add .beads/issues.jsonl 2>/dev/null || true
```

## 4. Create branch if on main

```bash
git branch --show-current
```

If output is `main`, create a feature branch:

```bash
git checkout -b <type>/<description>
```

**Types** (from CONTRIBUTING.md):
- `feat` - New feature (triggers minor release)
- `fix` - Bug fix (triggers patch release)
- `docs` - Documentation only
- `refactor` - Code change, no behavior change
- `test` - Test changes
- `ci` - CI/CD changes
- `chore` - Maintenance

## 5. Commit

```bash
git add -A
git commit -m "<type>(<scope>): <description>"
```

**Scopes** (from CONTRIBUTING.md):

Service scopes: `webapp`, `server`, `ai`, `webhooks`, `docs`

Infrastructure scopes (NO release): `ci`, `deps`, `deps-dev`, `docker`, `scripts`, `security`, `db`, `no-release`

Feature scopes: `gitprovider`, `leaderboard`, `mentor`, `notifications`, `profile`, `teams`, `workspace`

**Rules:**
- Lowercase description
- Imperative mood ("add" not "added")
- No period at end
- Max 72 characters

## 6. Push

```bash
git push -u origin HEAD
```

## 7. Create PR

```bash
PAGER=cat gh pr create --base main --fill --web
```

Or with explicit body:

```bash
PAGER=cat gh pr create --base main \
  --title "<type>(<scope>): <description>" \
  --body "## Description

<1-2 sentences describing what and why>

Fixes #<issue-number>

## How to test

<steps to verify, or 'CI covers this'>

## 7. Create PR
 
Generate a PR title and body based on your changes.
**Do not use --web**. We want to create it automatically.
 
```bash
PAGER=cat gh pr create --base main --fill
```
 
*Note: If --fill is insufficient, generate a specific body matching the template.*
 
## 8. Open in Browser
 
```bash
PAGER=cat gh pr view --web
```
 
## 9. Verify URL
 
```bash
PAGER=cat gh pr view --json url,title -q '"PR: \(.title)\nURL: \(.url)"'
```
 
## 10. Close beads
 
```bash
PR_NUM=$(PAGER=cat gh pr view --json number -q .number)
bd list --status open
```
 
```bash
bd close <id> --reason "PR #$PR_NUM"
```
