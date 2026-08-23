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
(`webapp/src/test/overlay.ts`). Without the catch the helper throws on the ordinary path.

## 3. `react-hooks/exhaustive-deps` in a directive turns off hook linting for the whole component

Spell a suppression the way the diagnostic prints it. oxlint reports the React hook rules as
`react(exhaustive-effect-dependencies)` and `react(set-state-in-effect)`, and a directive naming
either of those suppresses **only that rule, only on the line it points at** — `set-state-in-effect`
still fails a build whose `exhaustive-effect-dependencies` was silenced.

`react-hooks/exhaustive-deps` is a different, legacy name, and it does not behave that way. One
`// oxlint-disable-next-line react-hooks/exhaustive-deps` **anywhere inside a component body**
suppresses every React hook diagnostic in that component — both rules, every effect in it, whichever
line the directive sits above. Neighbouring components in the same file keep reporting, so the file
does not look disabled.

`options.reportUnusedDisableDirectives` does not close this one. It catches a directive that
suppresses nothing — including one naming a rule that does not exist, reported as
`Unused oxlint-disable directive` and failing the build. But `react-hooks/exhaustive-deps` really
does suppress its own warning, so it counts as used and the collateral goes unremarked. The run
exits 0.

Fix the effect rather than reach for the directive.

## 4. One story's MSW handlers answer for the whole Docs page

Autodocs mounts every story of a file into **one** document, and `msw-storybook-addon` installs on a
single global worker — so the last story's handlers serve every story on that page. One error story
silently breaks its siblings' Docs page while every isolated story, and therefore every test and every
snapshot, stays green. That is not hypothetical: it is what made a screen's Docs page read
"Couldn't load this feedback". A story file installs no handlers at all;
`scripts/check-presentational-components.ts` enforces it.

## 5. `test:storybook` does not run the README-export check — CI does, right after

`.github/workflows/ci-tests.yml` runs `pnpm run export:readme-assets` after `test:storybook` and fails
the job if `docs/images/readme` is dirty. So the storybook job can go red having printed a clean pass line. If a
change moves or renames a story that exports a README asset, run
`pnpm --filter webapp run export:readme-assets` and commit the result.

## 6. Storybook subcomponents get no Controls, and their `argTypes` cannot be overridden

*"Subcomponents are only intended for documentation purposes and have some limitations: 1. The
`argTypes` of subcomponents are inferred … and cannot be manually defined or overridden. 2. The table
for each documented subcomponent does *not* include controls"*. This is the cost that decides whether a
part becomes a compound subcomponent or stays a prop — see `rules/composition-and-slots.md` rule 3.

## 7. A hand-rolled stateful wrapper swallows the spy in `meta.args`

If the wrapper passes its own `onChange` instead of `{...args}`, the `fn()` declared in `meta.args` can
never be called, never be asserted, and never appears in the Actions panel — while the file looks fully
instrumented. Use `Stateful` / `StatefulPatch` from `webapp/src/stories/stateful.tsx`.
