# Testing Guide

## Quick Start

**Essential workflow for writing tests:**

1. **Choose test type** based on what you're testing

2. **Run tests:**

```bash
# Unit tests only (fast)
mvn test

# All tests including integration
mvn verify
```

3. **Extend appropriate base class:**

```java
// Fast unit test
class MyServiceTest extends BaseUnitTest { }

// Integration test with Spring context + database
class MyServiceIntegrationTest extends BaseIntegrationTest { }
```

```{warning}
**⚠️ NAMING MATTERS**: Use `*Test.java` for unit tests, `*IntegrationTest.java` for integration tests. Maven plugins depend on this.
```

## Why Testing?

Tests catch bugs before production, enable safe refactoring, and document expected behavior. Our setup provides fast feedback with minimal configuration overhead.

## Development Workflow

### 1. Writing Unit Tests

- Extend `BaseUnitTest` for isolated component testing
- Use `@Mock` and `@InjectMocks` for dependencies
- Test runs in < 1 second with no Spring context

```java
class UserServiceTest extends BaseUnitTest {
    @Mock private UserRepository repository;
    @InjectMocks private UserService service;

    @Test @DisplayName("Should validate email format")
    void shouldValidateEmailFormat() {
        // Fast isolated test
    }
}
```

### 2. Writing Integration Tests

- Extend `BaseIntegrationTest` for component interaction testing
- Uses shared PostgreSQL container (60-75% faster than per-test containers, but still much slower than unit tests)
- Full Spring context with real database

```java
class GitHubLabelMessageHandlerIntegrationTest extends BaseIntegrationTest {
    @Test @DisplayName("Should persist label from webhook")
    void shouldPersistLabelFromWebhook(@GitHubPayload("label.created") GHEventPayload.Label payload) {
        // Test with real webhook data
    }
}
```

## GitHub Webhook Testing

Use real webhook data captured from the `HephaestusTest` GitHub organization:

**Available webhook examples:**

- `label.created.json`, `label.edited.json`, `label.deleted.json`
- `repository.created.json`, `create.json`, `push.json`
- Export more examples as needed on demand

**Adding new webhook examples:**

```bash
# Extract new webhook examples from NATS stream
python3 scripts/nats_extract_examples.py
```

The `@GitHubPayload("label.created")` annotation automatically loads real GitHub webhook JSON from `src/test/resources/github/label.created.json` into your test method.

## Maven Commands

```bash
# Fast development cycle
mvn test                              # Unit tests only

# Full test suite
mvn verify                           # All tests + packaging

# Specific test
mvn test -Dtest=UserServiceTest       # Single test class
mvn test -Dtest=UserServiceTest#shouldValidateEmail  # Single method

# Test categories (when implemented)
mvn test -Dgroups=unit               # Unit tests only
mvn test -Dgroups=integration        # Integration tests only
```
