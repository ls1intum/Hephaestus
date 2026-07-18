# Changesets

A **changeset** here is a release note: a `.changeset/*.md` file that becomes `CHANGELOG.md` and drives
the version bump. (Not a Liquibase `<changeSet>` — a schema change needs both.)

Every PR that changes shipped code (anything under `server/`, `webapp/`, or `docker/` except tests and in-tree docs) ships one; CI (`verify-changesets`) enforces it.

```bash
pnpm changeset          # write one (pick the bump, describe the change)
pnpm changeset --empty  # no user-facing effect — then write why in the file body (non-interactive)
```

The summary lands in the changelog **verbatim**, in the operator/user's voice — lead with what they can now
do, or the symptom a fix removes. No class/hook/file names. No agent-attribution trailers. One changeset per
user-visible change; unsure whether it's visible? Add one — a reviewer can delete it, a missing note can't.

No TTY (agents, CI)? `pnpm changeset` is interactive — instead write `.changeset/<slug>.md` by hand in the
format shown below. That is the one sanctioned hand-write; never touch `CHANGELOG.md` directly.

**Bump = the operator's upgrade cost:**

| Bump | Operator upgrade | Examples |
| --- | --- | --- |
| `patch` | no action | bug fix, internal change, additive auto-applied migration |
| `minor` | no action | new capability; note any new *optional* env var / flag in the summary |
| `major` | must act first | required new env var, removed/renamed config, destructive/manual migration, dropped API — state the action + update `MIGRATION.md` |

**Pre-1.0 (now): never pick `major`** — it would cut 1.0.0; CI rejects it. Breaking changes ride in `minor`
instead, so a pre-1.0 `minor` is *not* guaranteed zero-action: if the operator must act, say so
(`**Operators:** …`) and update `MIGRATION.md` exactly as a `major` would.

Example (a migration-bearing fix):

```md
---
"hephaestus": minor
---

Fixes duplicate leaderboard entries after a team rename.
```

Full flow and rules: [release management guide](https://ls1intum.github.io/Hephaestus/contributor/release-management).
