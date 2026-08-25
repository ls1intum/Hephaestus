# ADR 0032: Generated clients are a Maven build boundary

**Status:** Accepted
**Date:** 2026-08-25

## Context

The application compiled its handwritten sources together with 11,726 generated GitHub, GitLab, and
Outline transport classes. An application or test edit could therefore repeat generation and compile
the entire source set. A CI experiment that transferred the application's `target/classes` avoided
some repeated compilation but added a producer to the required workflow's critical path and treated
mutable build output as the reuse boundary.

ADR 0001 flattened the application into `server/` because the former nesting had no architectural
purpose. Generated clients now provide a concrete reason for a reactor: their schemas, generators,
dependencies, invalidation, and compiled output have a different lifecycle from application code.

## Decision

`server/pom.xml` is the reactor parent for two modules:

- `generated-clients` owns every GitHub and GitLab GraphQL input, the Outline OpenAPI inputs, their
  generator configuration, and the resulting JAR. GraphQL documents remain resources in that JAR
  because the application loads them at runtime.
- `application` owns the Spring Boot application, database migrations, and every server test. It
  consumes generated transports only through the generated-client dependency.

Generation is part of the generated module's standard Maven lifecycle. Tests run by default. The
build does not infer behavior from files under `target/`, skip tests through an incremental profile,
or transfer application class directories between jobs.

The generated JAR is configured for reproducible output and is the only server module eligible for
remote build reuse. The scheduled Server Phase Reference workflow verifies that two clean builds are
byte-for-byte identical.
Application output is deliberately excluded because handwritten code and resources change together
and because caching it would recreate the unsafe whole-application boundary.

## Consequences

- The first clean build still generates and compiles all clients. Later builds can reuse one immutable
  generated JAR locally and in CI without serializing verification behind a producer job.
- Changes to generated-client schemas, documents, templates, generator configuration, Java, or their
  compile dependencies invalidate that module. Application and test-only edits do not.
- Server commands continue to start at `server/`, but commands targeting the deployable select the
  `application` module.
- Source and output paths gain a module segment. CI, release tooling, developer scripts, IDE setup,
  and contributor documentation must use the reactor rather than reaching into a former monolithic
  `target/` tree.
- This supersedes ADR 0001's rejection of application nesting. The added path now represents an
  enforceable build and dependency boundary rather than an organizational wrapper.
