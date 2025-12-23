# Hephaestus Agent Handbook

**⚠️ Do NOT stage, commit, or push unless you have permission to do so.**

This file governs the entire repository. Combine these guardrails with the scoped instructions under `.github/instructions/**` (general coding, TSX, Storybook, Java tests).

## 0. Beads Issue Tracker

Use **beads** (`bd` CLI) for persistent task tracking across sessions. This replaces markdown TODO lists.

> **First-time setup**: Run `bd onboard` to see comprehensive integration instructions. It outputs AGENTS.md and Copilot configuration templates.

### Why Beads?

- **Persistent memory**: Issues survive context window resets
- **Dependency graphs**: Track what blocks what with `bd dep`
- **Ready work detection**: `bd ready` shows unblocked tasks by priority
- **Git-synced**: Issues auto-export to `.beads/issues.jsonl` (commit with code)

### Issue Types & Priorities

| Type | Use For |
|------|---------|
| `bug` | Something broken |
| `feature` | New functionality |
| `task` | Work item (tests, docs, refactoring) |
| `epic` | Large feature with subtasks |
| `chore` | Maintenance |

| Priority | Meaning |
|----------|---------|
| `0` | Critical (security, data loss, broken builds) |
| `1` | High (major features, important bugs) |
| `2` | Medium (default) |
| `3` | Low (polish, optimization) |
| `4` | Backlog |

### Session Start Protocol

At the **start of every session**, run:

```bash
bd ready --json          # What's unblocked and ready to work on?
bd list --status open    # Full context of all open work
bd blocked               # What's waiting on dependencies?
```

This gives you immediate context without re-reading documentation.

### Core Workflow

```bash
# 1. Check ready work
bd ready --json

# 2. Claim a task
bd update <id> --status in_progress

# 3. Do the work...

# 4. Discover new work? Track its origin!
bd create "Found bug during refactor" -t bug -p 1
bd dep add <new-id> <current-id> --type discovered-from

# 5. Complete work
bd close <id> --reason "Fixed in commit abc123"

# 6. Session end check
bd list --status open
```

### The `discovered-from` Pattern

When you find new work while working on something else, **always link it**:

```bash
# Working on heph-abc, discover a race condition
bd create "Race condition in SlackService" -t bug -p 1
# Returns: heph-xyz

# Link it to what you were working on
bd dep add heph-xyz heph-abc --type discovered-from
```

**Why?** This creates an audit trail of how work was discovered, enabling:

- Forensics: "Why did we create this issue?"
- Context: "What was the agent doing when it found this?"
- Priority: Discovered bugs often relate to active work

### Session End Protocol

Before ending any session:

1. **File issues** for any discovered/remaining work
2. **Close** completed issues with `--reason`
3. **Verify** with `bd list --status open`
4. **Commit** `.beads/issues.jsonl` with your code changes

> **Note**: Beads auto-exports to JSONL after mutations (5s debounce). Just commit the file with your changes—no manual export needed.

### Essential Commands

```bash
# Session management
bd ready              # Unblocked issues, sorted by priority
bd blocked            # Issues waiting on dependencies
bd list --status open # All open issues
bd stale --days 7     # Forgotten issues needing attention

# Issue details
bd show <id>          # Full issue details
bd dep tree <id>      # Visualize dependency graph
bd search "auth"      # Full-text search

# Housekeeping
bd doctor             # Check beads health
bd sync               # Force sync with JSONL
```

### Rules

- ✅ Use `bd` for ALL task tracking
- ✅ Always use `--json` for programmatic parsing
- ✅ Link discovered work with `discovered-from` dependencies
- ✅ Commit `.beads/issues.jsonl` with code changes
- ❌ Do NOT use markdown TODO lists
- ❌ Do NOT create planning docs in repo root (use `history/` if needed)

## 1. Architecture map

