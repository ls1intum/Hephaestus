---
name: land-pr
description: |
  Validate, regenerate, branch, commit, push, and open the PR.
  Runs the local CI mirror first, so a remote build cannot fail on something a local gate catches.
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

`.github/workflows/cicd.yml` holds the `dorny/paths-filter` block that decides this; read it rather
than guessing, and note the two shapes that surprise people:

- **`scripts/**` and the root lint, formatter, TypeScript, and package configuration listed in
  the workflow trigger both the webapp and app-server legs**, because the gates they configure run
  on both.
- **`docs/**` triggers the app-server leg.** There is no such thing as a docs-only PR that skips
  validation: `docs:lint` and `check:diagrams` run there.

## 3. Format, then check

```bash
pnpm run format
pnpm run check
```

`check` is the complete local quality gate — every leg is listed under `check` in the root
`package.json`, and every one also runs in CI. CI additionally runs service tests, builds, images,
security checks, and workflow-specific gates. Formatting must never be the reason a remote build
fails.

## 4. Regenerate what your change invalidated

Generated artefacts are never hand-edited, and regeneration is destructive — it empties the target
directory first, so stash local edits.

```bash
pnpm run generate:api          # controllers or DTOs changed: rewrites openapi.yaml AND webapp/src/api
pnpm run db:draft-changelog    # entities changed (needs Docker); then prune the diff to real deltas
pnpm run db:generate-erd-docs  # after any changelog change
```

`generate:api:application-server:specs` **fails when a port it needs is busy** — HTTP, management, or
the JMX port it defaults to. It restores the previous spec rather than committing an empty one, so
the cost is a wasted Maven cycle. Pass free ports; the exact invocation is in `server/AGENTS.md`
§ OpenAPI generation ports.

## 5. Run the tests your diff can break

```bash
pnpm run test:webapp
cd server && ./mvnw -pl application -am test -Dsurefire.includedGroups=unit -T 2C --batch-mode -q
```

`-am` is required so a cold checkout builds the generated-client dependency before the application.

## 6. Re-run format + check

Regeneration produces unformatted output. Run step 3 again; both must be green on the final tree.

## 7. Changeset

A PR touching `server/`, `webapp/` or `docker/` needs a `.changeset/*.md` or `verify-changesets`
fails it.

```bash
pnpm changeset          # user-facing: pick the bump, write the summary in the operator's voice
pnpm changeset --empty  # no user-facing effect; say why in the body
```

`pnpm changeset` is interactive — with no TTY, hand-write `.changeset/<slug>.md` (`.changeset/README.md`
has the shape). The summary lands in `CHANGELOG.md` verbatim, so it names what an operator or user can
now do, not a class or a file. **Pre-1.0, never pick `major`** — it would cut 1.0.0 and the gate
rejects it; a breaking change rides in `minor` with `**Operators:** …` in the summary and a
`MIGRATION.md` update.

Touching `db/changelog/` without touching `.changeset/` is always wrong — they are different things
with the same word in them.

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
BODY
)"
```

`.github/PULL_REQUEST_TEMPLATE.md` carries the checklist the reviewer expects; keep its headings.

## 10. Verify

```bash
PAGER=cat gh pr view --json url,title -q '"PR: \(.title)\nURL: \(.url)"'
```
