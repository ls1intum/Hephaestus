# Hephaestus web app

The React 19 SPA: TanStack Router and Query, Tailwind 4, shadcn primitives over Base UI, Vitest and
Storybook for tests, oxlint and oxfmt for lint and format, Vite for the build.

**Read [`AGENTS.md`](./AGENTS.md) before changing anything here.** It is the gotchas — the rules a
look at the tree does not tell you, several of which fail the build if you guess: never hand-write a
`queryKey`, never call `fetch` under `src/**`, never read the clock during render, never edit
`src/components/ui/` in passing, and never import the query layer from a component.

| Task | Command |
|------|---------|
| Dev server | `pnpm run dev:webapp` — port 4200 |
| Type check | `pnpm run typecheck:webapp` |
| Lint and format | `pnpm run check:webapp` — does **not** type-check |
| Unit tests | `pnpm run test:webapp` |
| Storybook | `pnpm --filter webapp run storybook:dev` |
| Story tests | `pnpm --filter webapp run test:storybook` |

The repository-wide gates are `pnpm run check` before pushing and `pnpm run verify` before requesting
review; the root [`AGENTS.md`](../AGENTS.md) lists what each one covers.

Component and story conventions live in the `storybook-components` skill
(`.claude/skills/storybook-components/`), which ships the rubric a webapp diff is graded against.
