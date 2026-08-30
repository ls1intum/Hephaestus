# Hephaestus

**⚠️ Do NOT stage, commit, or push unless you have permission to do so.**

Hephaestus is an open-source AI mentor for software teams. It reads the work developers already do
in GitHub, GitLab, Slack and Outline against the engineering practices their project cares about — a
curated set of practices ships with it — and delivers practice feedback on the work itself, on the developer's
own practice pages, or in conversation.

`docs/contributor/practice-feedback-language.md` is the normative vocabulary and it binds this file
too: the unit a review records is an **observation**, the unit a developer receives is **feedback**,
and neither the application nor a review is an *agent*.

- `server/` — Spring Boot 4 + Java 21 + Spring Modulith 2. Liquibase-managed PostgreSQL, SQL-layer
  multi-tenancy (`core/tenancy/`), generated `openapi.yaml`. Three runtime roles (`server`, `worker`,
  `webhook`) selected by `hephaestus.runtime.*` — ADR 0005 and ADR 0008. See `server/AGENTS.md`.
- `webapp/` — React 19 SPA, TanStack Router/Query, Tailwind 4, generated API client in `src/api/**`.
  See `webapp/AGENTS.md`.
- `docs/` — contributor docs published to GitHub Pages, including the generated ERD.

The repository uses Node.js for repository scripts and application tooling, and Bun for package
management; `package.json#devEngines.runtime` and `packageManager` are the authoritative versions.
`webapp` is the main TypeScript package; `docs` is a second. The runner
(`server/application/src/main/resources/agent/`), the precompute runner and lib (`docker/agents/precompute/`) and
the per-practice precompute scripts (`server/application/src/main/resources/practices/precompute/`) are
type-checked as one project via `tsconfig.agents.json` and
linted by the root `.oxlintrc.json` and formatted by the root `.oxfmtrc.json`. JDK 21, and Docker
for the database helpers.

## Skills

Load these rather than reasoning from scratch. Each lives in `.claude/skills/<name>/`, which Claude
Code and opencode both read. Codex reads `.agents/skills/` and nothing else, so the four that drive a
contribution are mirrored there byte for byte; `check:instructions` fails if a half goes missing or
drifts. Copy a skill nowhere else.

| Skill | When |
|-------|------|
| `/storybook-components` | Component props, stories, play functions, a11y posture; grading a webapp diff |
| `/composition-patterns` | Compound components, render props, React 19 API shape |
| `/web-design-guidelines` | UI accessibility and UX review |
| `/react-best-practices` | Frontend performance — a vendored Vercel pack; read its applicability table first, since much of it is Next.js-only |
| `/fix-ci`, `/land-pr`, `/resolve-review` | CI triage, opening a PR, answering review comments — mirrored for Codex |
| `/gh-stack` | Creating and maintaining stacked pull requests — mirrored for Codex |

## Quality gates

Finish every change set with `bun run format` then `bun run check`, so styling and type checks
reflect the final state. Document any skipped gate in the PR description.

Use `bun run check:affected` for in-session feedback; it is not the pre-push gate. Its contract is in
`docs/contributor/local-verification.mdx`.

| Command | Does |
|---|---|
| `bun run format` / `format:check` | Apply / verify formatting (Java + TypeScript) |
| `bun run check` | Static analysis, formatting checks, agent tests, and repository policy checks — every leg is listed in the root `package.json` |
| `bun run verify` | Complete local CI mirror for checks that need no live service, image build, or hosted credential |
| `bun run test:webapp` | Vitest |
| `bun run test:agents` | Agent runtime and precompute specs, on Bun |
| `bun run test:server:unit` | Server unit tests — see `server/AGENTS.md` for all four tiers |

Naming: `format` applies, `format:check` verifies read-only for CI, `lint` lints, `check` is the
comprehensive local quality gate. A `:webapp`, `:server` or `:agents` suffix scopes any of them; `:java`
scopes `format` and `lint` only — the Java leg of `check` is `check:server`.

`bun run check` is the complete local quality gate; `bun run verify` adds locally runnable builds and
broader tests. CI distributes these checks across required jobs,
using path filters where appropriate, and adds builds, generated-file checks, broader tests, image
checks, and security scans. Run `check` before pushing and `verify` before requesting review.

### Lint and format scopes

**Oxlint owns linting; oxfmt owns the formatting scopes below and sorts imports.**

| Tree | oxlint config | Formatted by | Scripts |
|---|---|---|---|
| the SPA (`webapp/`) | `webapp/.oxlintrc.json` | oxfmt (`.oxfmtrc.json`) | `format:webapp`, `lint:webapp`, `check:webapp` |
| the docs site (`docs/`) | `docs/.oxlintrc.json` | oxfmt for JavaScript, TypeScript, JSON/JSONC, and CSS (`.oxfmtrc.json`) | `format:docs`; linted by `lint:agents` |
| the agent runtime and specs, both precompute trees, and `scripts/**` | `.oxlintrc.json` | oxfmt (`.oxfmtrc.json`) | `format:agents`, `lint:agents`, `check:agents` |
| selected repository tooling configuration | `.oxlintrc.json` where applicable | oxfmt (`.oxfmtrc.json`) | `format:config` |

