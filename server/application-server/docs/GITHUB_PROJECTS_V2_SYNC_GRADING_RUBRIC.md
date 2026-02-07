# GitHub Projects V2 Synchronization Implementation Grading Rubric

> **Philosophy**: This rubric is ruthlessly strict. An A+ represents exceptional engineering that could serve as a reference implementation. "Good enough" is a C. Most first implementations will score D or F in several categories until refined.

## Quick Reference

| Grade | Meaning | Typical Characteristics |
|-------|---------|------------------------|
| **A+** | Exceptional | Reference implementation quality; handles edge cases most developers never consider |
| **A** | Excellent | Production-ready; comprehensive but missing 1-2 advanced features |
| **B** | Good | Solid implementation with minor gaps; suitable for low-to-medium traffic |
| **C** | Adequate | Works but has clear deficiencies; technical debt present |
| **D** | Poor | Functional but unreliable; significant rework needed |
| **F** | Failing | Does not meet basic requirements; fundamentally flawed |

---

## 1. Data Model Quality

> Entity design, relationships, constraints, and schema hygiene.

### A+ (Exceptional)
- [ ] All entities use GitHub's node IDs as primary keys (not auto-generated IDs) to enable idempotent sync
- [ ] Deterministic ID generation strategy for entities without GitHub database IDs (bit-shifting approach like `BaseGitHubProcessor`)
- [ ] Comprehensive unique constraints on natural business keys (e.g., `project_id + item_id`, `field_id + option_id`)
- [ ] Proper cascade strategies: explicit `ON DELETE` behavior, no orphan leaks
- [ ] `@Version` optimistic locking on mutable entities to prevent lost updates
- [ ] Audit columns present: `created_at`, `updated_at`, `synced_at` with proper indexing
- [ ] Immutable value objects for derived/computed data (e.g., `@Immutable` annotation)
- [ ] Database CHECK constraints for enum fields and valid state transitions
- [ ] Soft-delete support with `deleted_at` column for entities that can be deleted upstream
- [ ] Junction tables have composite primary keys (not surrogate keys)
- [ ] All foreign keys have explicit indexes for JOIN performance
- [ ] Column types match GitHub data precisely (e.g., `TEXT` for markdown, proper `TIMESTAMP WITH TIME ZONE`)

### A (Excellent)
- [ ] All criteria above met except 1-2 advanced features (e.g., optimistic locking or soft-delete)
- [ ] Clear separation between sync metadata and domain data
- [ ] Proper nullable vs non-nullable column definitions matching GitHub API

### B (Good)
- [ ] Correct entity relationships with proper foreign keys
- [ ] Unique constraints on natural keys present
- [ ] Missing some audit columns or optimistic locking
- [ ] Cascade behavior mostly correct but not comprehensive

### C (Adequate)
- [ ] Basic entity structure correct but missing constraints
- [ ] No unique constraints on natural business keys (relying on ID only)
- [ ] Missing audit columns
- [ ] Some N+1 query risks due to eager loading choices

### D (Poor)
- [ ] Auto-generated IDs without idempotency strategy
- [ ] Missing or incorrect foreign key relationships
- [ ] No consideration for concurrent updates
- [ ] Schema allows invalid states (e.g., missing NOT NULL on required fields)

### F (Failing)
- [ ] Entities don't map to GitHub's data model
- [ ] Missing critical relationships (e.g., no link between ProjectItem and Project)
- [ ] No unique constraints anywhere
- [ ] Will fail with duplicate key errors or orphaned data in production

---

## 2. Sync Architecture

> Incremental sync, cursor persistence, transaction management, and data consistency.

### A+ (Exceptional)
- [ ] Incremental sync with cursor/timestamp persistence (survives restarts)
- [ ] Two-phase sync: initial full sync + incremental webhook-driven updates
- [ ] Cursor stored in dedicated table with repository/project scope isolation
- [ ] Transaction boundaries at the correct granularity (per-page or per-entity, not entire sync)
- [ ] Idempotent operations: re-running sync produces identical results
- [ ] Change detection: only updates when data actually changed (dirty checking)
- [ ] Conflict resolution strategy documented and implemented (e.g., "last write wins" with `updated_at`)
- [ ] Transactional outbox pattern for domain events (events committed with data)
- [ ] Support for sync state recovery: can resume from any checkpoint after failure
- [ ] Handles schema evolution: new fields in GitHub API don't break sync
- [ ] Parallel sync capability with proper isolation (multiple repositories simultaneously)
- [ ] Back-pressure mechanism: pauses sync when downstream can't keep up

