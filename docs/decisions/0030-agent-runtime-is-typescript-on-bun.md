# ADR 0030: The agent runtime is TypeScript on Bun, with no Node in the sandbox

**Status:** Accepted
**Date:** 2026-08-22
**Authors:** Agent runtime 1.0

## Context

The sandbox runtime — the practice-review runner, the mentor runner, their shared sidecars, the
precompute runner and every per-practice precompute script — was JavaScript executed by Node inside
the agent image. Three facts forced a decision:

1. **None of it was type-checked.** 3,161 lines of `.mjs` were checked by nothing: no `allowJs`, no
   `checkJs`, no `// @ts-check`. The Pi SDK ships 140 `.d.ts` files (8,097 lines) and we were
   compiling against none of them, because the SDK was installed only into the image and was not a
   dependency of the repository.
2. **None of it was linted or formatted.** Biome was scoped to `webapp/`, so three indent styles
   coexisted in one tree while `.editorconfig` had declared a fourth.
3. **The image carried two runtimes.** Node ran the runner; Bun already had to be installed to run
   the precompute scripts. Every agent image pull paid for both.

The absence of type-checking was not theoretical. Turning it on surfaced four defects that both test
suites had been green through for months, including a mentor that never received its own system
prompt and a review result path that could not succeed at all (see § Consequences).

## Decision drivers

- **One runtime in the sandbox.** Two interpreters is an unforced supply-chain and size cost.
- **No build step.** The runner is staged into the sandbox as a classpath resource and executed in
  place; anything requiring compilation would put a build artifact between the source we review and
  the code that runs.
- **Types must come from the SDK, not from us.** A hand-rolled shape of someone else's API is a
  second source of truth that drifts silently.
- **The pin must stay honest.** The image pins Bun by version *and* per-arch sha256, and Renovate
  must keep working.

## Decision

The agent runtime is TypeScript, executed directly by Bun. `PiRuntimeFactory` invokes `bun`, and the
image contains no Node and no npm — the Pi SDK is installed with `bun add`, and the build asserts
that neither binary survives.

Type-checking, linting and formatting cover the whole tree: `tsconfig.agents.json` (strict, plus
`noUncheckedIndexedAccess`) with the Pi SDK as a devDependency pinned to the version the image
installs, and the root `biome.jsonc`.

### Consequences we accepted deliberately

- **Bun ignores unknown CLI flags silently.** V8 flags are therefore forbidden: `--no-warnings` is
  inert and `--max-old-space-size` would be *accepted and ignored*, so a heap cap would appear to
  exist while doing nothing. The mentor's per-process heap ceiling is gone; its bound is now the
  sandbox memory limit, which caps the whole process rather than one heap generation. `--smol` and
  `--expose-gc` both work under Bun and are kept.
- **The jemalloc `LD_PRELOAD` and `MALLOC_CONF` tuning is removed.** Bun uses mimalloc, so the
  tuning no longer governs the heap, and `SandboxEnvBlocklist` strips `LD_PRELOAD` anyway.
- **`rootDirs` and `allowImportingTsExtensions` are load-bearing**, not conveniences: the runner
  imports siblings as `./x.ts` because that is what Bun resolves, and the precompute trees are two
  directories that a runtime symlink merges into one.

## Alternatives considered

- **JSDoc + `checkJs`, staying on Node.** Gives identical diagnostics against the same SDK types
  with no runtime change, and would have been the right call if types were the only goal. Rejected
  because it leaves two runtimes in the image and pays the annotation cost in comments rather than
  in syntax.
- **`oven/bun` as the base image.** Rejected: it would have meant surrendering the per-arch sha256
  pinning. `debian:bookworm-slim` is the same OS as `node:24-slim` with Node subtracted, so there is
  no glibc drift for git, jq or curl.
- **`trustedDependencies` for the three blocked postinstalls.** Rejected on evidence: an A/B of npm
  with scripts running against bun with scripts blocked showed every file byte-identical in all
  three packages, so trusting them would grant build-time code execution for zero bytes. All three
  scripts also invoke `node`, which no longer exists.

## Revisit triggers

- Bun gains a real heap ceiling equivalent to `--max-old-space-size`, making the mentor's in-process
  bound expressible again.
- The Pi SDK stops working under Bun, or starts requiring a postinstall that genuinely produces
  files.
