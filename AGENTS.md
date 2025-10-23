# Hephaestus Agent Handbook

This file governs the entire repository. Combine these guardrails with the scoped instructions under `.github/instructions/**` (general coding, TSX, Storybook, Java tests).

## 1. Architecture map
- `server/application-server/`: Spring Boot 3.5, Liquibase-managed PostgreSQL schema, synchronous + reactive APIs, generated OpenAPI spec in `openapi.yaml`.
- `webapp/`: React 19 + TanStack Router/Query, Tailwind 4 UI kit (`src/components/ui`), generated API client in `src/api/**`.
- `server/intelligence-service/`: FastAPI service orchestrating AI models. OpenAPI spec is exported via Poetry and mirrored into the Java client under the application server.
- `server/webhook-ingest/`: FastAPI webhook intake that forwards events into NATS JetStream.
- `keycloakify/`: Custom Keycloak theme built with Vite/React and copied into Keycloak containers.
- `docs/`: Contributor docs (including the ERD that `db:generate-erd-docs` regenerates).

## 2. Toolchain & environment prerequisites
- **Node.js**: Use the exact version from `.node-version` (currently 22.10.0). Stick with npm—the repo maintains `package-lock.json` and uses npm workspaces.
- **Java**: JDK 21 (see `pom.xml`). Maven wrapper is checked in; run builds through `./mvnw` or `mvn` with Java 21 configured.
- **Python**: Python 3.13 with Poetry 2.x. Both Python services keep virtualenvs inside their folders (`.venv`). Run `npm run bootstrap:py` before formatting/linting to ensure dev dependencies are installed.
- **Docker & Docker Compose**: Required for database helper scripts (`scripts/db-utils.sh`) and for spinning up Postgres/Keycloak/NATS locally.
- **Databases**: Default PostgreSQL DSN is `postgresql://root:root@localhost:5432/hephaestus`. The database helpers spin this up for you via Docker.
- **Environment variables**: When generating intelligence service OpenAPI specs locally set `MODEL_NAME=fake:model` and `DETECTION_MODEL_NAME=fake:model` (the FastAPI settings expect a provider-qualified model name).

## 3. Quality gates & routine commands
Run the relevant commands locally before opening a PR:

| Concern | Commands |
| --- | --- |
| Format everything | `npm run format` (wraps Java + Python + webapp formatting) |
| Lint everything | `npm run lint` (runs Prettier check for Java + flake8 for Python + Biome + TypeScript type check) |
| Webapp build | `npm --workspace webapp run build` (Vite build + `tsc --noEmit`) |
| Webapp tests | `npm --workspace webapp run test` (Vitest) and add focused unit tests when touching logic. |
| Storybook | `npm --workspace webapp run build-storybook` (Chromatic depends on a clean build). |
| Application-server tests | Use Maven groups to mirror CI: `./mvnw test -Dgroups=unit`, `-Dgroups=integration`, `-Dgroups=architecture`. |
| Intelligence service lint/type check | `poetry run black --check .`, `poetry run flake8 .`, `poetry run mypy .` inside `server/intelligence-service`. |
| Webhook ingest lint | `poetry run black --check .` and `poetry run flake8 .` inside `server/webhook-ingest`. |

Document any skipped gate in the PR description with a rationale.

## 4. Code generation & forbidden edits
We rely heavily on generated artifacts. Never hand-edit these directories—regenerate instead:

