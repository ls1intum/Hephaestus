---
name: land-pr
description: |
  Validate, regenerate, branch, commit, push, and open the PR.
  Runs the local quality gate first, so a remote build cannot fail on something a local gate catches.
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

## 1. See what changed

```bash
git status --short
git diff --name-only HEAD
```

Nothing staged or modified means nothing to land.

## 2. Know which CI legs your diff triggers

`detect-changes` in `.github/workflows/cicd.yml` holds the path filters; read it rather than
guessing. Two shapes surprise people: `docs/**`, `scripts/**` and the root lint, format and
tsconfig files select the `Tooling and Docs` leg, not the App Server leg; and `package.json` or
`pnpm-lock.yaml` select every source leg.

## 3. Format, then check

```bash
pnpm run format
pnpm run check
```

`check` is the complete local quality gate — every task is listed in the `quality` array in
`vite.config.ts`, and every one also runs in CI. CI additionally runs service tests, builds, images,
security checks, and workflow-specific gates. Formatting must never be the reason a remote build
fails.

## 4. Regenerate what your change invalidated

Generated artefacts are never hand-edited, and regeneration is destructive — it empties the target
directory first, so stash local edits.

```bash
pnpm run generate:api          # controllers or DTOs changed: rewrites openapi.yaml AND webapp/src/api
pnpm run db:draft-changelog    # entities changed (needs Docker); writes and wires the changelog, then prune it
pnpm run db:generate-erd-docs  # after pruning a changelog
```

`generate:api:application-server:specs` packages the reactor and boots the executable JAR on ports
it allocates itself, so nothing needs freeing; root `AGENTS.md` § Command caveats covers the
`HEPHAESTUS_APPLICATION_JAR` shortcut for a JAR you already built.

## 5. Run the tests your diff can break

```bash
pnpm run test:webapp
pnpm run test:server:unit
```

## 6. Re-run format + check

Regeneration produces unformatted output. Run step 3 again; both must be green on the final tree.

## 7. Changeset

A PR touching `server/`, `webapp/` or `docker/` needs a `.changeset/*.md` or `verify-changesets`
fails it.

```bash
pnpm changeset          # user-facing: pick the bump, write the summary in the operator's voice
pnpm changeset --empty  # no user-facing effect; say why in the body
```

`pnpm changeset` is interactive — with no TTY, hand-write `.changeset/<slug>.md`. The rules — voice,
bump, pre-1.0 `minor` with `**Operators:**` and a `.migration/<slug>.md` fragment, never
`MIGRATION.md` — are in `.changeset/README.md`. Touching `db/changelog/` without touching
`.changeset/` is always wrong.

## 8. Branch, commit, push

```bash
git branch --show-current    # if main, branch first
git checkout -b <type>/<description>
git add -A
git commit -m "<type>(<scope>): <description>"
git push -u origin HEAD
```

Types and scopes are enumerated in `commitlint.config.ts`, which is what validates the PR title —
read it there rather than from a copy. No `!` in the title; pre-1.0 breaking changes are carried by
the changeset, not the header.

## 9. Open the PR

```bash
PAGER=cat gh pr view --json number,url 2>/dev/null || PAGER=cat gh pr create --base main --title "<type>(<scope>): <description>" --body "$(cat <<'BODY'
## Description

<1-2 sentences: what and why>

## How to test

<manual steps, or "CI covers this">

## Checklist

<the items from .github/PULL_REQUEST_TEMPLATE.md that apply>
BODY
)"
```

## 10. Verify

```bash
PAGER=cat gh pr view --json url,title -q '"PR: \(.title)\nURL: \(.url)"'
```
