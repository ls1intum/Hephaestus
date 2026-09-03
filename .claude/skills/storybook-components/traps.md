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

## 3. A hook suppression aimed at the `useEffect` line suppresses nothing

`react/set-state-in-effect` reports on the **`setState` call inside the effect body**, not on the
`useEffect` line. So the obvious placement — a `disable-next-line` above the effect — points at the
`useEffect`: the diagnostic still fires, and the directive is additionally reported as
`Unused oxlint-disable directive` because `options.reportUnusedDisableDirectives` is `error` in the
repo-root `.oxlintrc.json`. The build fails complaining about the directive while the diagnostic you
meant to silence is still there. Put the directive above the reported line, or fix the effect.

One `setState` in an effect usually trips **two** rules at once — `react/set-state-in-effect` and
`react/no-deriving-state-in-effects` — so a directive naming one leaves the other reporting on the
same line and looks like the suppression failed. Name both, or fix the effect.

Naming `react/rules-of-hooks` instead is the dangerous spelling: oxlint routes the hook rules through
that one name, so a directive naming it **anywhere in a component body** silences every hook
diagnostic in that component, whichever line it sits above. A sibling component in the same file keeps
reporting, so the file does not look disabled. The directive is itself reported unused, so the build
fails — but it fails about the directive, and the diagnostics it swallowed are simply gone.

## 4. One story's MSW handlers answer for the whole Docs page

Autodocs mounts every story of a file into **one** document, and `msw-storybook-addon` installs on a
single global worker — so the last story's handlers serve every story on that page. One error story
silently breaks its siblings' Docs page while every isolated story, and therefore every test and every
snapshot, stays green; the symptom is a Docs page that renders every sibling as the error state. A
story file installs no handlers at all, and `scripts/check-presentational-components.ts` enforces it.

## 5. `test:storybook` does not run the README-export check — CI does, right after

`.github/workflows/ci-quality-gates.yml` runs the webapp package's `export:assets` immediately after
`test:storybook`, and fails the job if `docs/images/readme` is dirty afterwards. So the storybook job can go red
having printed a clean pass line. If a change moves or renames a story that exports a README asset,
run `vp run --filter webapp export:assets` and commit the result.

## 6. A hand-rolled stateful wrapper swallows the spy in `meta.args`

If the wrapper passes its own `onChange` instead of `{...args}`, the `fn()` declared in `meta.args` can
never be called, never be asserted, and never appears in the Actions panel — while the file looks fully
instrumented. Use `Stateful` / `StatefulPatch` from `webapp/src/stories/stateful.tsx`.
