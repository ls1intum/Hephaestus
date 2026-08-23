---
applyTo: "**/*.ts,**/*.tsx"
---

# TypeScript standards

Follow the [general coding guidelines](./general-coding.instructions.md) in addition to the rules
below.

`webapp/AGENTS.md` is the SPA's own guide and wins wherever the two overlap — file naming, the
container/presentation split, data fetching, React Compiler, role gating, testing. This file carries
only what applies to **every** TypeScript tree in the repo, the Bun agent runtime included.

## TypeScript

- Reach for `type` when composing shapes and `interface` when you need declaration merging or
  extension. Export prop and return-value types so other modules can reuse them.
- Default to `const`, mark collections `readonly` when practical, and do not mutate arguments.
- Assert intent with `satisfies` rather than a cast. `typescript/no-explicit-any`,
  `no-non-null-assertion` and the `no-unsafe-*` family are all errors, so a cast is usually the linter
  telling you the type is wrong upstream.
- Validate anything crossing a trust boundary — a webhook body, a hand-parsed stream, `JSON.parse` —
  with a discriminated union or a `zod` schema. Generated API types are already checked by `tsc` and
  need no second guard.

## Lint and format

`pnpm run format` then `pnpm run check`, from the repo root. Never start oxlint from inside a package
directory: `options.typeAware` lives in the repo-root config and a nested run silently checks nothing
type-aware. Fix findings or suppress one inline with
`// oxlint-disable-next-line <plugin>/<rule> -- <why>`, spelled exactly as the diagnostic prints it.

## React, where it applies

- Author components as named functions and annotate props explicitly. `React.FC` is a lint error.
- The React Compiler runs at build time (`webapp/vite.shared.ts`), so `useMemo`, `useCallback` and
  `memo` are not importable from `react` — nor is `forwardRef`, since React 19 passes `ref` as an
  ordinary prop. The few memos that survive carry their reason on the suppression above the import.
- Keep render pure — no store mutation, navigation or DOM work during render, and that includes
  reading a clock. `webapp/AGENTS.md` § The time of day names the two sanctioned readings.
- Routes fetch, components receive props. `scripts/check-presentational-components.ts` fails the build
  on a component that imports the query layer.
- Compose class names with `cn()` from `@/lib/utils`. It is the repo's only wrapper over `clsx` and
  `tailwind-merge`; import neither directly.

## Testing

- Network is mocked with **MSW** (`src/mocks/handlers.ts`, installed by `src/test/setup-msw.ts`).
  Do not hand-roll `fetch` doubles, and do not `vi.mock` the generated SDK to fake a response.
- `@testing-library/jest-dom` is not registered in the Vitest project, so its matchers throw at
  runtime while `tsc` stays happy. `webapp/AGENTS.md` lists the replacements.
