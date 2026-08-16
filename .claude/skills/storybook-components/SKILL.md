---
name: storybook-components
description: >
  Component API and Storybook conventions for the Hephaestus webapp. Use when writing or
  changing a component under `webapp/src/components/**`, writing or reviewing a `*.stories.tsx`,
  designing a component's props, or grading a webapp diff in review. Covers props/state shape,
  Base UI `render=` slots, the vocabulary registries, story titles, args and Controls,
  play-function assertions, and the accessibility posture. Ships a grading rubric.
metadata:
  source: internal
  version: "1.0.0"
---

# Storybook + component API

Load the one file that answers your question. Nothing here restates React or Storybook docs; it
records what this repo decided, and what it has already been burned by.

| File | The question it answers |
|---|---|
| `rules/props-state-shape.md` | What shape should this prop be — object or scalars, union or flags, controlled or not — and does it deserve to exist? |
| `rules/composition-and-slots.md` | Should this be `children`, a prop, a slot, context, or a compound API — and what does a slotted element owe the primitive? |
| `rules/vocabulary-registries.md` | Where do a status's label, icon and colour come from, and when does a badge render nothing? |
| `rules/story-titles.md` | Where does this story land in the sidebar, and does it need an explicit `title`? |
| `rules/story-args-and-coverage.md` | Which states earn a story, and how do the Controls stay wired to the real component? |
| `rules/play-assertions.md` | Can this play function fail, and how do I assert against a portal or a just-opened overlay? |
| `rules/a11y.md` | What does axe not catch here, and what must a component name for itself? |
| `traps.md` | Something passed that should not have. Read this before debugging a green-but-wrong story. |
| `RUBRIC.md` | Grading a diff across nine dimensions, D→A+, with the anti-criteria that look like quality and are not. |

## Already enforced — do not re-litigate, and do not restate in prose

These fail `pnpm run check`. Treat a violation as a build error, not a style opinion, and do not
write a guideline that repeats one.

- `webapp/.biome/typed-story-meta.grit` — a `meta` naming a `component` must be
  `satisfies Meta<typeof X>`; a gallery meta naming no component may be bare `Meta`.
- `webapp/.biome/no-redundant-in-the-document.grit` — `expect(getBy…).toBeInTheDocument()` and
  bare `await expect(getBy…)`.
- `scripts/check-story-prose.mjs` (`check:stories`) — `<p>` in a comment Storybook publishes.
- `scripts/check-presentational-components.mjs` (`check:components`) — a component importing the
  query layer, and a story installing MSW handlers. Its allowlist is shrink-only.
- `.storybook/preview.tsx` sets `a11y: { test: "error" }` project-wide, with zero per-story
  overrides across the whole tree. Adding one is a finding, not a fix.

## Not here

`/composition-patterns` owns the React 19 API shape (`react19-no-forwardref`), render props, and
the generic compound-component pattern. This skill states only what those cost *here*.
