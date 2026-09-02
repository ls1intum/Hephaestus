# Hephaestus

Hephaestus is an open-source AI mentor for software teams. It reads the work developers already do
in GitHub, GitLab, Slack and Outline against the engineering practices their project cares about — a
curated set of practices ships with it — and delivers practice feedback on the work itself, on the
developer's own practice pages, or in conversation.

The rest of this file is what a capable engineer could not infer from the tree: what we value, what
must stay true, where a wrong move is expensive, and where the longer guides live. Treat it as good
defaults; a maintainer's explicit instruction overrides any of it.

## Vocabulary

`docs/contributor/practice-feedback-language.md` is the normative vocabulary and it binds this file
too: the unit a review records is an **observation**, the unit a developer receives is **feedback**,
and neither the application nor a review is an *agent*. `docs/contributor/practice-review-glossary.mdx`
owns the review operation's terms.

## What we value

- The simplest maintainable change. No abstraction, configuration or future-proofing the task in
  front of you does not need; no compatibility shim for a caller that does not exist.
- A framework or library feature over a hand-rolled one, every time it fits.
- Root causes over workarounds. A comment that documents a workaround is a flag that the fix is
  not done.
- Comments and documents say what the code cannot: a constraint, a platform behaviour, a rejected
  alternative. They stay true after the next refactor; nothing in them is a run number, a measured
  duration or an incident.
- One home per fact. A rule stated in two places is a conflict waiting to happen; the second copy
  is a pointer.
- If a rule here fights the task, say so and get a maintainer's decision rather than working around
  it silently.

## What must stay true

Each of these has a gate; the gate is the contract, this list is the map.

- **Tenancy is enforced in SQL.** Workspace scoping lives in
  `server/application/src/main/java/de/tum/cit/aet/hephaestus/core/tenancy/` and
  `DataIsolationArchitectureTest` asserts it, not a remembered `WHERE` clause.
- **The artifact tested is the artifact shipped.** CI packages the server once; the OpenAPI spec,
  the browser suite and the container image all come from that JAR — `docs/contributor/ci-cd.mdx`
  § Build once.
- **A released migration never changes.** `db/changelog/` files that reached `main` are immutable
  and `master.xml` is append-only — `docs/contributor/database-migration.mdx`.
- **Every release carries evidence.** Signed images, provenance, SBOMs and a vulnerability policy
  evaluated at build and release time — `docs/contributor/release-management.mdx` and
  `docs/contributor/vulnerability-remediation.mdx`.
- **Generated artefacts are regenerated, never edited** — the table below.

## Where a wrong move is expensive

- **Killing by pattern.** Never `pkill -f`, `pgrep | kill` or `kill` a PID found by matching a name
  or path: this host runs several worktrees, and your own process carries this worktree's path. Kill
  only a PID you captured at spawn.
- **Shared host ports.** `server/.env` pins `POSTGRES_PORT` and `MANAGEMENT_PORT` per worktree and
  another worktree's container may hold them. Override the variable for the run
  (`POSTGRES_PORT=<free> pnpm run db:check-drift`, `MANAGEMENT_PORT=0 SERVER_PORT=0` for Maven
  tests); never stop a container you did not start.
- **The local database is a bind mount.** The PostgreSQL data folder under `server/` survives
  `docker compose down -v`. `pnpm run dev:reset` deletes it; `db:draft-changelog` and
  `db:check-drift` move it aside and restore it, so never run two database helpers at once.
- **One Maven process per checkout.** Two write the same `target/` directories.
- **Regeneration is destructive.** `generate:api:application-server:client` empties `webapp/src/api/`
  first; stash local edits there.

## How it works

One JAR boots in three runtime roles — `server`, `worker`, `webhook` — selected by
`hephaestus.runtime.*` (ADR 0005, ADR 0008; `docs/admin/runtime-roles.mdx`). The webhook role
receives SCM events and publishes them to NATS JetStream; the server consumes them, syncs the
work, runs practice reviews in sandboxed agent containers, and delivers the resulting feedback.
The SPA talks to the server only through the generated client. `docs/contributor/system-design.mdx`
and `docs/contributor/practice-review-pipeline.mdx` have the diagrams and the stages.

