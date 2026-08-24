---
applyTo: "server/src/test/**/*.java"
---
# Server tests

`server/AGENTS.md` owns the four tiers, the tag each one carries, the command that runs it, why
`-P'!quick'` is mandatory, and why an integration test's *filename* decides whether it ever runs. Read
it before your first run — a plain `mvn test` reports BUILD SUCCESS having run nothing.

What the tiers do not say:

- **One behaviour per test**, named `should[ExpectedBehavior]When[Condition]`, arranged
  Arrange-Act-Assert, with the minimum setup that reaches the behaviour. A test asserting three things
  reports one failure and hides the other two.
- **Tests run in parallel against a shared database.** Assume rows from other tests already exist:
  assert on the row you created, never on a count or on "the only" result, and never write cleanup
  that another test depends on having run.
- **A mocked repository cannot catch a wrong query.** Anything whose correctness lives in JPQL, a
  native `@Query`, or a Liquibase changelog needs a tier that talks to Postgres — `server/AGENTS.md`
  § Things that bite lists the shapes this has already cost.
