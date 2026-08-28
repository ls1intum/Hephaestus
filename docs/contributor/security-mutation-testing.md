---
sidebar_position: 9
title: Security mutation testing
---

# Security mutation testing

The server uses PIT as an advisory check for eleven security-sensitive classes. It is not a
mutation-score gate and does not replace integration tests, threat modelling, or review.

## Run the suite

CODEOWNERS own triage. The advisory **Security mutation testing** workflow runs when a pull request
changes a target or its tests, and monthly to detect toolchain drift. It can also be dispatched manually
or run locally with JDK 21:

```bash
pnpm run test:server:mutation
```

The command reports preflight-compilation and PIT-goal wall time. It fails if Maven fails, the report is
missing or invalid, or PIT leaves any mutation in a technical or incomplete state. HTML, XML, and the
Markdown summary are available in the application target's `pit-reports` directory; the workflow uploads the
same directory.

Each workflow run has a 10-minute budget, with the PIT command limited to eight minutes so failure
artifacts still have time to upload. Incremental analysis is disabled so every run evaluates the full
target set.

## Triage

Classify survivors by observable behavior, not by score:

- Add a public-behavior test when a mutant exposes an unverified contract.
- Record equivalent or unproductive mutants; do not assert private call order or implementation detail.
- Remove unwired code instead of building mutation tests around it.
- Treat technical and incomplete statuses as an invalid run, never as killed mutations.

The evaluation for [issue #1498](https://github.com/ls1intum/Hephaestus/issues/1498) removed an unwired
issuer-discovery component, strengthened AAD framing and OAuth-state secret tests, and established a
10-minute runner budget. The decision is to run the suite automatically as a non-required advisory
check on relevant pull requests and once a month. Review that decision by 2026-11-30; remove the suite
if it does not keep finding actionable gaps at acceptable triage cost. A required gate needs a separate
proposal backed by stable runs and useful findings.

### Evaluation record

The 2026-08-28 evaluation used Linux x86-64, JDK 21, PIT 1.30.0, and disabled the Maven build cache.

| Measure | Original baseline | Expanded suite |
| --- | ---: | ---: |
| Generated | 203 | 392 |
| Killed | 137 | 370 |
| Survived | 18 | 8 |
| No coverage | 40 | 14 |
| Technical errors | 8 | 0 |

- A cold run before expansion used 58 seconds for compilation and 23 seconds for PIT. The expanded
  warm run used 3 seconds for compilation and 40 seconds for PIT.
- Approximately 90 minutes of triage found a fail-open excessive-encoding boundary, a sub-second
  OAuth-intent freshness bug, and missing contracts around cookie hardening, nonce reuse, origin
  canonicalization, impersonation problem responses, and webhook timestamp/signature boundaries.
- Rerunning the same 392 mutants after the expansion fixes moved the result from 362 killed and 21
  uncovered to 370 killed and 14 uncovered. GitLab signature verification reached 100% test strength
  without exclusions or private-method assertions.
- The eight survivors are equivalent or unproductive boundary/logging mutations. The fourteen
  uncovered mutations are defensive or outside the isolated target-test mapping.

## Further reading

- [PIT Maven guide](https://pitest.org/quickstart/maven/)
- [Mutation testing at Google](https://testing.googleblog.com/2021/04/mutation-testing.html)
- [Thoughtworks Technology Radar](https://www.thoughtworks.com/radar/techniques/mutation-testing)
- [OWASP SSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html)