## Where code lives

- `server/` — Spring Boot 4, Java 21, Spring Modulith 2; Liquibase-managed PostgreSQL; generated
  `openapi.yaml`. `server/AGENTS.md` has the build traps and entity conventions.
- `webapp/` — React 19 SPA, TanStack Router/Query, Tailwind 4, generated API client in `src/api/**`.
  `webapp/AGENTS.md` has the component and story conventions.
- `docs/` — contributor and admin docs published to GitHub Pages, including the generated ERD.
- `scripts/` — repository tooling in TypeScript, on the Node and pnpm versions pinned by
  `package.json#devEngines.runtime` and `packageManager`. The agent runner
  (`server/application/src/main/resources/agent/`), the precompute runner and lib
  (`docker/agents/precompute/`) and the per-practice precompute scripts
  (`server/application/src/main/resources/practices/precompute/`) are type-checked as one project
  via `tsconfig.agents.json`.

Skills live in `.claude/skills/<name>/`, read by Claude Code and opencode; Codex reads
`.agents/skills/` and nothing else, so the four that drive a contribution are mirrored there byte
for byte and `check:instructions` fails when a half drifts. Copy a skill nowhere else.

| Skill | When |
|-------|------|
| `/storybook-components` | Component props, stories, play functions, a11y posture; grading a webapp diff |
| `/composition-patterns` | Compound components, render props, React 19 API shape |
| `/web-design-guidelines` | UI accessibility and UX review |
| `/react-best-practices` | Frontend performance — a vendored Vercel pack; read its applicability table first, since much of it is Next.js-only |
| `/fix-ci`, `/land-pr`, `/resolve-review` | CI triage, opening a PR, answering review comments — mirrored for Codex |
| `/gh-stack` | Creating and maintaining stacked pull requests — mirrored for Codex |

## Verifying

Smallest proof first: the test file you touched, the scoped check for the tree you changed
(`pnpm run check:affected` selects it from your diff). Before pushing, `pnpm run format` then
`pnpm run check`; the pre-push hook runs `check` again. `pnpm run verify` adds the builds and test
suites that need no credential; CI owns images, browser and live-service suites. The tiers and
their exact scopes are in `docs/contributor/local-verification.mdx`.

| Command | Does |
|---|---|
| `pnpm run format` / `format:check` | Apply / verify formatting (Java + TypeScript) |
| `pnpm run check` | Every task in the `quality` array in `vite.config.ts`: static analysis, formatting, agent tests, repository policy |
| `pnpm run verify` | `check` plus the credential-free builds and test suites |
| `pnpm run test:webapp` | Vitest |
| `pnpm run test:agents` | Agent runtime and precompute specs, on Node |
| `pnpm run test:server:unit` | Server unit tests — the other tiers are in `server/AGENTS.md` § Test tiers |

Naming: `format` applies, `format:check` verifies read-only for CI, `lint` lints, `check` is the
quality gate. A `:webapp`, `:server` or `:agents` suffix scopes any of them; `:java` scopes `format`
and `lint` only — the Java leg of `check` is `check:server`.

### Lint and format

Oxlint lints; oxfmt formats and sorts imports. Each tree states its rule set in full —
`webapp/.oxlintrc.json`, `docs/.oxlintrc.json`, and the root `.oxlintrc.json` for the agent trees,
`scripts/**` and tooling config — and each config carries the reasoning for its own deltas.
`docs:lint` is the docs package's `typecheck` plus `markdownlint-cli2` (`docs/.markdownlint-cli2.jsonc`).

- **Start every oxlint run from the repo root.** A nested config *replaces* the root's rules rather
  than merging, and `options` — `typeAware`, `reportUnusedDisableDirectives` — is honoured only from
  the config oxlint discovers as the root. Started inside `webapp/`, every type-aware rule reads as
  enabled and checks nothing.
- **Type-aware rules need a file named exactly `tsconfig.json`.** The root stub exists so the Node
  trees, configured by `tsconfig.agents.json`, have one.
- **The house rules are one oxlint plugin under `webapp/tools/oxlint/`.** All three configs load it
  and each chooses which rules to turn on, so adding a rule there enables it nowhere.
  `webapp/AGENTS.md` § Linting has the rest.

