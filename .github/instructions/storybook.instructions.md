---
applyTo: "**/*.stories.ts,**/*.stories.tsx"
---
# Storybook

The full conventions — props shape, slots, story titles, args and Controls, play assertions, the
accessibility posture, and a grading rubric — live in `.claude/skills/storybook-components/`. Read the
file there that answers your question rather than reasoning from Storybook's own docs, which do not
know what this repo has been burned by.

This file is the short version: what a story here owes, and what fails the build if it does not.

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
- A `meta` that names a `component` is `satisfies Meta<typeof Component>`. A gallery meta naming no
  component may be bare `Meta`.
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
case, and every top-level segment must appear in `storySort.order` in `.storybook/preview.ts` or it
sorts to the bottom silently. `rules/story-titles.md` in the skill has the namespaces.

## Published prose

Nearly every file carries `tags: ["autodocs"]`, so a JSDoc block above `meta` or above a story **is**
the component's documentation page. It earns its place only by recording something the reader cannot
derive from the code below it — a rejected alternative, a trap, a why. Restating the story's name is
the common failure.

If the stories render a test harness rather than the component, either point `component` at the real
component or drop `autodocs`; do not publish the harness's props as if they were the API. A file that
opts out says why in its meta.

## These fail the build, not review

- A story that installs MSW handlers (`scripts/check-presentational-components.ts`). Autodocs mounts
  every story of a file into one document over a single global worker, so the last story's handlers
  answer for the whole page.
- `parameters.a11y` or `globals.a11y` on a meta or a story (`hephaestus/no-story-a11y-override`).
  `.storybook/preview.ts` sets `a11y: { test: "error" }` project-wide and anything local can only
  lower it. A genuine misfire is exempted once, in `preview.ts`, with the upstream link beside it.
- A `play` function that never reaches an assertion (`hephaestus/play-must-assert`), and
  `expect(getBy…).toBeInTheDocument()` (`hephaestus/no-redundant-in-the-document`).
- `within(canvasElement)` when the play was handed `canvas` (`hephaestus/no-within-canvas-element`).
- A `<p>` tag in a comment Storybook publishes (`scripts/check-story-prose.ts`). Storybook renders
  these blocks with `markdown-to-jsx`; separate paragraphs with a blank comment line.
