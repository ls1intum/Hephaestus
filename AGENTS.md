# Hephaestus

**⚠️ Do NOT stage, commit, or push unless you have permission to do so.**

Hephaestus reviews software practices for engineering teams: it ingests work from GitHub, GitLab,
Slack and Outline, has an LLM agent observe it against a curated practice catalogue, and delivers
feedback to the developer in-context, in a reflection page, or in conversation.

- `server/` — Spring Boot 4 + Java 21 + Spring Modulith 2. Liquibase-managed PostgreSQL, SQL-layer
  multi-tenancy (`core/tenancy/`), generated `openapi.yaml`. Three runtime roles (`server`, `worker`,
  `webhook`) selected by `hephaestus.runtime.*` — ADR 0005 and ADR 0008. See `server/AGENTS.md`.
- `webapp/` — React 19 SPA, TanStack Router/Query, Tailwind 4, generated API client in `src/api/**`.
  See `webapp/AGENTS.md`.
- `docs/` — contributor docs published to GitHub Pages, including the generated ERD.

Node is pinned in `.node-version` and drives the repo's own tooling; the repo is pnpm 11 workspaces.
`webapp` is the main TypeScript package; `docs` is a second, with its own tooling. The agent sandbox runs no Node at all: the runner
(`server/src/main/resources/agent/`), the precompute runner and lib (`docker/agents/precompute/`) and
the per-practice precompute scripts (`server/src/main/resources/practices/precompute/`) are
TypeScript executed directly by Bun, whose version the agent image pins in
`docker/agents/pi/Dockerfile`. They are type-checked as one project via `tsconfig.agents.json` and
linted and formatted via the root `biome.jsonc`. JDK 21, and Docker for the database helpers.

## Skills

Load these rather than reasoning from scratch.

| Skill | When |
|-------|------|
| `/storybook-components` | Component props, stories, play functions, a11y posture; grading a webapp diff |
| `/composition-patterns` | Compound components, render props, React 19 API shape |
| `/web-design-guidelines` | UI accessibility and UX review |
| `/react-best-practices` | Frontend performance (~40% Next.js-specific — check applicability) |
| `/fix-ci`, `/land-pr`, `/resolve-review` | CI triage, opening a PR, answering review comments |
| `/gh-stack` | Creating and maintaining stacked pull requests |

## Quality gates

Finish every change set with `pnpm run format` then `pnpm run check`, so styling and type checks
reflect the final state. Document any skipped gate in the PR description.

| Command | Does |
|---|---|
| `pnpm run format` / `format:check` | Apply / verify formatting (Java + TypeScript) |
| `pnpm run check` | The full gate — every leg it runs is listed as `check` in the root `package.json` |
| `pnpm run test:webapp` | Vitest |
| `pnpm run test:agents` | Agent runtime and precompute specs, on Bun |
| `mvn test -P'!quick'` | Server unit tests — see `server/AGENTS.md` for the four tiers and why `!quick` is mandatory |

Naming: `format` applies, `format:check` verifies read-only for CI, `lint` lints, `check` is the
comprehensive verification. A `:webapp`, `:server` or `:agents` suffix scopes any of them; `:java`
scopes `format` and `lint` only — the Java leg of `check` is `check:server`.

### Lint and format scopes

Two TypeScript trees, and **the linter is not the same in both**:

| Tree | Lints | Formats | Scripts |
|---|---|---|---|
| the SPA (`webapp/`) | oxlint (`webapp/.oxlintrc.json`) | Biome (`webapp/biome.jsonc`) | `format:webapp`, `lint:webapp`, `check:webapp` |
| the Bun agent runtime, its specs, both precompute trees, `scripts/**` | Biome (`biome.jsonc`) | Biome (`biome.jsonc`) | `format:agents`, `lint:agents`, `check:agents` |

Both Biome configs run the pinned `@biomejs/biome` from the root `package.json`; `biome.jsonc` is the
monorepo root config and `webapp/biome.jsonc` declares `"root": false`, so each tree keeps its own
rules. Each config carries the reasoning for its own deltas — read it before switching a rule either
way. `check:agents` also runs `typecheck:agents` and `typecheck:scripts`; `check:webapp` does **not** run
`typecheck:webapp`, which is a separate leg. `check:webapp:fix` and `check:agents:fix` apply every
safe fix.

The SPA's house lint rules are oxlint JS plugins in `webapp/tools/oxlint/rules/`, each with a
`RuleTester` suite beside it — `webapp/AGENTS.md` § Linting.

## Generated artefacts — never hand-edit, regenerate

| Artefact | Command |
|---|---|
| `server/openapi.yaml` | `pnpm run generate:api:application-server:specs` |
| `webapp/src/api/**` | `pnpm run generate:api:application-server:client` |
| `docs/contributor/erd/schema.mmd` | `pnpm run db:generate-erd-docs` |
| `webapp/src/routeTree.gen.ts` | TanStack Router Vite plugin |
| `server/target/generated-sources/graphql-*` | GraphQL codegen, run manually — do not `mvn clean` |

