# ADR 0001: Server stack upgrade to Spring Boot 4 baseline

**Status**: Accepted (epic #1096)
**Date**: 2026-05-18
**Closes**: #1118, #1119, #1120, #1121, #1122

## Context

`server/application-server/` ran on Spring Boot 3.5.14 / Hibernate 6 / Spring Modulith 1.4 /
Spring Security 6 / Liquibase 4 / springdoc 2.x. This epic lifts it to Boot 4 / Hibernate 7 /
Modulith 2 / Security 7 / Liquibase 5 / springdoc 3 in one PR so the consolidation work that
follows lands on a single stable target.

## Bumped

| Component | From | To | Notes |
|---|---|---|---|
| `spring-boot-starter-parent` | 3.5.14 | **4.0.6** | Java 21 LTS still supported. |
| `spring-modulith-bom` | 1.4.11 | **2.0.6** | `@ApplicationEventListener` removed. |
| Hibernate ORM | 6.x | **7.2.12** (Boot 4 managed) | Stricter NOT-NULL CHECK constraints for `@Inheritance(SINGLE_TABLE)`. |
| Liquibase | 4.x | **5.0.2** (Boot 4 managed) | Runtime engine. |
| `liquibase-hibernate6` 4.33.0 | | **`liquibase-hibernate7` 5.0.2-rc1** | Maven-plugin-only scope (not on the runtime classpath). Replace with the GA when `org.liquibase.ext:liquibase-hibernate7:5.0.2` ships. |
| springdoc-openapi | 2.8.15 | **3.0.3** | TS client regenerated; webapp typecheck green. |
| `resilience4j-spring-boot3` 2.4.0 | | **`resilience4j-spring-boot4` 2.4.0** | Drop-in artifact swap. |
| `okhttp` 4.12.0 | | **`okhttp-jvm` 5.3.2** | bare `okhttp:5.x` is a near-empty KMP shim (square/okhttp#8913). |
| `mockwebserver` | | `mockwebserver3` | Square's canonical artifact for the OkHttp 5 line. |
| `sentry-spring-boot-starter-jakarta` 8.40.0 | | **`sentry-spring-boot-4-starter` 8.41.0` | Sentry split the starter at 8.24.0-alpha.1; the jakarta starter is the Boot 3 line. |
| Jackson 2 (`com.fasterxml.jackson.*`) | | **Jackson 3 (`tools.jackson.*`)** | Annotations stay under `com.fasterxml.jackson.annotation`. |

## Deleted

- `hypersistence-utils-hibernate-63` — replaced by Hibernate-native `@JdbcTypeCode(SqlTypes.JSON)` on 5 entities (`AgentJob`, `ChatMessage`, `Practice`, `PracticeFinding`, `ProjectField`).
- `ClassImportIntegratorIntegratorProvider` — registered 6 DTOs that no `select new` callsite used unqualified.
- `JacksonConfig.java` + `JsonNullableModule` registration — `JsonNullable<T>` had zero Java consumers.
- `@EnableWebSecurity` on `SecurityConfig` — auto-enabled since Security 6.
- `openapi-generator{-maven-plugin}` `<dependency>` blocks — orphaned from the deleted intelligence-service.
- `spring-boot-starter-aop` — Boot 4 deleted the artifact; AOP comes transitively via data-jpa/security.
- `maven-compiler-plugin` override (Boot 4 ships ≥ 3.15.0), `plexus-utils` pin (Boot 4 transitive is current), explicit `netty-resolver-dns-native-macos` (Boot 4 netty-bom manages it).

## Added

- `spring-boot-restclient` — Boot 4 split `RestTemplateBuilder` out of starter-web.
- `spring-boot-webclient` — Boot 4 split `WebClient.Builder` autoconfig out of starter-webflux.
- `spring-boot-webtestclient` (test scope) — `@AutoConfigureWebTestClient` moved here in Boot 4.
- `tools.jackson.dataformat:jackson-dataformat-yaml` — used by `AchievementRegistry`.
- `Jackson3FormatMapper` + `HibernateJacksonFormatMapperConfig` — Hibernate 7.2 ships only a Jackson-2 `JsonFormatMapper`, so Jackson 3 JSONB columns (e.g. `JsonNode metadata`) crash on read/write. Delete both files when bumping to Hibernate 7.3 (HHH-19890 adds an upstream `Jackson3JsonFormatMapper`).
- `spring-modulith-core` + `spring-modulith-starter-test` — drives `ApplicationModulesVerificationTest`.
- `spring-retry 2.0.12` (no longer in Boot 4's dependency management).
- Testcontainers 2.0 artifact renames: `junit-jupiter` → `testcontainers-junit-jupiter`, `postgresql` → `testcontainers-postgresql`.

## Held

| Hold | Revisit trigger |
|---|---|
| **Java 21 → 25 LTS** | Modulith 2.x, JGit, docker-java certifications on Java 25. |
| **docker-java 3 → 4** | 4.x GA on Maven Central (no 4.x exists as of merge). |
| **Sentry 8 → 9** | Tied to the observability epic. |

## Module verification

`ApplicationModulesVerificationTest` runs `ApplicationModules.of(Application.class).verify()` and
compares the resulting violation set against SHA-256 hashes in
`src/test/resources/modulith-violation-baseline.txt`. **Any new violation hash fails CI; any stale
hash fails CI.** The architecture epic shrinks the baseline by narrowing each module's
`allowedDependencies`. The 11 packages annotated with `@ApplicationModule(displayName = …)` in
this PR currently leave `allowedDependencies` unset (open modules); the architecture epic owns
the closure of each module's API.

## Security posture

The application is a **stateless OAuth2 resource server** (`Authorization: Bearer …`).
Spring Security 7 flipped CSRF defaults for browser-form flows; the explicit
`.csrf(csrf -> csrf.disable())` call on both `SecurityFilterChain` beans (main + LLM proxy)
is load-bearing. `SecurityFilterChainArchitectureTest` locks the resolved filter chain
composition at boot time (no `CsrfFilter`, `JobTokenAuthenticationFilter` precedes
`UsernamePasswordAuthenticationFilter` on the proxy chain), and an ArchUnit rule asserts only
`SecurityConfig` and `LlmProxySecurityConfig` may declare `SecurityFilterChain` beans.

## Rollback

`git revert` the merge commit, then `mvn -f server/application-server/pom.xml verify`. The
soak runbook at `docs/operations/server-deps-boot4-soak.md` covers the deploy-side gate.
