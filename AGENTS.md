# Hephaestus Agent Handbook

**⚠️ Do NOT stage, commit, or push unless you have permission to do so.**

This file governs the entire repository. Each service has its own `AGENTS.md` with service-specific patterns:

- `webapp/AGENTS.md` — React, TanStack, Tailwind patterns
- `server/AGENTS.md` — Spring Boot, JPA, testing (includes the `integration.core.webhook` receiver)

## 1. Architecture map

- `server/`: Spring Boot 4 + Java 21 + Spring Modulith 2, Liquibase-managed PostgreSQL schema, synchronous + reactive APIs, generated OpenAPI spec in `openapi.yaml`. SQL-layer multi-tenancy via `core/tenancy/`. Three runtime roles (`server`, `worker`, `webhook`) selected by `hephaestus.runtime.*` properties — see ADR 0005 (baseline) and ADR 0008 (webhook). The `webhook-server` production container runs the same image as `application-server` with `SPRING_PROFILES_ACTIVE=prod,webhook` so webhook reception survives an app-server restart.
- `webapp/`: React 19 + TanStack Router/Query, Tailwind 4 UI kit (`src/components/ui`), generated API client in `src/api/**`.
- `docs/`: Contributor docs (including the ERD that `db:generate-erd-docs` regenerates).

## 2. Toolchain & environment prerequisites

- **Node.js**: Use the exact version from `.node-version` (currently 24.15.0). The repo uses pnpm 11 with `pnpm-lock.yaml` and pnpm workspaces (`pnpm-workspace.yaml`). The webapp is the only TypeScript package.
- **Java**: JDK 21 (see `pom.xml`). Run builds with `mvn` from `server/`.
- **Docker & Docker Compose**: Required for database helper scripts (`scripts/db-utils.sh`) and for spinning up Postgres/NATS locally.
- **Databases**: Default PostgreSQL DSN is `postgresql://root:root@localhost:5432/hephaestus`. The database helpers spin this up for you via Docker.
## 3. Quality gates & routine commands

Run the relevant commands locally before opening a PR:

### Aggregate commands (all services)

| Concern           | Command                | Description                                    |
| ----------------- | ---------------------- | ---------------------------------------------- |
| Format everything | `pnpm run format`       | Apply formatting to Java + TypeScript + webapp |
| Check formatting  | `pnpm run format:check` | Verify formatting without changes (CI)         |
| Lint everything   | `pnpm run lint`         | Lint all server + client packages              |
| Full check        | `pnpm run check`        | Comprehensive: format + lint + typecheck       |

### Per-service commands

| Service                  | Format                                | Format Check                                | Lint                                | Check                                |
| ------------------------ | ------------------------------------- | ------------------------------------------- | ----------------------------------- | ------------------------------------ |
| **Webapp**               | `pnpm run format:webapp`               | `pnpm run format:webapp:check`               | `pnpm run lint:webapp`               | `pnpm run check:webapp`               |
| **Java**                 | `pnpm run format:java`                 | `pnpm run format:java:check`                 | —                                   | —                                    |

### Additional commands

| Concern                  | Command                                                                                                                                                                                                                                                                                                                                                                                                               |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Webapp build             | `pnpm run build:webapp`                                                                                                                                                                                                                                                                                                                                                                                                |
| Webapp tests             | `pnpm run test:webapp`                                                                                                                                                                                                                                                                                                                                                                                                 |
| Webapp typecheck         | `pnpm run typecheck:webapp`                                                                                                                                                                                                                                                                                                                                                                                            |
| Webapp Storybook         | `pnpm --filter webapp run build-storybook`                                                                                                                                                                                                                                                                                                                                                                                   |
| Server tests | **Four test tiers:** <br>• `mvn test` runs unit tests (`@Tag("unit")`) <br>• `mvn test -Parchitecture-tests` runs ArchUnit + Modulith verification (`@Tag("architecture")`) <br>• `mvn verify` runs unit + integration tests (`@Tag("integration")`) <br>• `mvn test -Plive-tests` runs live GitHub API tests (`@Tag("live")`) <br><br>Live tests require GitHub App credentials configured in `application-live-local.yml` (gitignored). The Maven profile is the single guard—tests only run when explicitly activated. |

**Script naming conventions:**

- `format` = apply formatting fixes
- `format:check` = verify formatting (read-only, for CI)
- `lint` = run linting checks
- `lint:fix` = apply lint fixes
- `check` = comprehensive verification (format + lint + typecheck)
- `check:fix` = apply all fixes
- `ci` = CI-optimized output

