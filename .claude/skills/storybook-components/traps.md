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

## 3. A hook suppression lands on the wrong line, and the legacy rule name takes the pass with it

`react/set-state-in-effect` reports on the **`setState` call inside the effect body**, not on the
`useEffect` line. So the obvious placement — a `disable-next-line` above the effect — points at the
`useEffect` and suppresses nothing; the diagnostic still fires and the directive is additionally
reported as `Unused oxlint-disable directive`. Put the directive above the reported line, or, better,
fix the effect.

Spelling it as the legacy `react-hooks/…` name is worse. One such directive anywhere in a component
body suppresses **every** React hook diagnostic in that component, whichever line it sits above —
neighbouring components in the same file keep reporting, so the file does not look disabled.

`options.reportUnusedDisableDirectives` does catch both shapes: either way the run exits 1. The cost
is not a silent pass, it is a misleading one — the build fails complaining about an unused directive
while the diagnostic you meant to suppress has quietly gone somewhere else.

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

## 6. A hand-rolled stateful wrapper swallows the spy in `meta.args`

If the wrapper passes its own `onChange` instead of `{...args}`, the `fn()` declared in `meta.args` can
never be called, never be asserted, and never appears in the Actions panel — while the file looks fully
instrumented. Use `Stateful` / `StatefulPatch` from `webapp/src/stories/stateful.tsx`.
