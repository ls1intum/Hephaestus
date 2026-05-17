# ADR 0001: Server stack upgrade to Spring Boot 4 baseline

**Status**: In progress (epic #1096)
**Date**: 2026-05-17
**Closes**: #1118, #1119, #1120, #1121, #1122
**Drivers**: Felix Dietrich (TUM)

## Context

`server/application-server/` ran on Spring Boot 3.5.14 / Hibernate 6.x / Spring Modulith 1.4 / Spring
Security 6 / Liquibase 4 / springdoc 2.x. The 2026 baseline is Boot 4 / Hibernate 7 / Modulith 2 /
Security 7 / Liquibase 5 / springdoc 3. The consolidation work that follows (#1066 etc.) needs a
single stable target; doing the upgrade on the current pre-refactor monolith isolates
dependency-bump risk from the architectural moves.

This ADR records what bumped, what was held, and the revisit trigger for each holdback.

## Bumped

| Component | From | To | Notes |
|---|---|---|---|
| Spring Boot | 3.5.14 | **4.0.6** | Brings Spring Framework 7, Servlet 6.1, Tomcat 11.0.21, Jakarta EE 11. |
| Spring Modulith | 1.4.11 | **2.0.6** | `@ApplicationEventListener` removed; `verify()` API stricter. |
| Hibernate ORM | 6.x (Boot-managed) | **7.2.12** (Boot 4 managed) | Stricter lazy-loading; native JSON support via `@JdbcTypeCode(SqlTypes.JSON)`. |
| Liquibase | 4.x (Boot-managed) | **5.0.2** (Boot 4 managed) | New migration-history schema check; format-spec tightened. |
| `liquibase-hibernate6` | 4.33.0 | **`liquibase-hibernate7` 5.0.2-rc1** | RC pin: stable 5.x not yet published on Maven Central for this extension. Revisit when 5.0.2 GA ships. |
| `hypersistence-utils-hibernate-63` | 3.15.2 | **REMOVED** | Replaced by Hibernate-native `@JdbcTypeCode(SqlTypes.JSON)` across 5 JSONB entities + `UserAchievement`. Inconsistent dual mapping eliminated. |
| Spring Security | 6.x | **7.0.x** (Boot 4 managed) | Lambda DSL only; `@EnableWebSecurity` redundant and removed. |
| springdoc OpenAPI | 2.8.15 | **3.0.3** | JSON Schema 2020-12 emission; webapp TS client regen required. |
| `springdoc-openapi-maven-plugin` | 1.5 | unchanged | 1.6 not yet published; current pin still works. |
| resilience4j | `resilience4j-spring-boot3 2.4.0` | **`resilience4j-spring-boot4 2.4.0`** | Artifact rename only; same major version line. Programmatic CircuitBreakerRegistry beans untouched. |
| OkHttp | 4.12.0 | **`okhttp-jvm 5.3.2`** | Maven users must use the `-jvm` classifier; the bare `okhttp:5.x` JAR is a KMP shim (square/okhttp#8913). `mockwebserver` test artifact renamed to `mockwebserver3`. |
| Sentry | `sentry-spring-boot-starter-jakarta 8.40.0` | **`sentry-spring-boot-4-starter 8.41.0`** | Sentry split the starter at 8.24.0-alpha.1; the jakarta starter is the Boot 3 line. |
| Keycloak admin client | 26.0.9 | unchanged | Already current; verified compatible with Security 7. |
| Jackson | 2.x (`com.fasterxml.jackson.*`) | **3.1.2** (`tools.jackson.*`) | Package rename; `ObjectMapper` immutable, `JsonMapper.builder()` builder pattern; `JsonProcessingException` → unchecked `JacksonException`; `ParameterNamesModule` + standalone `JavaTimeModule` dropped (folded into core). Jackson annotations stay under `com.fasterxml.jackson.annotation`. |
| Java | 21 LTS | unchanged | Boot 4 supports Java 17/21/25; we stay on 21. |

## Deleted

- `hypersistence-utils-hibernate-63` — replaced by Hibernate-native JSON mapping.
- `ClassImportIntegratorIntegratorProvider` — registered 6 DTOs for JPQL `select new`; grep confirmed all 2 callsites use fully-qualified class names, so the integrator was dead code. Removed `spring.jpa.properties.hibernate.integrator_provider` from `application.yml`.
- `org.openapitools:openapi-generator-maven-plugin` and `openapi-generator` `<dependency>` entries — orphaned from the deleted intelligence-service; 0 `org.openapitools.codegen` imports in `src/`.
- `JacksonConfig.java` + `JsonNullableModule` registration — `JsonNullable<T>` had zero Java usages; configuration moved to `spring.jackson.*` properties in `application.yml`.
- `@EnableWebSecurity` on `SecurityConfig` — auto-enabled since Security 6.x when any `SecurityFilterChain` bean is defined.
- `maven-compiler-plugin` 3.15.0 override — Boot 4 parent ships ≥ 3.15.0.
- `plexus-utils` 3.6.1 pin — Boot 4 transitive resolution avoids the CVE-affected versions.
- explicit `netty-resolver-dns-native-macos` — Boot 4 netty-bom (4.2.x) manages it.
- `spring-boot-starter-aop` — Boot 4 deleted this artifact entirely. AOP infrastructure (spring-aop, aspectjweaver) is pulled transitively via `spring-boot-starter-data-jpa` and `spring-boot-starter-security`. We have no `@Aspect` classes of our own.

## Added

- `spring-boot-restclient` — Boot 4 split `RestTemplateBuilder` + `RestClient` autoconfig into their own module; no longer transitive via `spring-boot-starter-web`.
- `tools.jackson.dataformat:jackson-dataformat-yaml` — needed by `AchievementRegistry` for `achievements.yml` loading.
- `spring-modulith-core` (runtime) + `spring-modulith-starter-test` (test) — drives the new `ApplicationModulesVerificationTest`.
- Explicit `spring-retry` version pin (2.0.12) — Spring Boot 4 dropped this from its dependency management.

## Held

| Hold | Revisit trigger |
|---|---|
| **Java 21 → 25 LTS** | Modulith 2.x, JGit, and docker-java certifications on Java 25. |
| **docker-java 3 → 4** | 4.x GA on Maven Central. As of 2026-05-17 no 4.x version exists. |
| **Sentry 8 → 9** | Tied to the observability epic, not this upgrade. Coexistence verified during the soak. |

The principal-engineer review noted Jackson 3 was held in the original epic but the project owner
chose to migrate now (recorded as a scope-locked decision). `@ApplicationModule` declarations were
similarly pulled in: 11 packages now carry `@ApplicationModule(displayName = …)`, with
`allowedDependencies` deliberately left open. Narrowing them is the architecture epic's job.

## Module verification

`ApplicationModulesVerificationTest` runs `ApplicationModules.of(Application.class).verify()` against
the application's module graph (`@Tag("architecture")` so it runs in the `architecture-tests`
profile). New violations against the baseline allowlist (`src/test/resources/modulith-violation-baseline.txt`)
fail CI. The architecture epic owns emptying the baseline by either tightening `allowedDependencies`
or restructuring callsites into module SPIs.

## Security posture

The application is a **stateless OAuth2 resource server** (`Authorization: Bearer …`). Spring Security 7
flipped CSRF defaults for browser-form flows; the explicit `.csrf(csrf -> csrf.disable())` call on
both `SecurityFilterChain` beans (main + LLM proxy) is load-bearing.

`SecurityFilterChainArchitectureTest` locks this in at two levels:

- **Runtime slice**: introspects the resolved filter chain after Spring context boot, asserts
  no `CsrfFilter` is present on any chain, and asserts `JobTokenAuthenticationFilter` precedes
  `UsernamePasswordAuthenticationFilter` on the LLM-proxy chain.
- **Static drift**: ArchUnit rule asserting only `SecurityConfig` and `LlmProxySecurityConfig`
  may declare `SecurityFilterChain` beans.

## Rollback

Single PR; `git revert <merge sha>` followed by `mvn -f server/application-server/pom.xml verify`
restores the Boot 3.5.14 baseline. The 48h soak runbook in
`docs/operations/server-deps-boot4-soak.md` captures the deploy-side gate.