Document any skipped gate in the PR description with a rationale. Always finish a change set by running `pnpm run format` followed by `pnpm run check` so both styling and type checks reflect the final state.

## 4. Code generation & forbidden edits

We rely heavily on generated artifacts. Never hand-edit these directories—regenerate instead:

| Artifact                                                                                                                            | Source command                                                                                                    |
| ----------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `server/openapi.yaml`                                                                                            | `pnpm run generate:api:application-server:specs` (runs `mvn verify -DskipTests=true -Dapp.profiles=specs`).        |
| `webapp/src/api/**/*`, `webapp/src/api/@tanstack/react-query.gen.ts`, `webapp/src/api/client.gen.ts`, `webapp/src/api/types.gen.ts` | `pnpm run generate:api:application-server:client` (wraps `pnpm --filter webapp run openapi-ts`).                          |
| `docs/contributor/erd/schema.mmd`                                                                                                   | `pnpm run db:generate-erd-docs` (connects to the same Postgres instance).                                          |

Regeneration is destructive; stash local edits before running these commands. Check diffs carefully—generated clients must be committed alongside API changes.

## 5. Database workflow (Liquibase)

- Liquibase changelog files live under `server/src/main/resources/db/changelog/` and are included via `master.xml`.
- Use `pnpm run db:draft-changelog` after changing JPA entities. The script will:
  1. Spin up PostgreSQL through Docker (ensure Docker is running or set `CI=true` with a ready Postgres).
  2. Snapshot the schema, run Liquibase diff, and create a timestamped changelog file.
  3. Tear down the temporary container.
- Trim the generated changelog to only the real schema deltas (e.g., new columns). Never commit the raw diff wholesale—prune back to the minimal change set before renaming it into `db/changelog/`.
- **Filename convention (required):** the committed file MUST be `<epoch-ms-timestamp>_changelog.xml` — a real millisecond timestamp (`date +%s%3N`, i.e. what the draft tool emits), strictly greater than the latest existing changelog. Do NOT invent a round number and do NOT use a descriptive suffix; keep the `_changelog.xml` name. New `<include>` entries are **appended** to `master.xml` — the committed list is append-only (CI-enforced), not globally timestamp-sorted. `changeSet` ids follow `<timestamp>-1`, `<timestamp>-2`, …
- **One consolidated changelog per branch/PR (required):** add every schema change for a branch as additional `<changeSet>` entries inside that single file. Never open a PR with more than one new changelog file — consolidate. Each `changeSet` should be preconditioned (`onFail="MARK_RAN"`) with a `<rollback>`, matching the existing files.
- After drafting a changelog, run `pnpm run db:generate-erd-docs` to keep ERD docs in sync.
- Never manually edit generated Liquibase diff sections unless you fully understand the implications. Prefer creating a follow-up changelog to fix mistakes.
- **Released Liquibase changelogs are immutable (CI-enforced):** once a changelog file reaches `main` it must never be edited, renamed, or deleted, and `master.xml` is append-only — the `Migrations` quality gate fails otherwise. Fix mistakes forward with a new changelog. Destructive schema changes (drop/rename of released tables/columns) follow deprecate-then-remove across two releases — see `docs/contributor/database-migration.mdx`. (This is the DB changelog; the `.changeset/*.md` release notes in §10 are a separate thing.)

## 6. Frontend (webapp) expectations

