# Application Server

Spring Boot 3.5 backend providing the REST API for Hephaestus.

## Commands

| Task | Command |
|------|---------|
| Run locally | `./mvnw spring-boot:run` |
| Unit tests | `./mvnw test` |
| Integration tests | `./mvnw verify` |
| Live GitHub tests | `./mvnw test -Plive-tests` |
| Format | `npm run format:java` |
| Generate OpenAPI | `npm run generate:api:application-server:specs` |

## Boundaries

### Always
- Run `./mvnw test` before committing
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
- Hand-edit generated files in `intelligenceservice/`

## Project Structure

```
src/main/java/de/tum/in/www1/hephaestus/
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
├── activity/                     # Activity tracking, bad practice detection
├── mentor/                       # AI mentor proxy to intelligence-service
├── profile/                      # User profiles
├── notification/                 # Email and Slack messaging
└── intelligenceservice/          # Generated AI service client (DO NOT EDIT)
```

## Architecture Patterns

### Workspace Multi-Tenancy

All workspace-scoped controllers use `@WorkspaceScopedController`:

```java
@WorkspaceScopedController
@RequireAtLeastWorkspaceAdmin  // or @RequireWorkspaceOwner
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
    String avatarUrl  // optional - no annotation
) {
    public static UserDTO from(User entity) {
        return new UserDTO(entity.getId(), entity.getLogin(), entity.getAvatarUrl());
    }
}
```

## Testing

### Test Tiers (JUnit 5 Tags)

| Tag | Purpose | Base Class | Command |
|-----|---------|------------|---------|
| `@Tag("unit")` | Fast, no Spring context | `BaseUnitTest` | `./mvnw test` |
| `@Tag("integration")` | Full Spring Boot + Testcontainers | `BaseIntegrationTest` | `./mvnw verify` |
| `@Tag("live")` | Real GitHub API calls | - | `./mvnw test -Plive-tests` |

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
        webTestClient.get()
            .uri("/workspaces/{slug}/resource", "test-workspace")
            .headers(TestAuthUtils.withAdminUser())
            .exchange()
            .expectStatus().isOk();
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

| Annotation | Use For |
|------------|---------|
| `@Service` | Business logic |
| `@Repository` | Data access (extends JpaRepository) |
| `@RestController` | REST endpoints |
| `@WorkspaceScopedController` | Workspace-scoped REST endpoints |
| `@Transactional` | Service layer only (never controllers) |
| `@ConfigurationProperties` | Type-safe configuration |

### Lombok

```java
@Service
@RequiredArgsConstructor  // Constructor injection
@Slf4j                    // Logging
public class UserService {
    private final UserRepository userRepository;  // injected via constructor
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
2. Run `npm run db:draft-changelog`
3. Review and prune the generated changelog to minimal deltas
4. Rename to `<timestamp>_<description>.xml`
5. Run `npm run db:generate-erd-docs` to update ERD

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

| Mistake | Fix |
|---------|-----|
| `@Transactional` on controller | Move to service layer |
| Missing test tag | Add `@Tag("unit")` or `@Tag("integration")` |
| N+1 query | Use `JOIN FETCH` in `@Query` |
| Edited generated client | Discard, run `npm run generate:api:intelligence-service:client` |