| Artifact | Source command |
| --- | --- |
| `server/application-server/openapi.yaml` | `npm run generate:api:application-server:specs` (runs `mvn verify -DskipTests=true -Dapp.profiles=specs`). |
| `webapp/src/api/**/*`, `webapp/src/api/@tanstack/react-query.gen.ts`, `webapp/src/api/client.gen.ts`, `webapp/src/api/types.gen.ts` | `npm run generate:api:application-server:client` (wraps `npm -w webapp run openapi-ts`). |
| `server/application-server/src/main/java/de/tum/in/www1/hephaestus/intelligenceservice/**` | `npm run generate:api:intelligence-service:client` (OpenAPI Generator CLI). |
| `server/intelligence-service/openapi.yaml` | `MODEL_NAME=fake:model DETECTION_MODEL_NAME=fake:model npm run generate:api:intelligence-service:specs` (delegates to `poetry run openapi`). |
| `server/intelligence-service/app/db/models_gen.py` | `npm run db:generate-models:intelligence-service` (requires the application-server database to be reachable). |
| `docs/contributor/erd/schema.mmd` | `npm run db:generate-erd-docs` (connects to the same Postgres instance). |

Regeneration is destructive; stash local edits before running these commands. Check diffs carefully—generated clients must be committed alongside API changes.

## 5. Database workflow (Liquibase)
- Liquibase changelog files live under `server/application-server/src/main/resources/db/changelog/` and are included via `master.xml`.
- Use `npm run db:draft-changelog` after changing JPA entities. The script will:
  1. Spin up PostgreSQL through Docker (ensure Docker is running or set `CI=true` with a ready Postgres).
  2. Snapshot the schema, run Liquibase diff, and create a timestamped changelog file.
  3. Tear down the temporary container.
- After drafting a changelog, run `npm run db:generate-erd-docs` and `npm run db:generate-models:intelligence-service` to keep ERD docs and SQLAlchemy models in sync.
- Never manually edit generated Liquibase diff sections unless you fully understand the implications. Prefer creating a follow-up changelog to fix mistakes.

## 6. Frontend (webapp) expectations
- Follow the container/presentation split already in place (route files under `src/routes/**` fetch data and pass it to components under `src/components/**`). Keep components pure and side-effect free.
- Fetch data exclusively with TanStack Query v5 and the generated helpers in `@/api/@tanstack/react-query.gen.ts`. Spread the option objects: `useQuery(getTeamsOptions({ ... }))`. Use the generated `*.QueryKey()` helpers for cache invalidation.
- Do not call `fetch` directly; reuse the generated `@hey-api` client configured in `src/api/client.ts` and the shared QueryClient from `src/integrations/tanstack-query/root-provider.tsx`.
- State management lives in the colocated Zustand stores (`src/stores/**`). Derive UI state from TanStack Query results instead of duplicating loading/error flags.
- TypeScript style mirrors `.github/instructions/tsx.instructions.md`: explicit prop interfaces, no `React.FC`, favour `type` aliases for composite shapes, prefer discriminated unions or Zod guards when needed, keep imports using the `@/*` alias, avoid deep relative paths.
- Styling: Tailwind utility classes + shadcn primitives in `src/components/ui`. Compose class names with `clsx`/`tailwind-merge`; use tokenized colours (`bg-surface`, `text-muted`, etc.).
- Accessibility: follow shadcn patterns, wire up ARIA roles, and manage focus for dialogs/menus. Storybook stories should cover default/variant/edge states and satisfy `.github/instructions/storybook.instructions.md`.
- Tests: extend Vitest coverage when you change behaviour (`npm --workspace webapp run test`). Use Testing Library queries that mirror user intent (`getByRole`, `findByText`).
- Routing: declare new routes via `createFileRoute`, respect loader/guard conventions, and keep loader side effects out of render paths.
- Never hand-edit `routeTree.gen.ts`; it is generated by TanStack Router tooling.

