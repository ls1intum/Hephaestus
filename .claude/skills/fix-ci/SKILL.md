---
name: fix-ci
description: |
  Read every failing check in one pass, fix in dependency order, push once.
  Use when CI is failing, tests are broken, GitHub Actions report errors, or PR checks show red.
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

# Fix CI

Diagnose every failing check in one pass, then push once. Fixing 2 of 5 failures burns a push cycle
and a full CI run.

## 1. Wait for the run to finish

```bash
PAGER=cat gh pr view --json number,statusCheckRollup --jq '{
  pr: .number,
  failed: [.statusCheckRollup[] | select(.conclusion == "FAILURE") | .name],
  pending: [.statusCheckRollup[] | select(.status == "IN_PROGRESS" or .status == "QUEUED") | .name]
}'
```

Anything pending means you do not yet know the failure set.

## 2. Read the job summaries before the logs

The quality and test workflows write a markdown table to `$GITHUB_STEP_SUMMARY` naming, per failed
check, the exact command that fixes it, and emit the same text as `::error::` annotations. That table
is the prescription. Logs are only for what it cannot express — a test assertion, a type error.

```bash
RUN_ID=$(PAGER=cat gh run list --branch "$(git branch --show-current)" --limit 1 \
  --json databaseId,conclusion --jq '[.[] | select(.conclusion == "failure")][0].databaseId')
PAGER=cat gh api "repos/{owner}/{repo}/actions/runs/$RUN_ID/jobs" \
  --jq '[.jobs[] | select(.conclusion == "failure" and (.name | test("CI Status|all-ci") | not)) | {id, name}]'
```

Then per failed job id:

```bash
PAGER=cat gh api "repos/{owner}/{repo}/actions/jobs/$JOB_ID/logs" 2>&1 | tail -80
```

Read every failure before changing anything.

## 3. Fix in dependency order

Formatting, then lint, then types, then behaviour — an earlier fix routinely erases a later failure.
Each leg's annotation names its own command; this table is only what the annotation cannot tell you.

| Failure | What it actually means |
|---|---|
| `routeTree.gen.ts is stale` | Only a Vite build writes it. `cd webapp && pnpm run build`, then commit the file. |
| `README images are stale` | The storybook job runs `export:readme-assets` *after* `test:storybook`, so the job goes red having printed a clean pass line. Run `pnpm --filter webapp run export:readme-assets` and commit `docs/images/readme`. |
| Biome version skew | `check:biome-pin` compares `package.json`, `node_modules` and the `$schema` URLs in both `biome.jsonc` files. Fix the pin; do not reformat. |
| Migrations gate | A changelog that reached `main` was edited, renamed or deleted, or a `master.xml` `<include>` was not appended at the end. Fix forward with a new changeset; never edit the released file. |
| `verify-changesets` | The PR touches shipped code with no `.changeset/*.md`. `/land-pr` step 9 has the rules. |
| App Server leg red on a docs-only PR | Expected, not a misconfiguration: `docs/**` is inside the `application-server` paths filter, because `docs:lint` and `check:diagrams` run on that leg. |

## 4. Reproduce locally before pushing

```bash
pnpm run format
pnpm run check
```

`check` runs every leg CI runs except those needing Docker or a live credential — `docs:lint`
included. If `check` is green and CI is not, the difference is one of those.

The server reactor builds generated clients before the application. Run the unit tier with:

```bash
cd server && ./mvnw -pl application -am test -Dsurefire.includedGroups=unit -T 2C --batch-mode -q
```

`-Dgroups` is ignored: the POM binds `${surefire.includedGroups}`, and a POM element beats the
`-Dgroups` user property. `server/AGENTS.md` § Build traps has the other three tiers.

## 5. Commit and push once

```bash
git add -A
git commit -m "fix(<scope>): resolve ci failures"
git push
PAGER=cat gh pr checks --watch
```

If the same check fails twice with the same diagnosis, re-read the log rather than retrying the fix.