## TypeScript, in every tree

Holds wherever TypeScript is written here, `scripts/**` and the agent trees included;
`webapp/AGENTS.md` wins inside the SPA.

- Repository automation, validation and tests are typed TypeScript. Shell stays only at a real
  runtime boundary where Node is unavailable, kept POSIX-compatible, with its test orchestration in
  TypeScript.
- Separate import groups with blank lines where their evaluation order matters; oxfmt sorts within a
  group.
- A leading `_` marks what the language or a tool reads that way — an unused binding, a server
  field name, a runtime global — never something private.
- A cast is usually the linter telling you the type is wrong upstream. `no-explicit-any`,
  `no-non-null-assertion` and the `no-unsafe-*` family are errors; reach for `satisfies`.
- Validate anything crossing a trust boundary — a webhook body, a hand-parsed stream, a
  `JSON.parse` — with a discriminated union, or with a `zod` schema in the SPA, the only tree that
  has zod. Generated API types are already checked by `tsc`.
- Never log a token, a secret or a raw request body.

## Generated artefacts

| Artefact | Command |
|---|---|
| `server/openapi.yaml` | `pnpm run generate:api:application-server:specs` |
| `webapp/src/api/**` | `pnpm run generate:api:application-server:client` |
| `docs/contributor/erd/schema.mmd` | `pnpm run db:generate-erd-docs` |
| `webapp/src/routeTree.gen.ts` | TanStack Router Vite plugin |
| `server/generated-clients/target/generated-sources/**` | GraphQL and Outline codegen, owned by the generated-clients Maven module |

Maven-generated sources live under `target/` and are never committed. Commit `server/openapi.yaml`
and `webapp/src/api/**` with the API change that produced them.

## Database changes

Procedure: `docs/contributor/database-migration.mdx`. Entity conventions the drift gate reads:
`server/AGENTS.md` § Schema changes. `pnpm run db:draft-changelog` writes the drift into this
branch's single changelog and wires it into `master.xml`; a branch never hand-writes one or adds a
second.

## Pull requests

- Do not stage, commit, push or open a pull request unless asked. A branch is the maintainer's to
  land.
- Title follows Conventional Commits with the types and scopes in `CONTRIBUTING.md`; `pull-request.yml`
  validates it. One concern per PR; a description that says "also" is two PRs.
- The PR template is the body. State the problem, the change, and how it was verified; upload
  evidence to GitHub rather than committing it.
- A PR that changes shipped code ships a changeset — `.changeset/README.md` is the contract. With no
  TTY, hand-write `.changeset/<slug>.md` in the shape shown there; `verify-changesets` fails the PR
  without one and rejects `major` before 1.0. The summary is a release note in the operator's or
  user's voice, never a code description, and never carries an agent-attribution trailer.
- Stacked PRs follow `CONTRIBUTING.md` § Stacked Pull Requests through `/gh-stack`. A layer that
  cannot satisfy the normal PR, release and quality requirements on its own is not a layer.
- When babysitting review bots: verify each finding against the source, fix the real ones, dismiss
  the rest with a written reason, and stop when the bots are green on the latest commit.

## Plans and work artifacts

The gitignored scratch directories for plans, research and measurements (the `.gitignore` names
them) are never committed. Durable facts go into the document that owns them under `docs/`, and a merged PR is
the implementation record — do not keep a second checklist in the repository.

## Command caveats

Each of these reports success and leaves a stale or wrong result.

- **`generate:api:application-server:specs` honours `HEPHAESTUS_APPLICATION_JAR`.** With it set, the
  spec is scraped from that JAR, not from your checkout. Unset it after a CI-style run. Without it
  the script packages the reactor with tests skipped and boots the JAR under the `specs` profile on
  a free port.
- **`surefire:test` as a bare goal runs whatever `target/test-classes` holds.** After editing a
  test, run a lifecycle phase (`test-compile`) first, or use the `pnpm run test:server:*` scripts,
  which do.
- Maven test selection and `server/.env` leaking into test JVMs are in `server/AGENTS.md`
  § Build traps.