### A (Excellent)
- [ ] Meets most A+ criteria but missing 1-2 advanced features (e.g., parallel sync or back-pressure)
- [ ] Clear sync state machine with documented states (IDLE, SYNCING, FAILED, etc.)
- [ ] Proper use of `@Transactional` with correct propagation levels

### B (Good)
- [ ] Incremental sync implemented but cursor persistence is basic
- [ ] Transaction boundaries reasonable but not optimized
- [ ] Idempotent for most operations
- [ ] Missing some edge case handling (e.g., deleted items during sync)

### C (Adequate)
- [ ] Full sync only (no incremental capability)
- [ ] Entire sync in single transaction (risky for large datasets)
- [ ] No cursor persistence (always starts from beginning)
- [ ] Idempotency not guaranteed

### D (Poor)
- [ ] No transaction management consideration
- [ ] Partial sync failures leave inconsistent state
- [ ] No way to track sync progress or resume
- [ ] Race conditions between sync and webhook handlers

### F (Failing)
- [ ] Sync corrupts data on re-run
- [ ] No transactional integrity
- [ ] Can create duplicates or orphans
- [ ] No error handling - exceptions bubble up and leave partial state

---

## 3. Error Handling

> Rate limiting, retries, graceful degradation, and resilience.

### A+ (Exceptional)
- [ ] Exponential backoff with jitter for all retries (prevents thundering herd)
- [ ] Respects GitHub's `Retry-After` header and rate limit headers
- [ ] Rate limit tracking per installation/scope (not global)
- [ ] Critical threshold detection: stops sync before exhausting rate limit
- [ ] Distinguishes transient vs permanent errors (see `GitHubExceptionClassifier` pattern)
- [ ] Circuit breaker pattern for persistent failures
- [ ] Graceful degradation: partial sync success is better than complete failure
- [ ] Dead letter queue for permanently failed items (manual review)
- [ ] Error classification into categories: RETRYABLE, PERMANENT, RATE_LIMITED, UNAUTHORIZED
- [ ] Structured error logging with context (entity ID, sync phase, attempt number)
- [ ] Alerting integration for critical errors (e.g., auth failures, repeated rate limits)
- [ ] Handles GitHub API deprecation warnings gracefully

### A (Excellent)
- [ ] Comprehensive retry logic with backoff
- [ ] Rate limit aware but missing some advanced features (e.g., circuit breaker)
- [ ] Good error logging and classification
- [ ] Handles most transient errors automatically

### B (Good)
- [ ] Basic retry logic implemented
- [ ] Respects rate limits but doesn't track proactively
- [ ] Error logging present but not structured
- [ ] Some errors handled, others propagate up

### C (Adequate)
- [ ] Simple retry (fixed count, no backoff)
- [ ] Rate limits handled reactively (after hitting limit)
- [ ] Generic error handling (catch-all)
- [ ] No distinction between error types

### D (Poor)
- [ ] No retry logic
- [ ] Rate limits cause failures
- [ ] Errors logged but not handled
- [ ] Single error fails entire sync

### F (Failing)
- [ ] Errors swallowed silently
- [ ] No rate limit handling (gets blocked by GitHub)
- [ ] Unhandled exceptions crash the service
- [ ] No logging of failures

---

## 4. Performance

> Pagination efficiency, query optimization, batch processing, and scalability.