- `server/application-server/`: Spring Boot 3.5, Liquibase-managed PostgreSQL schema, synchronous + reactive APIs, generated OpenAPI spec in `openapi.yaml`.
- `webapp/`: React 19 + TanStack Router/Query, Tailwind 4 UI kit (`src/components/ui`), generated API client in `src/api/**`.
- `server/intelligence-service/`: TypeScript/Hono service orchestrating AI models via Vercel AI SDK. OpenAPI spec is exported and mirrored into the Java client under the application server.
- `server/webhook-ingest/`: Hono/TypeScript webhook intake that forwards events into NATS JetStream.
- `docs/`: Contributor docs (including the ERD that `db:generate-erd-docs` regenerates).

## 2. Toolchain & environment prerequisites

- **Node.js**: Use the exact version from `.node-version` (currently 22.10.0). Stick with npm—the repo maintains `package-lock.json` and uses npm workspaces. The intelligence-service and webhook-ingest are TypeScript services that use npm.
- **Docker & Docker Compose**: Required for database helper scripts (`scripts/db-utils.sh`) and for spinning up Postgres/Keycloak/NATS locally.
- **Databases**: Default PostgreSQL DSN is `postgresql://root:root@localhost:5432/hephaestus`. The database helpers spin this up for you via Docker.
- **Environment variables**: When generating intelligence service OpenAPI specs locally, set `MODEL_NAME=fake:model` and `DETECTION_MODEL_NAME=fake:model` (the service settings expect a provider-qualified model name).

## 3. Quality gates & routine commands

Run the relevant commands locally before opening a PR:

### Aggregate commands (all services)

| Concern | Command | Description |
| --- | --- | --- |
| Format everything | `npm run format` | Apply formatting to Java + TypeScript + webapp |
| Check formatting | `npm run format:check` | Verify formatting without changes (CI) |
| Lint everything | `npm run lint` | Format check + Biome + typecheck |
| Full check | `npm run check` | Comprehensive: format + lint + typecheck |
| CI check | `npm run ci` | CI-optimized check across all services |

### Per-service commands

| Service | Format | Format Check | Lint | Check |
| --- | --- | --- | --- | --- |
| **Webapp** | `npm run format:webapp` | `npm run format:webapp:check` | `npm run lint:webapp` | `npm run check:webapp` |
| **Java** | `npm run format:java` | `npm run format:java:check` | — | — |
| **Intelligence Service** | `npm run format:intelligence-service` | `npm run format:intelligence-service:check` | `npm run lint:intelligence-service` | `npm run check:intelligence-service` |
| **Webhook Ingest** | `npm run format:webhook-ingest` | `npm run format:webhook-ingest:check` | `npm run lint:webhook-ingest` | `npm run check:webhook-ingest` |

### Additional commands

| Concern | Command |
| --- | --- |
| Webapp build | `npm run build:webapp` |
| Webapp tests | `npm run test:webapp` |
| Webapp typecheck | `npm run typecheck:webapp` |
| Webapp Storybook | `npm -w webapp run build-storybook` |
| Application-server tests | **Three test tiers:** <br>• `./mvnw test` runs unit tests (`@Tag("unit")`) <br>• `./mvnw verify` runs unit + integration tests (`@Tag("integration")`) <br>• `./mvnw test -Plive-tests` runs live GitHub API tests (`@Tag("live")`) <br><br>Live tests require GitHub App credentials configured in `application-live-local.yml` (gitignored). The Maven profile is the single guard—tests only run when explicitly activated. |

**Script naming conventions:**

- `format` = apply formatting fixes
- `format:check` = verify formatting (read-only, for CI)
- `lint` = run linting checks
- `lint:fix` = apply lint fixes  
- `check` = comprehensive verification (format + lint + typecheck)
- `check:fix` = apply all fixes
- `ci` = CI-optimized output

Document any skipped gate in the PR description with a rationale. Always finish a change set by running `npm run format` followed by `npm run check` so both styling and type checks reflect the final state.

## 4. Code generation & forbidden edits

We rely heavily on generated artifacts. Never hand-edit these directories—regenerate instead:

