# Server

Spring Boot 4 / Java 21 / Spring Modulith 2. Liquibase owns the schema; the OpenAPI spec and the
GraphQL clients are generated. Package layout under
`application/src/main/java/de/tum/cit/aet/hephaestus/` follows the domain (`core/`, `workspace/`, `agent/`,
`practices/`, `integration/`, `mentor/`, …) — read it rather than a copy of it here.

This file is the gotchas. Spring, JPA and Lombok idioms that the surrounding code already shows you
are not here: write code that reads like the file you are editing.

## Local development loop

`pnpm run dev` from the repo root launches `mprocs` with server and webapp in separate panes and brings up
the Postgres container. For plain terminals: `pnpm run dev:server` and `pnpm run dev:webapp`.

- **No devtools.** Hot reload is JVM HotSwap via the IDE — IntelliJ's Spring Boot run config with
  *Update Classes and Resources* on save. Method-body edits reload; signature changes, new methods and
  `@Configuration` edits need a full restart
  ([ref](https://docs.spring.io/spring-boot/how-to/hotswapping.html)).
- **`ddl-auto: validate`** locally — Liquibase owns DDL. If the validator fails on boot your DB has
  drifted: `pnpm run dev:reset`.
- **`BufferingApplicationStartup`** is wired in `Application.main()`. With `app.profiles=local`,
  `GET /actuator/startup` returns the timeline; `StartupBudgetIntegrationTest` catches per-step
  regressions in CI.

## Build traps

Each of these can leave you with the wrong result.

- **Use the reactor.** `server/generated-clients` owns every GraphQL and Outline generator and
  `server/application` consumes its JAR. From the repository root, use
  `pnpm run test:server:unit` after a fresh checkout or generated-client input change. For the repeated
  application-edit loop, use `./mvnw -f application/pom.xml test` from `server/` (and
  `-Dtest=ClassName` to focus a test) so Maven does not restore the generated module and invalidate
  application incremental compilation.
- **`-Dgroups=architecture` silently runs the unit suite instead.** `pom.xml` sets Surefire's `<groups>`
  to `${surefire.includedGroups}`, and a POM element beats the `-Dgroups` user property — so the flag is
  discarded and the default (`unit`) runs. Use `pnpm run test:server:architecture`; CI passes the
  `${surefire.includedGroups}` property that the POM reads.
- **`clean` does not guarantee a cold build.** It removes workspace outputs, but Maven Build Cache can
  restore them. The repository enables the cache for `generated-clients`; Maven logs `Found cached
  build` on a hit. Pass `-Dmaven.build.cache.enabled=false` when measuring a cold build. Schema,
  generator configuration, Java, dependency, and generated-client POM changes invalidate the entry.
- **Do not run concurrent Maven processes in one checkout.** Both write the same module `target/`
  directories.
- **`server/.env` leaks into Maven test JVMs.** A local `MANAGEMENT_PORT` collides across worktrees and
  local OAuth variables make environment-sensitive tests fail only on your machine. Run tests with
  `MANAGEMENT_PORT=0 SERVER_PORT=0`.

## Boundaries

**Always** — run the unit baseline and every affected tier before committing · tag every test (`@Tag("unit")`,
`@Tag("integration")`, `@Tag("live")`) · declare a new endpoint's permission explicitly.

**Ask first** — schema changes · security configuration · a new `pom.xml` dependency · workspace
authorization logic.

**Never** — commit credentials · `System.out.println` (log through SLF4J with `{}` placeholders, and
never log a token) · `@Transactional` on a controller (service layer only) · expose an entity from a
controller.

## Null-safety

NullAway checks all handwritten production and test code in JSpecify mode; generated sources are
excluded. Every new package needs a `package-info.java` containing
`@org.jspecify.annotations.NullMarked`, and the build rejects missing null-marking scopes and
`NullAway` suppressions. Use `@Nullable` only for genuine absence and place it on the precise type:
`List<@Nullable String>` permits null elements; `String @Nullable []` permits a null array reference.
Fix violations at the contract or implementation boundary. In tests, refine a nullable result once
before using it rather than adding duplicate assertions. Run `pnpm run test:server:unit` after changing
a nullness contract.

## Test tiers

| Tag | Runs | Command |
|---|---|---|
| `unit` | no Spring context | `pnpm run test:server:unit` |
| `architecture` | ArchUnit + Modulith verification | `pnpm run test:server:architecture` |
| `integration` | full context + Testcontainers | `pnpm run test:server:integration` |
| `database` | contract tests against a running PostgreSQL | the *App Server: Database* CI job: from `server/`, `./mvnw -f application/pom.xml -Dsurefire.includedGroups=database surefire:test` with `SPRING_DATASOURCE_URL`, `_USERNAME` and `_PASSWORD` set |
| `live` | real GitHub API | from `server/`, `./mvnw test -Plive-tests` |

Live tests need GitHub App credentials in `application-live-local.yml` (gitignored); the Maven profile
is the only guard.

**An integration test's *filename* decides whether it ever runs.** Failsafe includes
`**/*IntegrationTest.java` and `**/*LiquibaseTest.java` and nothing else, so a `@Tag("integration")`
class named anything else is silently never executed by `./mvnw verify` — it fails no build and reports
no skip.

Name tests `should[ExpectedBehavior]When[Condition]`. Controller-level integration tests extend
`AbstractWorkspaceIntegrationTest` (or a domain-specific base) and exercise access control through
`WebTestClient` + `TestAuthUtils` — the identity comes from the mock JWT **token string**, not from an
annotation.

**Rows written by earlier tests are already there.** Assert on the row you created, never on a count
or on "the only" result, and never write cleanup that another test depends on having run.

## Things that bite

- **`Issue` is SINGLE_TABLE with `PullRequest` as a subclass.** A JPQL query over `Issue` therefore
  returns pull requests too. Any query that means "issues only" must say `WHERE TYPE(i) = Issue`
  explicitly — see `MentorContextQueryRepository` and `ReviewableArtifactOwnershipRepository`. A test
  with a mocked repository cannot catch a missing `TYPE(…)`.
- **The changelog is untested by the suite.** Tests run against `ddl-auto: create`, so a broken
  changelog passes every tier; `pnpm run db:check-drift` is the check
  (`docs/contributor/database-migration.mdx`).
- **A native `@Query` may not contain an apostrophe inside a `--` comment.** Hibernate reads it as the
  start of a string literal and the whole `ApplicationContext` fails to build, naming something else;
  `NativeQueryCommentArchTest` turns that into a named failure.
- **`EntityManager` is injected as a `@PersistenceContext` field**, not through the constructor
  (`WorkspaceMembershipService`, `GitHubUserProcessor`). Everything else is constructor injection via
  `@RequiredArgsConstructor`.
- **A bean that exists in one runtime role only is gated on that role** (`@ConditionalOnProperty` on
  a `RuntimeRole` property, as in `LeaderboardTaskScheduler`), and a consumer that must survive its
  absence takes `ObjectProvider` (`WorkspaceSyncTargetProvider`). An ungated consumer crash-loops the
  `worker` and `webhook` runtimes, which start a different slice of the context.
- **`SlackMessageService` resolves bot tokens per workspace at send time** via `ConnectionService`.
  There is no global `App` bean and no `slack.token` property; admins connect each workspace through
  `/oauth/callback/slack`.

## API and security conventions

- Workspace-scoped controllers carry `@WorkspaceScopedController` and take a `WorkspaceContext`.
  Authorization is declared, never assumed: `@RequireAtLeastWorkspaceAdmin`, `@RequireWorkspaceOwner`,
  `@RequireMentorAccess`, `@PreAuthorize("hasAuthority('app_admin')")` for instance-admin routes.
  Anything reachable while impersonating goes through `ImpersonationGuard`. Admin mutations declare
  `@Audited` or `@AuditExempt` — an ArchUnit rule enforces it.
- Express lifecycle transitions as HTTP methods (`PATCH /workspaces/{slug}/status`), not RPC verbs.
- Every error is an RFC-7807 `ProblemDetail` produced by a `@RestControllerAdvice` — the rules are in
  `docs/contributor/api-error-handling.md`.
- **A schema type without a `DTO` suffix is dropped from the generated spec** unless it is listed in
  `OpenAPIConfiguration.ALLOWED_DOMAIN_OBJECTS`. The webapp client then simply has no type for it, with
  no error anywhere. Domain types the API deliberately exposes (`ProblemDetail`, `PracticeBinding`, …)
  are there for this reason.
- DTOs are records. All bare components are non-null under `@NullMarked`; add JSpecify `@NonNull` when
  that component must also appear in the generated schema's `required` list. A component the API may
  omit is `@Nullable`, never bare.
- **Never wrap a DTO component in `Optional<>`.** springdoc unwraps it to the value type but still marks
  it required, so the generated TypeScript declares it non-optional and its response transformer
  converts it unconditionally — a value the server never sends is typed as one it always sends. Use
  `@Nullable T`.

## Schema changes

Procedure: `docs/contributor/database-migration.mdx`. What the drift gate reads from an entity:

- An un-annotated field in a `@NullMarked` package is a NOT NULL column; a column that may be NULL
  carries `@Nullable` on the field.
- A foreign key backing a plain id column with no JPA association is named `sfk_*`; the gate
  ignores that prefix (`application/pom.xml`, `diffExcludeObjects`). An association's foreign key
  keeps `fk_*` and is drift-checked, so a misnamed constraint fails in either direction.

## Webhook receiver

`integration/core/webhook/`. Pure verifier and builder classes (HMAC, GitLab token, subject builders,
dedup id) sit beside the Spring-backed controllers, the JetStream publisher and the stream bootstrap —
all gated together on `RuntimeRole.WEBHOOK_PROPERTY`. Configuration binds to `hephaestus.webhook.*`
through `core.webhook.WebhookProperties`, shared with auto-registration in `workspace/GitLabWebhookService`.

- **Production runs it in its own `webhook-server` container** — the same image as `application-server`
  with `SPRING_PROFILES_ACTIVE=prod,webhook` — so an app-server deploy does not interrupt reception.
  That matters because push events on GitHub and GitLab are **not manually redeliverable**: a webhook
  missed during a restart is lost.
- **Subject grammar**: `github.<owner>.<repo>.<event>`, `gitlab.<namespace>.<project>.<event>`. Dots
  inside a path segment become `~`; nested GitLab groups join with `~`. The consumer-side builder
  `integration.core.consumer.ConsumerSubjectMath#buildSubjectPrefix` must agree —
  `SubjectGrammarRoundTripTest` enforces it for every committed fixture.
- **ArchUnit guards the primitives**: `HexEncodingArchTest` (only `HexFormat.of()`),
  `LocaleSafetyArchTest` (no naked `toLowerCase`/`toUpperCase`). `application/pom.xml` sets
  per-package JaCoCo branch floors, checked whenever `-DskipCoverage=false`: `test:server:unit`,
  `test:server:verification` and the *App Server: Unit and architecture* CI job. Raise a floor when
  its package clears the next step.

## Container image

Paketo Cloud Native Buildpacks with Application CDS; no `Dockerfile`. From `server/`:
`./mvnw -pl application -am package -DskipTests`, then
`pack build hephaestus/application-server --path application/target/hephaestus-application-*.jar --descriptor application/project.toml --run-image <the run image pinned in .github/workflows/ci-build.yml>`.
Pinning and rationale: `docs/admin/buildpacks-cds-decision.md`.
