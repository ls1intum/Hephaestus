# Server

Spring Boot 4 backend providing the REST API for Hephaestus.

## Local development loop

From the repo root: `pnpm dev`. It brings compose up with healthchecks, then runs `mvn spring-boot:run` + `pnpm --filter webapp dev` in parallel.

### Conventions

- **No devtools.** Hot reload uses JVM HotSwap via the IDE — IntelliJ's Spring Boot run config with *Update Classes and Resources* on save (or *On Frame Deactivation*). Method-body edits reload; signature changes, new methods, and `@Configuration` edits require a full restart ([Spring Boot 4 hot-swapping ref](https://docs.spring.io/spring-boot/how-to/hotswapping.html)).
- **`ddl-auto: validate`** for local. Liquibase owns DDL. If the validator fails on boot, your DB has drifted — recreate with `docker compose down -v && docker compose up -d`.
- **`BufferingApplicationStartup`** is wired in `Application.main()`. With `app.profiles=local`, `GET /actuator/startup` returns the timeline; `StartupBudgetIntegrationTest` catches per-step regressions in CI.
- **Maven build cache** is opt-in: `-Dmaven.build.cache.enabled=true` (CI does this). Default off until [MBUILDCACHE-118](https://issues.apache.org/jira/browse/MBUILDCACHE-118) lands.
- **Pre-commit hook** (optional, install manually): `printf '#!/usr/bin/env sh\npnpm run format:check\n' > .husky/pre-commit && chmod +x .husky/pre-commit`. The existing `pre-push` hook runs the heavier `format:check && check` chain.

## Commands

| Task              | Command                                          |
| ----------------- | ------------------------------------------------ |
| Run full stack    | `pnpm dev` (from repo root)                      |
| Run server only   | `mvn spring-boot:run` (in `server/`) |
| Unit tests        | `mvn test`                                       |
| Integration tests | `mvn verify`                                     |
| Live GitHub tests | `mvn test -Plive-tests`                          |
| Format            | `pnpm run format:java`                           |
| Generate OpenAPI  | `pnpm run generate:api:application-server:specs` |
| Startup timeline  | `curl http://localhost:8080/actuator/startup`    |
| Build prod image  | `mvn spring-boot:build-image`                    |

### Container image

Built with Paketo Cloud Native Buildpacks (Application CDS enabled); `pom.xml` `<image>` pins builder + run image by digest. See `docs/admin/buildpacks-cds-decision.md`. No `Dockerfile` — local builds use `mvn spring-boot:build-image`.

### JPA conventions

`EntityManager`: inject as `@PersistenceContext` field (see `WorkspaceMembershipService`, `GitHubUserProcessor`). All other deps: constructor injection.

### Optional integrations

`SlackAppConfig`, `PosthogClient` are gated by `@ConditionalOnProperty`. Tolerant consumers (`AccountService`, `LeaderboardTaskScheduler`) take `ObjectProvider<T>`. `SlackMessageService` injects `App` directly on purpose: `notification.enabled=true` with `slack.token` unset must crash context refresh, not silently no-op cron ticks.

### Removing Liquibase changesets

Liquibase 5.x logs a warning but continues when `DATABASECHANGELOG` references unknown IDs — deleting a changeset is safe **only** when its schema effect is enforced by a later changeset (e.g. a NOT NULL constraint makes its own backfill validator obsolete). Never delete a changeset whose effect isn't enforced elsewhere.

### Port Conflicts (OpenAPI Generation)

The `generate:api:application-server:specs` script (springdoc) starts the app on port **8080** to download the spec. If port 8080 is occupied (e.g. by Coolify proxy), override with environment variables — **never stop other services**:

```bash
SERVER_PORT=8090 MANAGEMENT_SERVER_PORT=8092 mvn verify -DskipTests=true -Dapp.profiles=specs
```

Then regenerate the client:

```bash
pnpm run generate:api:application-server:client
```

## Boundaries

### Always

- Run `mvn test` before committing
- Use constructor injection (via `@RequiredArgsConstructor`)
- Return `ResponseEntity` with proper status codes
- Tag tests appropriately (`@Tag("unit")`, etc.)

### Ask First

- Database schema changes (migrations)
- Security configuration changes
- Adding new dependencies to `pom.xml`
- Modifying workspace authorization logic

### Never

- Commit credentials or API keys
- Use `System.out.println()` (use `@Slf4j` logging)
- Skip tests without documented reason

## Project Structure

```
src/main/java/de/tum/cit/aet/hephaestus/
├── Application.java              # Entry point (@SpringBootApplication)
├── config/                       # @Configuration beans
├── workspace/                    # Multi-tenant workspace management
├── gitprovider/                  # GitHub API integration
│   ├── common/                   # Base entities, converters
│   ├── pullrequest/              # PR entity, sync service
│   ├── issue/                    # Issue entity (PR inherits from Issue)
│   ├── user/                     # GitHub user sync
│   ├── team/                     # Team management
│   └── sync/                     # Data synchronization orchestration
├── leaderboard/                  # Scoring, rankings, league points
├── activity/                     # Activity tracking (XP, leaderboard gamification)
├── mentor/                       # AI mentor (in-process Pi agent)
├── profile/                      # User profiles
└── notification/                 # Email and Slack messaging
```

## Architecture Patterns

### Workspace Multi-Tenancy

All workspace-scoped controllers use `@WorkspaceScopedController`:

```java
@WorkspaceScopedController
@RequireAtLeastWorkspaceAdmin // or @RequireWorkspaceOwner
public class MyController {

    @GetMapping("/my-resource")
    public ResponseEntity<MyDTO> get(WorkspaceContext ctx) {
        // ctx.workspace(), ctx.slug() available
    }
}
```

### Entity Inheritance

`Issue` uses SINGLE_TABLE inheritance with `PullRequest` as subclass:

```java
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "issue_type")
public class Issue extends BaseGitServiceEntity { ... }

@Entity
@DiscriminatorValue("PULL_REQUEST")
public class PullRequest extends Issue { ... }
```

### DTOs and Mappers

- Use Java records for DTOs
- Annotate required fields with `@NonNull` (from `org.springframework.lang`)
- Keep mapping logic in dedicated `*Mapper` classes or static factory methods

```java
public record UserDTO(
    @NonNull Long id,
    @NonNull String login,
    String avatarUrl // optional - no annotation
) {
    public static UserDTO from(User entity) {
        return new UserDTO(entity.getId(), entity.getLogin(), entity.getAvatarUrl());
    }
}
```

## Testing

### Test Tiers (JUnit 5 Tags)

| Tag                   | Purpose                           | Base Class            | Command                 |
| --------------------- | --------------------------------- | --------------------- | ----------------------- |
| `@Tag("unit")`        | Fast, no Spring context           | `BaseUnitTest`        | `mvn test`              |
| `@Tag("integration")` | Full Spring Boot + Testcontainers | `BaseIntegrationTest` | `mvn verify`            |
| `@Tag("live")`        | Real GitHub API calls             | -                     | `mvn test -Plive-tests` |

### Test Structure (AAA Pattern)

```java
@Test
void shouldReturnUserWhenValidIdProvided() {
    // Arrange
    var user = createTestUser();
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    // Act
    var result = userService.findById(1L);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().getLogin()).isEqualTo("testuser");
}
```

### Test Naming

Use `should[ExpectedBehavior]When[Condition]`:

- `shouldReturnUserWhenValidIdProvided`
- `shouldThrowExceptionWhenUserNotFound`
- `shouldCreateWorkspaceWhenValidRequest`

### Integration Test Base

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldReturn200WhenAuthorized() {
        webTestClient
            .get()
            .uri("/workspaces/{slug}/resource", "test-workspace")
            .headers(TestAuthUtils.withAdminUser())
            .exchange()
            .expectStatus()
            .isOk();
    }
}
```

### Test Principles

- Single responsibility: one behavior per test
- Independent: no hidden dependencies between tests
- Repeatable: consistent setup, deterministic data
- Fast execution: tests may run in parallel
- Avoid required cleanup: assume data from previous tests may exist

## Code Style

### Annotations

| Annotation                   | Use For                                |
| ---------------------------- | -------------------------------------- |
| `@Service`                   | Business logic                         |
| `@Repository`                | Data access (extends JpaRepository)    |
| `@RestController`            | REST endpoints                         |
| `@WorkspaceScopedController` | Workspace-scoped REST endpoints        |
| `@Transactional`             | Service layer only (never controllers) |
| `@ConfigurationProperties`   | Type-safe configuration                |

### Lombok

```java
@Service
@RequiredArgsConstructor // Constructor injection
@Slf4j // Logging
public class UserService {

