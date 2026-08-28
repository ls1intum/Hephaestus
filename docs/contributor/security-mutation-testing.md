---
sidebar_position: 9
title: Security mutation testing
---

# Security mutation testing

PIT exercises a curated set of security-boundary unit tests. It is advisory: a valid run proves the
analysis completed, not that the application is secure or that a mutation score is acceptable. It
does not replace integration tests, threat modelling, or review.

## Run the suite

The **Security mutation testing** workflow runs when a pull request changes a target, its tests, or
the suite's build inputs. It also runs monthly to detect toolchain drift and supports manual dispatch.
Run the same analysis locally with JDK 21:

```bash
pnpm run test:server:mutation
```

The command prepares reactor dependencies, compiles the target tests, and runs PIT. It fails if any
Maven phase fails, the report is missing or invalid, or PIT leaves a mutation in a technical or
incomplete state. The job summary reports timing and outcomes. HTML, XML, and Markdown reports are
written below the application module's `target/pit-reports` directory and uploaded by the workflow
even on failure.

Each workflow run has a 10-minute budget, with the command limited to eight minutes so failure
artifacts can still upload. Incremental analysis is disabled.

## Triage

The pull-request author investigates changed survivors and uncovered mutations. The affected server
CODEOWNERS review any accepted classification.

- Add a public-behavior test when a mutant exposes an unverified contract.
- Document equivalent or unproductive mutants instead of asserting private call order or
  implementation details.
- Remove unwired code instead of building mutation tests around it.
- Treat technical and incomplete statuses as an invalid run, never as killed mutations.

[Issue #1498](https://github.com/ls1intum/Hephaestus/issues/1498) records the suite's evaluation and
the decision to keep it non-required and advisory. Making it required needs a separate proposal
backed by reliable hosted runs and continued useful findings.

## Further reading

- [PIT Maven guide](https://pitest.org/quickstart/maven/)
- [Mutation testing at Google](https://testing.googleblog.com/2021/04/mutation-testing.html)
- [Thoughtworks Technology Radar](https://www.thoughtworks.com/radar/techniques/mutation-testing)
