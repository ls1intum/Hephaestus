# Hephaestus

**⚠️ Do NOT stage, commit, or push unless you have permission to do so.**

Hephaestus is an open-source AI mentor for software teams. It reads the work developers already do
in GitHub, GitLab, Slack and Outline against the engineering practices their project cares about — a
curated set of practices ships with it — and delivers practice feedback on the work itself, on the developer's
own practice pages, or in conversation.

`docs/contributor/practice-feedback-language.md` is the normative vocabulary and it binds this file
too: the unit a review records is an **observation**, the unit a developer receives is **feedback**,
and neither the application nor a review is an *agent*.

Parallel contributions follow the hot-file lanes, generated-artifact, main-breakage, and merge-queue
protocol in `docs/contributor/ai-agent-workflow.mdx` § Parallel delivery protocol.

- `server/` — Spring Boot 4 + Java 21 + Spring Modulith 2. Liquibase-managed PostgreSQL, SQL-layer
  multi-tenancy (`core/tenancy/`), generated `openapi.yaml`. Three runtime roles (`server`, `worker`,
  `webhook`) selected by `hephaestus.runtime.*` — ADR 0005 and ADR 0008. See `server/AGENTS.md`.
- `webapp/` — React 19 SPA, TanStack Router/Query, Tailwind 4, generated API client in `src/api/**`.
  See `webapp/AGENTS.md`.
- `docs/` — contributor docs published to GitHub Pages, including the generated ERD.

The repository uses Node.js for repository scripts and application tooling, and pnpm for package
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

Finish every change set with `pnpm run format` then `pnpm run check`, so styling and type checks
reflect the final state. Document any skipped gate in the PR description.

Use `pnpm run check:affected` for in-session feedback; it is not the pre-push gate. Its contract is in
`docs/contributor/local-verification.mdx`.

| Command | Does |
|---|---|
| `pnpm run format` / `format:check` | Apply / verify formatting (Java + TypeScript) |
| `pnpm run check` | Static analysis, formatting checks, agent tests, and repository policy checks — every task is listed in the `quality` array in `vite.config.ts` |
| `pnpm run verify` | Complete local CI mirror for checks that need no live service, image build, or hosted credential |
| `pnpm run test:webapp` | Vitest |
| `pnpm run test:agents` | Agent runtime and precompute specs, on Node |
| `pnpm run test:server:unit` | Server unit tests — the other tiers are in `server/AGENTS.md` § Test tiers |

Naming: `format` applies, `format:check` verifies read-only for CI, `lint` lints, `check` is the
comprehensive local quality gate. A `:webapp`, `:server` or `:agents` suffix scopes any of them; `:java`
scopes `format` and `lint` only — the Java leg of `check` is `check:server`.

`pnpm run check` is the complete local quality gate; `pnpm run verify` adds locally runnable builds and
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
and the rules. It is the last leg of `pnpm run check` and runs on the Tooling and Docs CI leg, which
`docs/**` already triggers.

**Start every oxlint run from the repo root.** A nested config *replaces* the root's rules for the
files under it rather than merging, so each tree states its rule set in full — but `options`,
including `typeAware` and `reportUnusedDisableDirectives`, is honoured only from the config oxlint
discovers as the *root*. Start it inside `webapp/` and every type-aware rule reads as enabled and
checks nothing.

Type-aware rules need a project, and oxlint finds one by looking for a file named exactly
`tsconfig.json`. The root stub exists only so the Node trees — configured by `tsconfig.agents.json` —
have one; without it every ambient global resolves to an error type there.

Each config carries the reasoning for its own deltas; read it before switching a rule either way. The
house rules are oxlint JS plugins under `webapp/tools/oxlint/`, registered by its `index.ts`. They
live under `webapp/` but all three configs load that one plugin and each chooses which to turn on, so
adding a rule there enables it nowhere. `webapp/AGENTS.md` § Linting has the rest.

## TypeScript, in every tree

Holds wherever TypeScript is written here, the Node agent trees and `scripts/**` included.
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
| `server/openapi.yaml` | `pnpm run generate:api:application-server:specs` |
| `webapp/src/api/**` | `pnpm run generate:api:application-server:client` |
| `docs/contributor/erd/schema.mmd` | `pnpm run db:generate-erd-docs` |
| `webapp/src/routeTree.gen.ts` | TanStack Router Vite plugin |
| `server/generated-clients/target/generated-sources/**` | GraphQL and Outline codegen, owned by the generated-clients Maven module |

`generate:api:application-server:client` empties `webapp/src/api/` before it writes. Maven-generated
sources live under `target/` and are never committed. Commit `server/openapi.yaml` and
`webapp/src/api/**` with the API change that produced them.

## Database changes (Liquibase)

Procedure: `docs/contributor/database-migration.mdx`. Entity conventions the drift gate reads:
`server/AGENTS.md` § Schema changes. Two rules CI enforces:

- `pnpm run db:draft-changelog` writes the drift into this branch's single changelog and wires it
  into `master.xml`; a branch never hand-writes one or adds a second.
- A file under `db/changelog/` that reached `main` is never edited, renamed or deleted, and
  `master.xml` is append-only. Fix mistakes forward.

## Pull requests

Title follows Conventional Commits (types and scopes in `CONTRIBUTING.md`); the PR template walks the
rest.

### Stacked pull requests

`CONTRIBUTING.md` § Stacked Pull Requests has the process; `/gh-stack` drives `gh stack` rather than
retargeting branches by hand, and descendants get restacked after a lower layer changes. The one
constraint worth stating twice: **a layer that cannot satisfy the normal PR, release and quality
requirements on its own is not a layer.**

### Changesets (release notes)

`.changeset/README.md` is the contract. Every PR that changes shipped code ships one — with no TTY,
hand-write `.changeset/<slug>.md` in the shape shown there. `verify-changesets` fails the PR
without one and rejects `major` before 1.0. The summary is a release note in the operator's or
user's voice, never a code description, and never carries an agent-attribution trailer.

## Command caveats

Each of these fails *quietly* — the command reports success and leaves you with a stale or wrong result.

- **`generate:api:application-server:specs` honours `HEPHAESTUS_APPLICATION_JAR`.** With it set, the
  spec is scraped from that JAR, not from your checkout, and the run still reports success; unset it
  after a CI-style run. Without it the script packages the reactor with tests skipped and boots the
  JAR under the `specs` profile on a free port.
- Maven test selection, concurrent builds, and `server/.env` leaking into test JVMs are covered in
  `server/AGENTS.md` § Build traps. Read it before your first server test run.
