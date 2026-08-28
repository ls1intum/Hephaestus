---
sidebar_position: 9
title: Security mutation testing
---

# Security mutation testing

The server uses PIT as an advisory check for five security-sensitive classes. It is not a mutation-score
gate and does not replace integration tests, threat modelling, or review.

## Run the suite

CODEOWNERS own triage. Run the manual **Security mutation testing** workflow when a target class or its
tests change, or run locally with JDK 21:

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
10-minute runner budget plus a 30-minute triage budget. The decision is to keep the suite manual and
advisory. A required gate needs a separate proposal backed by stable runs and useful findings.

### Evaluation record

The 2026-08-28 evaluation used Linux x86-64, JDK 21, PIT 1.30.0, and disabled the Maven build cache.

| Measure | Baseline | Hardened |
| --- | ---: | ---: |
| Generated | 203 | 156 |
| Killed | 137 | 148 |
| Survived | 18 | 5 |
| No coverage | 40 | 3 |
| Technical errors | 8 | 0 |

- Preflight compilation: 58 seconds from invalidated application outputs.
- PIT goal: 23 seconds; PIT reported 19 seconds of analysis.
- Human triage: approximately 45 minutes.
- Actionable survivors: two found and fixed; none remain.
- Equivalent or unproductive results: eight remain—five survivors and three uncovered defensive or
  out-of-scope paths.
- Tests improved: eight behavioral cases covering AAD framing, u16 bounds, OAuth-state secret
  selection and entropy, canonical IPv4 bounds, and excessive percent encoding.

## Further reading

- [PIT Maven guide](https://pitest.org/quickstart/maven/)
- [Mutation testing at Google](https://testing.googleblog.com/2021/04/mutation-testing.html)
- [Thoughtworks Technology Radar](https://www.thoughtworks.com/radar/techniques/mutation-testing)
- [OWASP SSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html)
