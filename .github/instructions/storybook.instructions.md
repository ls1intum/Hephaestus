---
applyTo: "**/*.stories.ts,**/*.stories.tsx"
---
# Storybook

The full conventions — props shape, slots, story titles, args and Controls, play assertions, the
accessibility posture, the silent traps, and a grading rubric — live in
`.claude/skills/storybook-components/`. Read the file there that answers your question rather than
reasoning from Storybook's own docs, which do not know what this repo has been burned by.

This file is the short version: what a story here owes, and what no gate can tell you.

## What a story is for

A story proves the component renders what it is **given**. Stories accelerate development and design
review; they are not a second test suite and not documentation overhead. Enumerate the states the
component can actually reach — every branch of its state union, every `variant` value, the longest
realistic string, the empty collection — rather than filling in a checklist. A variant story that
reaches no new branch adds a Chromatic snapshot and a maintenance edge and nothing else.

A story renders with **no network**. Whether the screen sends the right query is a route test's
question, not a story's.

## Shape

- Colocate: `Component.stories.tsx` beside `Component.tsx`.
- Set defaults with `args` at the meta level, never `argTypes.defaultValue`.
- Keep at least one story per file driven by meta args (`export const Default: Story = {};`). A
  `render` that ignores `args` disables the Controls panel, which is most of a story's value.
- Add `argTypes` only to **correct** what `react-docgen` inferred wrong — a union rendered as a text
  box, an object arg that should be `control: false`. Not to restate inference.
- `layout: "centered"` for an isolated component, `"fullscreen"` for a page-level one.

## Titles

Most files omit `title` and are filed by their path under `src`, which cannot go stale. Declare an
explicit `title` when the file layout cannot express where a reader looks for the thing — a product
surface assembled from several directories, or one an admin knows by the screen it is on. Sentence
case. `rules/story-titles.md` in the skill has the namespaces and the sidebar-ordering gate.

## Published prose

Nearly every file carries `tags: ["autodocs"]`, so a JSDoc block above `meta` or above a story **is**
the component's documentation page. It earns its place only by recording something the reader cannot
derive from the code below it — a rejected alternative, a trap, a why. Restating the story's name is
the common failure.

If the stories render a test harness rather than the component, either point `component` at the real
component or drop `autodocs`; do not publish the harness's props as if they were the API. A file that
opts out says why in its meta.

## Gates

Several of the conventions above are enforced by house oxlint rules and by `check:stories`,
`check:story-sort` and `check:components`. Each states itself at the call site when it fires and
carries its own reasoning, so read the diagnostic rather than looking for the rule restated here.
The one thing no gate can see is whether a `play` function asserts the **outcome** of what it did
rather than merely reaching an assertion.
