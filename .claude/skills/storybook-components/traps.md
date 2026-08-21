# Traps — things that pass while being wrong

Everything here fails **silently**. A green run is not evidence against any of them.

## 1. A just-opened Base UI overlay reads as invisible for one frame

Base UI mounts the panel with `data-starting-style` and clears it a frame later, so the panel computes
to `opacity: 0` and a bare `toBeVisible()` fails on a perfectly mounted element. It is **not** a
duration problem: the Playwright context already requests `reducedMotion: "reduce"`, the media query
matches, and forcing every duration to 1ms does not fix it. Use `expectSettledVisible` /
`settledPopup()` from `webapp/src/test/overlay.ts`.

## 2. `animation.finished` **rejects** when the animation is cancelled

`AbortError`, not a resolution — and a popup that re-positions while opening replaces its own enter
animation routinely. Any settle helper must `.catch()` the rejection and treat it as an outcome
(`webapp/src/test/overlay.ts:60-66`). Without the catch the helper throws on the ordinary path.

## 3. An `exhaustive-deps` suppression silently disables `set-state-in-effect` too

oxlint runs the React hook rules as one fused pass, so `// oxlint-disable-next-line
react-hooks/exhaustive-deps` above a dependency array turns off **every** hook rule for that
`useEffect` — including `react/set-state-in-effect`, which reports on the `setState` line *above* the
directive. `options.reportUnusedDisableDirectives` does not save you: it calls the directive unused
while it is actively suppressing an error. One real finding in `TimeframeFilter.tsx` hid this way
until the directive was removed. Prefer letting a hook finding land in `oxlint-suppressions.json`,
where it is counted and reviewable, over a directive that takes its neighbours with it.

## 4. `oxlint-suppressions.json` counts findings per file, not per line

Fix one violation and introduce another of the same rule in the same file and the count is unchanged,
so CI stays green and the diff is empty. The baseline gates *new files and new rules*, not churn
inside an already-baselined file. Read a count that went up as a new bug, never as noise.

## 5. One story's MSW handlers answer for the whole Docs page

Autodocs mounts every story of a file into **one** document, and `msw-storybook-addon` installs on a
single global worker — so the last story's handlers serve every story on that page. One error story
silently breaks its siblings' Docs page while every isolated story, and therefore every test and every
snapshot, stays green. That is not hypothetical: it is what made a screen's Docs page read
"Couldn't load this feedback". A story file installs no handlers at all;
`scripts/check-presentational-components.mjs` enforces it.

## 6. `test:storybook` does not run the README-export check — CI does, right after

`.github/workflows/ci-tests.yml` runs `pnpm run export:readme-assets` after `test:storybook` and fails
the job if `docs/images/readme` is dirty. So the storybook job can go red printing "1382 passed". If a
change moves or renames a story that exports a README asset, run
`pnpm --filter webapp run export:readme-assets` and commit the result.

## 7. Storybook subcomponents get no Controls, and their `argTypes` cannot be overridden

*"Subcomponents are only intended for documentation purposes and have some limitations: 1. The
`argTypes` of subcomponents are inferred … and cannot be manually defined or overridden. 2. The table
for each documented subcomponent does *not* include controls"*. This is the cost that decides whether a
part becomes a compound subcomponent or stays a prop — see `rules/composition-and-slots.md` rule 3.

## 8. A hand-rolled stateful wrapper swallows the spy in `meta.args`

If the wrapper passes its own `onChange` instead of `{...args}`, the `fn()` declared in `meta.args` can
never be called, never be asserted, and never appears in the Actions panel — while the file looks fully
instrumented. Use `Stateful` / `StatefulPatch` from `webapp/src/stories/stateful.tsx`.
