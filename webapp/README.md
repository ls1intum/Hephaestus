# Hephaestus web app

The React 19 SPA: TanStack Router and Query, Tailwind 4, shadcn primitives over Base UI, Vitest and
Storybook for tests, oxlint and oxfmt for lint and format, Vite for the build.

**Read [`AGENTS.md`](./AGENTS.md) before changing anything here.** It is the gotchas — the rules a
look at the tree does not tell you, several of which fail the build if you guess: never hand-write a
`queryKey`, never call `fetch` under `src/**`, never read the clock during render, never edit
`src/components/ui/` in passing, and never import the query layer from a component.

| Task | Command |
|------|---------|
| Dev server | `vp run dev:webapp` — port 4200 |
| Type check | `vp run typecheck:webapp` |
| Lint and format | `vp run check:webapp` — does **not** type-check |
| Unit tests | `vp run test:webapp` |
| Storybook | `vp run --filter webapp storybook:dev` |
| Story tests | `vp run --filter webapp test:storybook` |

The repository-wide gates are `vp run check` before pushing and `vp run verify` before requesting
review; the root [`AGENTS.md`](../AGENTS.md) lists what each one covers.

Component and story conventions live in the `storybook-components` skill
(`.claude/skills/storybook-components/`), which ships the rubric a webapp diff is graded against.