`docs:lint` is **not** the oxlint leg — it is the docs package's own `typecheck` plus
`markdownlint-cli2`, configured by `docs/.markdownlint-cli2.jsonc`, which states both the file scope
and the rules. It is the last leg of `bun run check` and runs on the Tooling and Docs CI leg, which
`docs/**` already triggers.

**Start every oxlint run from the repo root.** A nested config *replaces* the root's rules for the
files under it rather than merging, so each tree states its rule set in full — but `options`,
including `typeAware` and `reportUnusedDisableDirectives`, is honoured only from the config oxlint
discovers as the *root*. Start it inside `webapp/` and every type-aware rule reads as enabled and
checks nothing.

Type-aware rules need a project, and oxlint finds one by looking for a file named exactly
`tsconfig.json`. The root stub exists only so the Bun trees — configured by `tsconfig.agents.json` —
have one; without it every ambient global resolves to an error type there.

Each config carries the reasoning for its own deltas; read it before switching a rule either way. The
house rules are oxlint JS plugins under `webapp/tools/oxlint/`, registered by its `index.ts`. They
live under `webapp/` but all three configs load that one plugin and each chooses which to turn on, so
adding a rule there enables it nowhere. `webapp/AGENTS.md` § Linting has the rest.

## TypeScript, in every tree

Holds wherever TypeScript is written here, the Bun agent trees and `scripts/**` included.
`webapp/AGENTS.md` wins over it inside the SPA.

Prefer typed Node.js/TypeScript for repository automation, validation, and tests. Keep shell only at a
real runtime boundary where Node.js is unavailable, such as an end-user bootstrap that must run before
the application toolchain is installed; keep that boundary POSIX-compatible and move its substantive
test orchestration into TypeScript.

- Separate import groups with blank lines wherever their relative evaluation order must not change; oxfmt sorts within each group.
- **A leading `_` marks what the language or a tool reads that way** — an intentionally unused
  binding, a server field name (`_id`), a runtime global. It never marks something private, which
  carries no prefix.
- **A cast is usually the linter telling you the type is wrong upstream.** `no-explicit-any`,
  `no-non-null-assertion` and the `no-unsafe-*` family are errors; reach for `satisfies` instead.
- **Validate anything crossing a trust boundary** — a webhook body, a hand-parsed stream, a
  `JSON.parse` — with a discriminated union, or with a `zod` schema in the SPA, which is the only tree
  that has zod. Generated API types are already checked by `tsc` and need no second guard.
- **Never log a token, a secret or a raw request body.**

## Generated artefacts — never hand-edit, regenerate

| Artefact | Command |
|---|---|
| `server/openapi.yaml` | `bun run generate:api:application-server:specs` |
| `webapp/src/api/**` | `bun run generate:api:application-server:client` |
| `docs/contributor/erd/schema.mmd` | `bun run db:generate-erd-docs` |
| `webapp/src/routeTree.gen.ts` | TanStack Router Vite plugin |
| `server/generated-clients/target/generated-sources/**` | GraphQL and Outline codegen, owned by the generated-clients Maven module |

Regeneration is destructive: it empties the target directory, so do not edit or commit Maven-generated
sources. Commit the version-controlled OpenAPI specification and webapp client alongside the API
change that produced them.

## Database changes (Liquibase)

Changelogs live in `server/application/src/main/resources/db/changelog/` and are included from `master.xml`.
Draft with `bun run db:draft-changelog` (needs Docker; CI sets `CI=true`), then **prune the diff to
the real deltas** — never commit the raw diff. Then `bun run db:generate-erd-docs`.

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

`CONTRIBUTING.md` § Stacked Pull Requests has the process; `/gh-stack` drives `gh stack` rather than
retargeting branches by hand, and descendants get restacked after a lower layer changes. The one
constraint worth stating twice: **a layer that cannot satisfy the normal PR, release and quality
requirements on its own is not a layer.**

### Changesets (release notes)

`.changeset/*.md` files that become `CHANGELOG.md` and drive the version bump. **Not** the same thing
as a Liquibase `<changeSet>` — a schema change needs both. Full flow:
`docs/contributor/release-management.mdx`; the file's shape is in `.changeset/README.md`.

- **Every PR that changes shipped code** (`server/`, `webapp/`, `docker/`, excluding tests and in-tree
  docs) ships one, and `verify-changesets` fails the PR otherwise. `bun changeset` is interactive;
  with no TTY, hand-writing `.changeset/<slug>.md` is the one sanctioned hand-write — never hand-edit
  `CHANGELOG.md`. If the change is invisible to operators and users, `bun changeset --empty` and say
  why in the body.
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

- **`generate:api:application-server:specs` needs three free ports.** It boots the server to scrape
  springdoc, so a busy HTTP, management **or JMX** port fails the run. It fails loudly and restores
  the previous spec rather than committing an empty one — `scripts/generate-openapi-spec.ts` — so the
  cost is a wasted Maven cycle, not a wrong spec. The recipe and the default port numbers are in
  `server/AGENTS.md` § OpenAPI generation ports.
- **That script runs a full Maven `verify`.** On a cold cache the first run downloads the whole Spring
  Boot dependency tree; expect several minutes.
- **`db:draft-changelog` needs Docker on PATH** and a running daemon.
- Maven test selection, concurrent builds, and `server/.env` leaking into test JVMs are covered in
  `server/AGENTS.md` § Build traps. Read it before your first server test run.