| Artifact | Source command |
| --- | --- |
| `server/application-server/openapi.yaml` | `npm run generate:api:application-server:specs` (runs `./mvnw verify -DskipTests=true -Dapp.profiles=specs`). |
| `webapp/src/api/**/*`, `webapp/src/api/@tanstack/react-query.gen.ts`, `webapp/src/api/client.gen.ts`, `webapp/src/api/types.gen.ts` | `npm run generate:api:application-server:client` (wraps `npm -w webapp run openapi-ts`). |
| `server/application-server/src/main/java/de/tum/in/www1/hephaestus/intelligenceservice/**` | `npm run generate:api:intelligence-service:client` (OpenAPI Generator CLI). |
| `server/intelligence-service/openapi.yaml` | `npm run generate:api:intelligence-service:specs` (runs `npm -w server/intelligence-service run openapi:export`). |
| `server/intelligence-service/src/shared/db/schema.ts` | `npm run db:generate-models:intelligence-service` (requires the application-server database to be reachable). |
| `docs/contributor/erd/schema.mmd` | `npm run db:generate-erd-docs` (connects to the same Postgres instance). |

Regeneration is destructive; stash local edits before running these commands. Check diffs carefully—generated clients must be committed alongside API changes.

## 5. Database workflow (Liquibase)

- Liquibase changelog files live under `server/application-server/src/main/resources/db/changelog/` and are included via `master.xml`.
- Use `npm run db:draft-changelog` after changing JPA entities. The script will:
  1. Spin up PostgreSQL through Docker (ensure Docker is running or set `CI=true` with a ready Postgres).
  2. Snapshot the schema, run Liquibase diff, and create a timestamped changelog file.
  3. Tear down the temporary container.
- Trim the generated changelog to only the real schema deltas (e.g., new columns). Never commit the raw diff wholesale—prune back to the minimal change set before renaming it into `db/changelog/`.
- After drafting a changelog, run `npm run db:generate-erd-docs` and `npm run db:generate-models:intelligence-service` to keep ERD docs and Drizzle schema in sync.
- Never manually edit generated Liquibase diff sections unless you fully understand the implications. Prefer creating a follow-up changelog to fix mistakes.

## 6. Frontend (webapp) expectations

- Follow the container/presentation split already in place (route files under `src/routes/**` fetch data and pass it to components under `src/components/**`). Keep components pure and side-effect free.
- Fetch data exclusively with TanStack Query v5 and the generated helpers in `@/api/@tanstack/react-query.gen.ts`. Spread the option objects: `useQuery(getTeamsOptions({ ... }))`. Use the generated `*.QueryKey()` helpers for cache invalidation.
- Do not call `fetch` directly; reuse the generated `@hey-api` client configured in `src/api/client.ts` and the shared QueryClient from `src/integrations/tanstack-query/root-provider.tsx`.
- State management lives in the colocated Zustand stores (`src/stores/**`). Derive UI state from TanStack Query results instead of duplicating loading/error flags.
- TypeScript style mirrors `.github/instructions/tsx.instructions.md`: explicit prop interfaces, no `React.FC`, favour `type` aliases for composite shapes, prefer discriminated unions or Zod guards when needed, keep imports using the `@/*` alias, avoid deep relative paths.
- Styling: Tailwind utility classes + shadcn primitives in `src/components/ui`. Compose class names with `clsx`/`tailwind-merge`; use tokenized colors (`bg-surface`, `text-muted`, etc.).
- Accessibility: follow shadcn patterns, wire up ARIA roles, and manage focus for dialogs/menus. Storybook stories should cover default/variant/edge states and satisfy `.github/instructions/storybook.instructions.md`.
- Tests: extend Vitest coverage when you change behaviour (`npm run test:webapp`). Use Testing Library queries that mirror user intent (`getByRole`, `findByText`).
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
- Group new tests under the proper JUnit tag so CI picks them up (`@Tag("unit")`, `@Tag("integration")`, or `@Tag("live")`). Follow the mantra in `.github/instructions/java-tests.instructions.md` (AAA structure, single assertion focus, deterministic data).
- Reuse existing DTO converters/mappers instead of duplicating mapping logic. Look at `gitprovider.team` for established patterns.
- Security: new endpoints must enforce permissions using the existing security utilities (`EnsureAdminUser`, etc.).
- Keep Liquibase changelog IDs monotonic and descriptive. Align entity annotations with the generated change sets.
- Annotate record components in DTOs with `org.springframework.lang.NonNull` when the API requires a value; leave optional fields bare so the OpenAPI spec stays minimal without extra schema annotations.
- Prefer resource-oriented workspace endpoints: express lifecycle transitions via HTTP methods (for example `PATCH /workspaces/{workspaceSlug}/status`) instead of RPC-style verbs and return consistent `ProblemDetail` payloads for errors.
- When adding or changing REST endpoints, follow the centralized exception-handling rules in `docs/contributor/api-error-handling.md` so every controller returns RFC-7807 `ProblemDetail` responses via `@RestControllerAdvice`.
- When integrating with the intelligence-service client, always regenerate (`npm run generate:api:intelligence-service:client`) after touching the spec and commit the updated Java files.
- Controller-level integration tests should extend `AbstractWorkspaceIntegrationTest` (or an equivalent domain-specific base), exercise access control through `WebTestClient` + `TestAuthUtils`, and follow the contributor testing guide's checklist. This keeps authentication, validation, and persistence assertions consistent across new endpoints.

