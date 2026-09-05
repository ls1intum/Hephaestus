# Hephaestus web app

The React 19 SPA: TanStack Router and Query, Tailwind 4, shadcn primitives over Base UI, Vitest and
Storybook for tests, oxlint and oxfmt for lint and format, Vite for the build.

**Read [`AGENTS.md`](./AGENTS.md) before changing anything here.** It has the commands and the
gotchas — the rules a look at the tree does not tell you, several of which fail the build if you
guess: never hand-write a `queryKey`, never call `fetch` under `src/**`, never read the clock during
render, never edit `src/components/ui/` in passing, and never import the query layer from a
component.

The repository-wide gates are `vp run check` before pushing and `vp run verify` before requesting
review; the root [`AGENTS.md`](../AGENTS.md) lists what each one covers.