### A+ (Exceptional)
- [ ] Cursor-based pagination with configurable page size (respects GitHub's 100 item limit)
- [ ] Uses `first`/`after` for forward pagination consistently
- [ ] Nested pagination handled correctly (e.g., project items within project)
- [ ] GraphQL query requests only needed fields (no over-fetching)
- [ ] Batch inserts/updates using `saveAll()` or native batch queries
- [ ] N+1 query prevention: uses JOIN FETCH or entity graphs for relationships
- [ ] Indexes defined for all query patterns (verified with EXPLAIN ANALYZE)
- [ ] Connection pooling properly configured for concurrent sync
- [ ] Lazy loading used appropriately (no `FetchType.EAGER` on collections)
- [ ] Projection DTOs for read operations (not full entities)
- [ ] Cache frequently accessed reference data (e.g., field definitions)
- [ ] Database statistics up-to-date for query planner
- [ ] Bulk upsert using `ON CONFLICT DO UPDATE` for high-volume sync

### A (Excellent)
- [ ] Efficient pagination implemented
- [ ] Good query optimization but missing some advanced techniques
- [ ] Batch operations used where appropriate
- [ ] Most indexes present

### B (Good)
- [ ] Pagination works correctly
- [ ] Some query optimization
- [ ] Batch processing present but not comprehensive
- [ ] Key indexes present, some missing

### C (Adequate)
- [ ] Basic pagination (may have edge cases)
- [ ] N+1 queries present but manageable
- [ ] Individual saves instead of batch
- [ ] Minimal indexing

### D (Poor)
- [ ] Inefficient pagination (loading all then filtering)
- [ ] Severe N+1 problems
- [ ] No batch processing
- [ ] Missing critical indexes

### F (Failing)
- [ ] No pagination (attempts to load all data at once)
- [ ] Queries cause timeouts on moderate datasets
- [ ] Will cause database performance issues
- [ ] Unusable at scale

---

## 5. Code Quality

> Following existing patterns, maintainability, documentation, and project conventions.

### A+ (Exceptional)
- [ ] Follows existing `gitprovider` package structure exactly
- [ ] Uses established patterns: `*SyncService`, `*Processor`, `*MessageHandler`
- [ ] Extends `BaseGitHubProcessor` for common operations
- [ ] Uses `GraphQlPaginationHelper` for pagination (not reinventing)
- [ ] DTOs are records with proper null handling (`@Nullable` annotations)
- [ ] GraphQL documents in `resources/graphql-documents/github/`
- [ ] Comprehensive Javadoc on public APIs with usage examples
- [ ] Clear separation: DTOs for API, Entities for persistence, Services for logic
- [ ] Uses project's `PostgresStringUtils.sanitize()` for string cleaning
- [ ] Configuration via `@ConfigurationProperties` (not hardcoded values)
- [ ] Logging follows project conventions (SLF4J, structured context)
- [ ] No code duplication - extracts common patterns to shared utilities
- [ ] Thread-safe design with documented concurrency guarantees

### A (Excellent)
- [ ] Follows patterns consistently with minor deviations
- [ ] Good documentation
- [ ] Clean separation of concerns
- [ ] Maintainable code structure

### B (Good)
- [ ] Patterns mostly followed
- [ ] Some documentation gaps
- [ ] Code is readable and understandable
- [ ] Minor inconsistencies with project style

### C (Adequate)
- [ ] Basic structure follows conventions
- [ ] Minimal documentation
- [ ] Code works but not elegant
- [ ] Some code duplication

### D (Poor)
- [ ] Doesn't follow established patterns
- [ ] No documentation
- [ ] Hard to understand code flow
- [ ] Significant duplication

### F (Failing)
- [ ] Completely different architecture from existing code
- [ ] Unmaintainable code
- [ ] Violates project conventions
- [ ] Would require rewrite to maintain

---

## 6. Event System

> Domain events, activity tracking, and audit trail.

### A+ (Exceptional)
- [ ] Domain events published for all significant state changes
- [ ] Events are immutable (follow `ActivityEvent` pattern)
- [ ] Idempotent event handling with deterministic event keys
- [ ] Events committed in same transaction as data changes (transactional outbox)
- [ ] Activity events integrate with existing `ActivityEvent` system
- [ ] Event types follow naming convention: `PROJECT_ITEM_CREATED`, `PROJECT_FIELD_UPDATED`, etc.
- [ ] Events contain sufficient context for replay/audit without entity lookup
- [ ] Event listener decoupling via Spring `@EventListener` or `ApplicationEventPublisher`
- [ ] Support for event replay (rebuilding state from event log)
- [ ] Dead letter handling for failed event processing
- [ ] Event versioning strategy for schema evolution
- [ ] Metrics/counters published for monitoring (items synced, errors, etc.)

### A (Excellent)
- [ ] Events published for key state changes
- [ ] Follows existing event patterns
- [ ] Good integration with activity system
- [ ] Missing some advanced features (replay, versioning)

### B (Good)
- [ ] Basic event publishing implemented
- [ ] Events are decoupled from sync logic
- [ ] Activity tracking present but limited
- [ ] Some event types missing

### C (Adequate)
- [ ] Minimal event publishing
- [ ] Direct coupling between sync and event handling
- [ ] No activity integration
- [ ] Audit trail incomplete

### D (Poor)
- [ ] Events only for some operations
- [ ] Events in separate transactions (consistency risk)
- [ ] No integration with existing systems
- [ ] Missing critical audit events

### F (Failing)
- [ ] No event system
- [ ] No audit trail
- [ ] Changes not traceable
- [ ] Violates audit requirements

---

## 7. Testing

> Coverage, integration tests, edge cases, and test quality.

### A+ (Exceptional)
- [ ] Unit tests for all business logic (>90% coverage on services)
- [ ] Integration tests against real GitHub API (like `AbstractGitHubLiveSyncIntegrationTest`)
- [ ] Repository tests with `@DataJpaTest` and test data builders
- [ ] Pagination edge cases tested: empty pages, single item, max items, interrupted sync
- [ ] Rate limit handling tested with mocked responses
- [ ] Error scenarios tested: network failures, malformed responses, partial failures
- [ ] Idempotency verified: running sync twice produces same result
- [ ] Concurrent sync tested for race conditions
- [ ] Performance tests for large datasets (optional but appreciated)
- [ ] Test fixtures follow `GitHubTestFixtureService` patterns
- [ ] GraphQL response mocking for unit tests
- [ ] Webhook handling tests with realistic payloads
- [ ] Mutation testing or property-based testing for complex logic

### A (Excellent)
- [ ] Comprehensive test coverage
- [ ] Integration tests present
- [ ] Edge cases covered
- [ ] Missing some advanced testing (mutation, performance)

### B (Good)
- [ ] Good unit test coverage
- [ ] Some integration tests
- [ ] Basic edge cases covered
- [ ] Test quality is reasonable

### C (Adequate)
- [ ] Basic happy path tests
- [ ] Limited edge case coverage
- [ ] No integration tests
- [ ] Tests exist but gaps present

### D (Poor)
- [ ] Minimal test coverage
- [ ] Only simple cases tested
- [ ] Tests are brittle or flaky
- [ ] Key paths untested

### F (Failing)
- [ ] No tests
- [ ] Tests that don't actually verify behavior
- [ ] Tests that always pass
- [ ] Untestable code structure

---

## Scoring Guidelines

### Overall Grade Calculation

1. **Calculate category grades** using the criteria above
2. **Weight the categories**:
   - Data Model Quality: 15%
   - Sync Architecture: 20%
   - Error Handling: 15%
   - Performance: 15%
   - Code Quality: 15%
   - Event System: 10%
   - Testing: 10%

3. **Convert to numeric scale**:
   - A+ = 4.3, A = 4.0, A- = 3.7
   - B+ = 3.3, B = 3.0, B- = 2.7
   - C+ = 2.3, C = 2.0, C- = 1.7
   - D+ = 1.3, D = 1.0, D- = 0.7
   - F = 0.0

4. **Calculate weighted average**

### Grade Interpretation

| Weighted Score | Grade | Verdict |
|---------------|-------|---------|
| 4.0+ | A/A+ | Ship it - exemplary work |
| 3.5-3.9 | A-/B+ | Ship with minor revisions |
| 3.0-3.4 | B | Acceptable for initial release, needs iteration |
| 2.5-2.9 | B-/C+ | Significant improvements needed before shipping |
| 2.0-2.4 | C | Not production-ready, major rework required |
| 1.5-1.9 | C-/D+ | Fundamental issues, consider rewrite |
| Below 1.5 | D/F | Start over with proper design |

### Automatic Grade Caps

Certain critical failures cap the maximum possible grade:

| Issue | Maximum Grade |
|-------|--------------|
| No idempotency strategy | C |
| No transaction management | C |
| No rate limit handling | C |
| No error handling | D |
| No pagination | D |
| Data corruption on re-sync | F |
| No tests | D |

---

## References

### GitHub API Documentation
- [GitHub Projects V2 GraphQL API](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/using-the-api-to-manage-projects)
- [GraphQL Pagination](https://docs.github.com/en/graphql/guides/using-pagination-in-the-graphql-api)
- [Rate Limits](https://docs.github.com/en/graphql/overview/rate-limits-and-query-limits-for-the-graphql-api)

### Design Patterns
- [Data Synchronization Patterns](https://hasanenko.medium.com/data-synchronization-patterns-c222bd749f99)
- [Exponential Backoff with Jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/)
- [Domain Events in DDD](https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/domain-events-design-implementation)

### Project-Specific Patterns
- `BaseGitHubProcessor` - Entity creation and relationship management
- `GraphQlPaginationHelper` - Pagination with rate limit tracking
- `ActivityEvent` - Immutable event pattern
- `ExponentialBackoff` - Retry configuration
- `GitHubExceptionClassifier` - Error classification

---

*Last Updated: 2026-02-02*
*Version: 1.0*