    private final UserRepository userRepository; // injected via constructor
}
```

Use: `@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Slf4j`, `@Builder`
Avoid: `@Data` (use granular annotations instead)

### JPA Best Practices

- Use `FetchType.LAZY` for associations by default
- Use `@Query` with `JOIN FETCH` to avoid N+1 problems
- Never expose entities directly in controllers; use DTOs
- Use `@Transactional(readOnly = true)` for read operations

### Logging

```java
@Slf4j
@Service
public class SyncService {

    public void syncRepository(String repoName) {
        log.info("Starting sync for repository: {}", repoName);
        // ... work ...
        log.debug("Sync completed, processed {} items", count);
    }
}
```

- Use parameterized logging (`{}` placeholders)
- Never log sensitive data (tokens, passwords)
- Include context (workspace, user, request ID)

## Database (Liquibase)

Migrations live in `src/main/resources/db/changelog/`.

### Adding Schema Changes

1. Modify JPA entities
2. Run `pnpm run db:draft-changelog`
3. Review and prune the generated changelog to minimal deltas
4. Rename to `<timestamp>_<description>.xml`
5. Run `pnpm run db:generate-erd-docs` to update ERD

### Changelog Format

```xml
<changeSet id="1234567890123_add_user_email" author="developer">
    <addColumn tableName="users">
        <column name="email" type="varchar(255)"/>
    </addColumn>
</changeSet>
```

## Security

### Authorization Annotations

```java
@RequireAtLeastWorkspaceAdmin   // ADMIN or OWNER role
@RequireWorkspaceOwner          // OWNER role only
@RequireMentorAccess            // mentor_access claim in JWT
```

### Public vs Protected

- `GET` endpoints on workspace resources: filtered by membership/visibility
- `POST/PUT/DELETE`: require authentication
- Mentor routes: require `mentor_access` JWT claim

## API Conventions

### Error Responses (RFC 7807)

All errors return `ProblemDetail`:

```java
@ExceptionHandler(EntityNotFoundException.class)
public ProblemDetail handleNotFound(EntityNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
}
```

### Resource-Oriented Design

- Use HTTP methods for lifecycle: `PATCH /workspaces/{slug}/status`
- Avoid RPC-style verbs in URLs
- Return consistent error payloads via `@RestControllerAdvice`

## Common Mistakes

| Mistake                        | Fix                                                             |
| ------------------------------ | --------------------------------------------------------------- |
| `@Transactional` on controller | Move to service layer                                           |
| Missing test tag               | Add `@Tag("unit")` or `@Tag("integration")`                     |
| N+1 query                      | Use `JOIN FETCH` in `@Query`                                    |