## 8. Intelligence service expectations (TypeScript)

- Uses Hono as the HTTP framework with Vercel AI SDK for LLM orchestration.
- Settings live in `src/env.ts`; `MODEL_NAME` and `DETECTION_MODEL_NAME` must be provider-qualified (`openai:gpt-5-mini`, `azure:gpt-5-mini`, etc.).
- Drizzle ORM for database access; schema is auto-generated via `npm run db:introspect` from the application-server's Liquibase-managed PostgreSQL.
- OpenAPI spec is exported via `npm run openapi:export`.
- Formatting: run `npm run format:intelligence-service`. Biome handles linting and formatting.

## 9. Webhook ingest service expectations (TypeScript)

- Uses Hono as the HTTP framework with `@nats-io/jetstream` for NATS publishing. Keep NATS subject naming consistent (`github.<owner>.<repo>.<event>`, `gitlab.<namespace>.<project>.<event>`).
- Security: HMAC-SHA256 signature verification for GitHub, token verification for GitLab. Uses `crypto.timingSafeEqual()` to prevent timing attacks.
- Configuration via environment variables with Zod validation (see `src/env.ts`). When adding config, extend the Zod schema.
- Uses Biome for linting/formatting. Run `npm run check:webhook-ingest` for full validation.
- Tests: Run `npm run test:webhook-ingest` (Vitest).

## 10. Documentation & assets

- ERD diagrams live under `docs/contributor/erd/`. Regenerate via `npm run db:generate-erd-docs` after schema changes.
- Contributor documentation should stay in `docs/` (GitHub Pages). Keep README/CONTRIBUTING updates concise and actionable.
- Screenshots or large binary assets belong under `docs/images/` or the Storybook stories, not inside source directories.

## 11. Pull requests

Before opening a PR, run `npm run format && npm run check`. The PR template (`.github/PULL_REQUEST_TEMPLATE.md`) guides you through title format and checklists. Key points:

- **Title**: Follow Conventional Commits—see `CONTRIBUTING.md` for types/scopes.
- **Generated files**: Regenerate and commit OpenAPI specs, clients, and ERD docs when APIs or entities change.
- **Database changes**: Run `npm run db:draft-changelog` and prune to minimal deltas.

## 12. Known command caveats

- `npm run db:draft-changelog` requires Docker to be installed and available on PATH. In CI we set `CI=true`; locally ensure Docker Desktop/daemon is running before invoking the script.
- `npm run generate:api:intelligence-service:specs` fails unless `MODEL_NAME` and `DETECTION_MODEL_NAME` are set (use the `fake:model` provider for tooling).
- `npm run generate:api:application-server:specs` performs a full Maven `verify` against the specs profile. The initial run downloads the entire Spring Boot dependency tree (~hundreds of MB); expect several minutes on a cold cache.

Stay consistent with the existing patterns and prefer improving the structure rather than introducing ad-hoc shortcuts.