## 7. Application server (Java/Spring) expectations
- Keep business logic in services annotated with `@Service` and transactional boundaries (`@Transactional`) where needed. Controllers should be thin (input validation + delegation).
- Use Lombok consistently (`@Getter`, `@Setter`, etc.) but prefer explicit builders or records when immutability helps.
- Group new tests under the proper JUnit tag so CI picks them up (`@Tag("unit")`, `@Tag("integration")`, or `@Tag("architecture")`). Follow the mantra in `.github/instructions/java-tests.instructions.md` (AAA structure, single assertion focus, deterministic data).
- Reuse existing DTO converters/mappers instead of duplicating mapping logic. Look at `gitprovider.team` for established patterns.
- Security: new endpoints must enforce permissions using the existing security utilities (`EnsureAdminUser`, etc.).
- Keep Liquibase changelog IDs monotonic and descriptive. Align entity annotations with the generated change sets.
- When integrating with the intelligence-service client, always regenerate (`npm run generate:api:intelligence-service:client`) after touching the spec and commit the updated Java files.

## 8. Python services expectations
- Both services rely on Poetry with in-project virtualenvs. Run `poetry install --with dev --no-root` before running tooling.
- Intelligence service:
  - Settings live in `app/settings.py`; `MODEL_NAME` and `DETECTION_MODEL_NAME` must be provider-qualified (`openai:gpt-4o`, `fake:model`, etc.). For tooling/CI we rely on the `fake` provider.
  - FastAPI routers under `app/routers/**`; keep business logic in `app/services/**` or domain-specific modules. Unit-test model/detector logic when modifying heuristics.
  - OpenAPI generation toggles `settings.IS_GENERATING_OPENAPI` to disable Langfuse; avoid leaking runtime-only side effects into generation mode.
- Webhook ingest service:
  - Uses FastAPI with NATS JetStream (`nats-py`). Keep NATS subject naming consistent with the README (`github.<owner>.<repo>.<event>`).
  - Configuration via environment variables (see README). When adding config, extend `pyproject.toml` and `.env` templates accordingly.
- Formatting: run `poetry run black .` (or `--check`) and `poetry run flake8 .`. Add type hints so mypy stays green in the intelligence service.

## 9. Keycloakify theme
- Theme assets live in `keycloakify/`. Build them with `npm run build-keycloak-theme`; artifacts land in `keycloakify/dist_keycloak` and are mounted by docker-compose.
- Keep components idiomatic React/TypeScript (same style rules as the webapp). Storybook lives here as well; ensure new components ship with stories covering the real Keycloak context.
- Do not commit the heavy `node_modules` content—only the generated theme under `dist_keycloak` that the compose files rely on.

## 10. Documentation & assets
- ERD diagrams live under `docs/contributor/erd/`. Regenerate via `npm run db:generate-erd-docs` after schema changes.
- Contributor documentation should stay in `docs/` (GitHub Pages). Keep README/CONTRIBUTING updates concise and actionable.
- Screenshots or large binary assets belong under `docs/images/` or the Storybook stories, not inside source directories.

## 11. Commit & PR checklist
Before marking work ready for review:
- [ ] Regenerate and commit any impacted OpenAPI specs, clients, ERD docs, or generated SQLAlchemy models.
- [ ] Run the formatting/linting/typecheck/test commands relevant to the modified modules; capture output for the PR description if CI cannot run a job locally.
- [ ] Verify database migrations through `db:draft-changelog` when JPA entities change and inspect the produced XML.
- [ ] Double-check that no generated files were edited manually.
- [ ] Follow Conventional Commit semantics for PR titles (`feat(webapp): ...`, etc., see `CONTRIBUTING.md`).

## 12. Known command caveats
- `npm run db:draft-changelog` requires Docker to be installed and available on PATH. In CI we set `CI=true`; locally ensure Docker Desktop/daemon is running before invoking the script.
- `npm run generate:api:intelligence-service:specs` fails unless `MODEL_NAME` and `DETECTION_MODEL_NAME` are set (use the `fake:model` provider for tooling).
- `npm run generate:api:application-server:specs` performs a full Maven `verify` against the specs profile. The initial run downloads the entire Spring Boot dependency tree (~hundreds of MB); expect several minutes on a cold cache.

Stay consistent with the existing patterns and prefer improving the structure rather than introducing ad-hoc shortcuts.
