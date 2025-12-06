# API Error Handling Guidelines

This document explains how we use Spring Boot's `@RestControllerAdvice` + `ProblemDetail` to keep our REST APIs predictable across services.

## Why `RestControllerAdvice`?

Industry guidance for Spring Boot 3 in 2025 recommends centralizing REST exception handling through `@RestControllerAdvice` so every controller returns a single, structured schema (see [BootcampToProd, 2025](https://bootcamptoprod.com/spring-boot-restcontrolleradvice-annotation/) and the Spring Boot best-practices roundups on [Medium](https://medium.com/towardsdev/stop-the-stacktrace-chaos-exception-handling-in-spring-boot-2025-best-practices-guide-b8c662f56e00)). The advantages align with what we want in Hephaestus:

- **Consistency**: Every error comes back as RFC-7807 `application/problem+json` regardless of which controller threw it.
- **Security**: We decide exactly what detail leaves the server, instead of leaking stack traces.
- **Reduced duplication**: Controllers focus on domain logic and throw meaningful exceptions; advice classes translate them into HTTP semantics.

## How `WorkspaceControllerAdvice` works

1. `@RestControllerAdvice(basePackages = "de.tum.in.www1.hephaestus.workspace")` scopes the advice to workspace endpoints only, but covers the entire workspace package (controllers for repositories, labels, lifecycle, etc.). Consistency is guaranteed because every route in that package flows through one mapper.
2. Each `@ExceptionHandler` method matches a domain exception (for example `EntityNotFoundException`) and returns a configured `ProblemDetail` object. Spring automatically serializes it to JSON with the proper status code.
3. The private helpers sanitize exception messages via `LoggingUtils` before returning them so we never leak stack traces or SQL fragments. Any unexpected `IllegalStateException` is treated as a server error and logged, while domain-specific violations (slug conflicts, lifecycle issues) have their own exception classes so the advice can return the right HTTP semantics.
4. Controllers simply throw exceptions (or let services throw) and never build ad-hoc `Map.of("error", ...)` responses again.

## Rolling the pattern out elsewhere

When you touch another REST controller:

1. **Identify the domain-specific exceptions** the controller or underlying services already throw.
2. **Create a dedicated advice class** in the same package (or a shared module if multiple controllers share the same error model) and annotate it with `@RestControllerAdvice(basePackages = "your.package")` so every controller in that bounded context reuses the mapper.
3. **Map exceptions to HTTP semantics**:
   - Validation or invariant violations → `400 Bad Request`
   - Missing resources → `404 Not Found`
   - Conflicts (duplicates, state transitions) → `409 Conflict`
   - Authorization issues should still bubble up to Spring Security so we get `401/403` uniformly.
4. **Return `ProblemDetail` everywhere**. Include a user-actionable `title` plus a `detail` sourced from the exception where it is safe to expose.
5. **Document the mapping** (update this file or the feature README) so clients know what to expect.

### Shared advice for multiple controllers

If controllers reuse the same exception hierarchy (e.g., Git provider controllers), point `@RestControllerAdvice` at the shared base package so every endpoint in that bounded context inherits the mapper. Keep advice classes focused—too many unrelated handlers in a single class become hard to maintain.

## Validation errors deserve structure, too

Spring Boot 3 surfaces method-argument validation failures as `MethodArgumentNotValidException` (body binding) or `ConstraintViolationException` (query/path parameters). Best-practice guides such as [codecentric’s deep dive into RFC 7807/RFC 9457](https://www.codecentric.de/en/knowledge-hub/blog/charge-your-apis-volume-19-understanding-problem-details-for-http-apis-a-deep-dive-into-rfc-7807-and-rfc-9457) and the 2025 Spring exception-handling roundups on [Medium](https://towardsdev.com/stop-the-stacktrace-chaos-exception-handling-in-spring-boot-2025-best-practices-guide-b8c662f56e00) recommend turning both into structured JSON so clients can highlight the right fields. Our convention:

- Use the same advice class to `@ExceptionHandler` both exception types.
- Return `ProblemDetail` with `title = "Validation failed"`, `status = 400`, and a short descriptive `detail`.
- Attach a machine-friendly `errors` map via `problem.setProperty("errors", …)` where each key is the offending field/parameter (we strip method prefixes so only the field name remains) and the value is a list of human-readable messages.

### Path-variable validation

Slug parameters across `WorkspaceController` use the shared `@WorkspaceSlug` constraint (backed by `WorkspaceSlugValidator`) and the controller is annotated with `@Validated`. Invalid slugs therefore raise a `ConstraintViolationException`, get picked up by the advice, and return the same `errors` map shape as body validation failures.

## Testing expectations

- Add WebTestClient or MockMvc tests that trigger the exception path and assert the `ProblemDetail` body.
- Cover the advice indirectly via integration tests so we catch serialization regressions when upgrading Spring Boot.

Following these steps ensures we remain aligned with current Spring Boot recommendations while keeping our API contracts brutally consistent for every consumer.