- Follow the container/presentation split already in place (route files under `src/routes/**` fetch data and pass it to components under `src/components/**`). Keep components pure and side-effect free.
- Fetch data exclusively with TanStack Query v5 and the generated helpers in `@/api/@tanstack/react-query.gen.ts`. Spread the option objects: `useQuery(getTeamsOptions({ ... }))`. Use the generated `*.QueryKey()` helpers for cache invalidation.
- Do not call `fetch` directly; reuse the generated `@hey-api` client configured in `src/api/client.ts` and the shared QueryClient from `src/integrations/tanstack-query/root-provider.tsx`.
- State management lives in the colocated Zustand stores (`src/stores/**`). Derive UI state from TanStack Query results instead of duplicating loading/error flags.
- TypeScript style: explicit prop interfaces, no `React.FC`, favour `type` aliases for composite shapes, prefer discriminated unions or Zod guards when needed, keep imports using the `@/*` alias, avoid deep relative paths. See `webapp/AGENTS.md` for full patterns.
- Styling: Tailwind utility classes + shadcn primitives in `src/components/ui`. Compose class names with `clsx`/`tailwind-merge`; use tokenized colors (`bg-surface`, `text-muted`, etc.).
- Accessibility: follow shadcn patterns, wire up ARIA roles, and manage focus for dialogs/menus. Storybook stories should cover default/variant/edge states. See `webapp/AGENTS.md` for Storybook patterns.
- Tests: extend Vitest coverage when you change behaviour (`pnpm run test:webapp`). Use Testing Library queries that mirror user intent (`getByRole`, `findByText`).
- Routing: declare new routes via `createFileRoute`, respect loader/guard conventions, and keep loader side effects out of render paths.
- Never hand-edit `routeTree.gen.ts`; it is generated by TanStack Router tooling.
- **React Compiler**: The webapp uses React Compiler (via `babel-plugin-react-compiler` in `vite.config.js`) which automatically optimizes components at build time. This means:
  - **Do not use `useMemo`, `useCallback`, or `React.memo` for new code** — the compiler handles memoization automatically.
  - Existing `useMemo`/`useCallback` usages can remain (they don't hurt and changing them can alter compilation output), but avoid adding new ones.
  - Only use manual memoization as an escape hatch when you need precise control over effect dependencies or specific caching behavior.
  - This applies to components in `src/components/**` and routes — vendored UI primitives in `src/components/ui` are exempt since they come from shadcn.

## 7. Application server (Java/Spring) expectations

- Keep business logic in services annotated with `@Service` and transactional boundaries (`@Transactional`) where needed. Controllers should be thin (input validation + delegation).
- Use Lombok consistently (`@Getter`, `@Setter`, etc.) but prefer explicit builders or records when immutability helps.
- Group new tests under the proper JUnit tag so CI picks them up (`@Tag("unit")`, `@Tag("integration")`, or `@Tag("live")`). Follow AAA structure, single assertion focus, deterministic data. See `server/AGENTS.md` for testing patterns.
- Reuse existing DTO converters/mappers instead of duplicating mapping logic. Look at `integration.scm.domain.team` for established patterns.
- Security: new endpoints must enforce permissions using the existing security utilities (`EnsureAdminUser`, etc.).
- Give each new changelog a fresh millisecond-timestamp ID greater than the previous one and append its `<include>` to the end of `master.xml` (append-only; the historical list is not globally sorted). Align entity annotations with the generated change sets.
- Annotate record components in DTOs with `org.jspecify.annotations.NonNull` when the API requires a value; leave optional fields bare so the OpenAPI spec stays minimal without extra schema annotations.
- Prefer resource-oriented workspace endpoints: express lifecycle transitions via HTTP methods (for example `PATCH /workspaces/{workspaceSlug}/status`) instead of RPC-style verbs and return consistent `ProblemDetail` payloads for errors.
- When adding or changing REST endpoints, follow the centralized exception-handling rules in `docs/contributor/api-error-handling.md` so every controller returns RFC-7807 `ProblemDetail` responses via `@RestControllerAdvice`.
- Controller-level integration tests should extend `AbstractWorkspaceIntegrationTest` (or an equivalent domain-specific base), exercise access control through `WebTestClient` + `TestAuthUtils`, and follow the contributor testing guide's checklist. This keeps authentication, validation, and persistence assertions consistent across new endpoints.

## 8. Webhook receiver (Java) expectations

- Lives at `server/src/main/java/de/tum/cit/aet/hephaestus/integration/core/webhook/`. Pure verifier/builder classes (HMAC, GitLab token, subject builders, dedup-id) sit beside Spring-backed controllers, JetStream publisher, and stream bootstrap — all gated together via `RuntimeRole.WEBHOOK_PROPERTY`.
- Production runs the receiver in a dedicated `webhook-server` container (same image as `application-server`, `SPRING_PROFILES_ACTIVE=prod,webhook`). The app-server's deploy cycle therefore does not interrupt webhook reception — push events on GitHub/GitLab are not manually redeliverable.
- NATS subject grammar: `github.<owner>.<repo>.<event>`, `gitlab.<namespace>.<project>.<event>`. Dots in path segments are replaced with `~`; nested GitLab groups join with `~`. The consumer-side prefix builder at `integration.core.consumer.ConsumerSubjectMath#buildSubjectPrefix` must agree — `SubjectGrammarRoundTripTest` enforces this for every committed fixture.
- HMAC / hex / locale safety enforced by ArchUnit: `HexEncodingArchTest` (only `HexFormat.of()`), `LocaleSafetyArchTest` (no naked `toLowerCase`/`toUpperCase`). Coverage gate: `integration.core.webhook` package ≥ 0.95 branch coverage in JaCoCo.
- Configuration: bound to `hephaestus.webhook.*` via `core.webhook.WebhookProperties` (shared with auto-registration in `workspace/GitLabWebhookService`).

## 9. Documentation & assets

- ERD diagrams live under `docs/contributor/erd/`. Regenerate via `pnpm run db:generate-erd-docs` after schema changes.
- Contributor documentation should stay in `docs/` (GitHub Pages). Keep README/CONTRIBUTING updates concise and actionable.
- Screenshots or large binary assets belong under `docs/images/` or the Storybook stories, not inside source directories.

## 10. Pull requests

Before opening a PR, run `pnpm run format && pnpm run check`. The PR template (`.github/PULL_REQUEST_TEMPLATE.md`) guides you through title format and checklists. Key points:

- **Title**: Follow Conventional Commits—see `CONTRIBUTING.md` for types/scopes.
- **Generated files**: Regenerate and commit OpenAPI specs, clients, and ERD docs when APIs or entities change.
- **Database changes**: Run `pnpm run db:draft-changelog`, prune to minimal deltas, and ship a **release changeset** that mentions the migration (see below).
- **Changeset**: Every PR that changes shipped code ships one (see below).

### Changesets (release notes)

These are **release changesets** — `.changeset/*.md` files that become `CHANGELOG.md` and drive the version bump. Do not confuse them with Liquibase `<changeSet>`s (the DB changelog in §5); a schema change needs *both*. Full flow: `docs/contributor/release-management.mdx`.

- **Every PR that changes shipped code (anything under `server/`, `webapp/`, or `docker/` except tests and in-tree docs) ships a changeset.** Run `pnpm changeset` (pick the bump, write the summary). If the change is invisible to operators and users (refactor, tests, CI, docs-only), run `pnpm changeset --empty` and write why in the file body. CI (`verify-changesets`) fails a shipped-code PR that has neither.
- **The summary lands in `CHANGELOG.md` verbatim, in the operator/user's voice.** Lead with what an operator or user can now do, or the symptom a fix removes — no class names, hook names, or file paths. If operators must act, add a line: `**Operators:** set FOO_BAR (optional, default …)`. Never put co-authored-by / agent-attribution trailers in the body; never hand-edit `CHANGELOG.md`.
  - ✗ `Refactor LeaderboardService scoring hooks` → ✓ `Fixes duplicate leaderboard entries after a team rename.`
  - **One changeset per user-visible change**; a PR shipping two unrelated visible changes ships two files. When unsure, add one.
  - **No TTY (agent/CI)?** `pnpm changeset` is interactive; instead write `.changeset/<slug>.md` by hand — frontmatter `"hephaestus": <bump>` and the summary as the body (exact shape in `.changeset/README.md`). This is the one sanctioned hand-write; `CHANGELOG.md` and frontmatter you invent elsewhere are not.
- **The bump is the operator's upgrade cost, not code semantics:**
  - `patch` — upgrade needs no action (bug fix, internal change, additive migration that runs automatically).
  - `minor` — new capability, still zero-action; call out any new *optional* env var / feature flag in the summary.
  - `major` — the operator must act before/at upgrade (required new env var, removed/renamed config, destructive or manual migration, dropped API). State the action and update `MIGRATION.md`.
  - **Pre-1.0 (now): never pick `major`** — it would cut 1.0.0 and `verify-changesets` rejects it. Breaking changes ride in `minor` instead, so a pre-1.0 `minor` is *not* guaranteed zero-action: if the operator must act, say so in the summary (`**Operators:** …`) and update `MIGRATION.md` exactly as a `major` would.
- **Schema migrations are first-class.** If the PR adds a Liquibase changelog under `server/src/main/resources/db/changelog/`, the changeset MUST say so (e.g. `Includes an automatic database migration.`) and be at least `minor`. Touching `db/changelog/` without touching `.changeset/` is always wrong.

## 11. Known command caveats

- `pnpm run db:draft-changelog` requires Docker to be installed and available on PATH. In CI we set `CI=true`; locally ensure Docker Desktop/daemon is running before invoking the script.
- `pnpm run generate:api:application-server:specs` performs a full Maven `verify` against the specs profile. The initial run downloads the entire Spring Boot dependency tree (~hundreds of MB); expect several minutes on a cold cache.

Stay consistent with the existing patterns and prefer improving the structure rather than introducing ad-hoc shortcuts.
