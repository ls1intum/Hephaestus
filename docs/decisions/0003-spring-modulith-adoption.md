# ADR 0003: Spring Modulith 2.0 adoption with pragmatic shared kernels

**Status:** Accepted
**Date:** 2026-05-20
**Authors:** Server foundations epic (#1097)

## Context

The server has 18 top-level packages and a growing population of cross-package
dependencies. ArchUnit alone (17 tests) catches surface-level rule violations but
doesn't have a first-class "module" concept; cross-internal-package coupling
sneaks through. Spring Modulith 2.0 (GA November 2025) ships
`@ApplicationModule`, named interfaces, `Type.OPEN`, and a built-in cycle detector
that runs as JUnit + ArchUnit under the hood.

Earlier draft plans speculated narrow named interfaces (`workspace::context`,
`practices::{spi,review,finding,model}`). The first real `ApplicationModules.of(...).verify()`
run on the existing codebase surfaced ~250 uses of `workspace.context.WorkspaceContext`
from feature modules, ~100 uses of `practices.{model,review,finding}` internals from
`agent`, plus several root-package cycles — overwhelming evidence that those packages
ARE shared kernels in practice, not modules with narrow APIs.

## Decision drivers

- Catch architectural drift at PR time (cycles, internal-package leaks)
- Generate diagrams as CI artifacts (PlantUML / C4 / Module Canvas)
- Don't over-engineer named-interface boundaries that contradict actual usage
- Stay close to Modulith idiom — future framework features assume the same shape

## Considered options

1. **Bare `@ApplicationModule` on every package; OPEN for shared kernels** —
   pragmatic, matches actual usage, ships fast.
2. **Named interfaces narrowing every cross-module API** — architecturally pure;
   requires ~30 new `@NamedInterface` declarations and a sub-package reorganization;
   evidence says the codebase doesn't actually have narrow APIs today.
3. **No Modulith adoption, keep ArchUnit only** — saves a starter dependency but
   misses the cycle gate, the diagrams, and the future framework features.

## Decision

Option 1. Annotate every top-level package with `@ApplicationModule`. Mark the four
empirically-shared kernels as `Type.OPEN`:

- `core` (logging, exceptions, security, tenancy, runtime)
- `config` (cross-cutting Spring `@Configuration` grab-bag)
- `gitprovider` (47 entities, 14 SPI interfaces — already inverted from feature modules)
- `workspace` (multi-tenancy primitives; consumed everywhere)
- `practices` (`model`, `review`, `finding` used widely by `agent`)
- `activity` (`ActivityEventType` referenced from `achievement` via generics)
- `integrations` (`PosthogClient` used cross-module)

Wire `ModulithVerificationTest` into the architecture surefire group; generate
diagrams under `server/target/modulith-docs/` as CI artifacts (not committed to git —
avoids review churn).

## Consequences

- Every PR runs `ApplicationModules.of(HephaestusApplication.class).verify()` (~80s
  warm).
- Adding a new module is one `package-info.java` with `@ApplicationModule`.
- Cycles introduced in future PRs fail the build with specific source/target package
  references — no detective work needed.
- "Type.OPEN as shared kernel" is honest about today's coupling. Future epics can
  narrow `workspace`, `practices`, `activity`, `integrations` once there's field
  evidence that narrowing buys specific value (don't speculatively decompose).
- Root-package cycles (Application.java + WebConfig.java + SecurityConfig.java +
  SecurityUtils.java) are accepted today because moving them to `core/` is more
  invasive than the cycle's actual harm. Future cleanup tracked separately.

## Revisit trigger

A new cycle that lands in CI surfaces a coupling pattern not covered by the current
OPEN-or-named-interface choice; or Spring Modulith 2.x introduces a deployment-unit
concept that supersedes the role-flag pattern in ADR 0005.