Regeneration is destructive: it empties the target directory, so stash local edits first. Commit
generated clients alongside the API change that caused them.

## Database changes (Liquibase)

Changelogs live in `server/src/main/resources/db/changelog/` and are included from `master.xml`.
Draft with `pnpm run db:draft-changelog` (needs Docker; CI sets `CI=true`), then **prune the diff to
the real deltas** — never commit the raw diff. Then `pnpm run db:generate-erd-docs`.

- **Filename**: `<epoch-ms-timestamp>_changelog.xml` — a real millisecond timestamp (`date +%s%3N`),
  strictly greater than the latest existing one. No round numbers, no descriptive suffix. `changeSet`
  ids are `<timestamp>-1`, `-2`, … Each is preconditioned (`onFail="MARK_RAN"`) with a `<rollback>`.
- **One consolidated changelog per branch.** Every schema change for the branch is another
  `<changeSet>` inside that one file. A PR with two new changelog files is wrong.
- **Released changelogs are immutable, and CI enforces it.** Once a file reaches `main` it is never
  edited, renamed or deleted, and `master.xml` is **append-only** — new `<include>`s go at the end, and
  the committed list is not globally timestamp-sorted. The `Migrations` gate fails otherwise. Fix
  mistakes forward. Destructive changes deprecate-then-remove across two releases:
  `docs/contributor/database-migration.mdx`.

## Pull requests

Title follows Conventional Commits (types and scopes in `CONTRIBUTING.md`); the PR template walks the
rest.

### Stacked pull requests

For dependent work that is easier to review in coherent layers, follow `CONTRIBUTING.md` § Stacked Pull
Requests. Plan the dependency order before creating layers, put foundations below their dependants,
and do not create a layer that cannot satisfy the normal PR, release, and quality requirements on its
own.

When `gh stack` manages a stack, use `/gh-stack` instead of manually retargeting its
branches. Restack descendants after changing a lower layer. Do not stage, commit, push, submit, or
merge without explicit user permission.

### Changesets (release notes)

`.changeset/*.md` files that become `CHANGELOG.md` and drive the version bump. **Not** the same thing
as a Liquibase `<changeSet>` — a schema change needs both. Full flow:
`docs/contributor/release-management.mdx`.

- **Every PR that changes shipped code** (`server/`, `webapp/`, `docker/`, excluding tests and in-tree
  docs) ships one. `pnpm changeset` is interactive; with no TTY, hand-write `.changeset/<slug>.md` with
  frontmatter `"hephaestus": <bump>` and the summary as the body (shape in `.changeset/README.md`).
  This is the one sanctioned hand-write — never hand-edit `CHANGELOG.md`. If the change is invisible to
  operators and users, `pnpm changeset --empty` and say why in the body. CI (`verify-changesets`) fails
  a shipped-code PR with neither.
- **The summary lands in `CHANGELOG.md` verbatim, in the operator's or user's voice.** Lead with what
  they can now do, or the symptom a fix removes. No class names, hook names or file paths. No
  co-authored-by or agent-attribution trailers.
  ✗ `Refactor LeaderboardService scoring hooks` → ✓ `Fixes duplicate leaderboard entries after a team rename.`
  One changeset per user-visible change.
- **The bump is the operator's upgrade cost, not code semantics.** `patch` — no action needed.
  `minor` — new capability; name any new *optional* env var. `major` — the operator must act, and
  `MIGRATION.md` is updated. **Pre-1.0 (now): never pick `major`** — it would cut 1.0.0 and
  `verify-changesets` rejects it. Breaking changes ride in `minor`, so a pre-1.0 `minor` is not
  guaranteed zero-action: say `**Operators:** …` in the summary and update `MIGRATION.md` exactly as a
  `major` would.
- **Automatic migrations are flagged by the release workflow** (it diffs `db/changelog/`), so the
  changeset does not mention them — keep it user-facing. Touching `db/changelog/` without touching
  `.changeset/` is always wrong.

## Command caveats

Each of these fails *quietly* — the command reports success and leaves you with a stale or wrong result.

- **`generate:api:application-server:specs` exits 0 when the app never started.** It boots the server
  to scrape springdoc, so a busy HTTP, management **or JMX** port means no spec is written and the exit
  code is still 0 — you commit a spec missing your new endpoint. Pass free ports; the full recipe and
  the default port numbers are in `server/AGENTS.md`.
- **That script runs a full Maven `verify`.** On a cold cache the first run downloads the whole Spring
  Boot dependency tree; expect several minutes.
- **`db:draft-changelog` needs Docker on PATH** and a running daemon.
- Everything `mvn`-shaped — the `quick` profile silently skipping every test, `mvn clean` wiping the
  generated GraphQL sources, two Maven runs sharing one `target/`, `server/.env` leaking into test
  JVMs — is in `server/AGENTS.md` § Build traps. Read it before your first server test run.

Prefer improving the structure over ad-hoc shortcuts, and write code that reads like the code around it.
