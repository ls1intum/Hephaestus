# Webapp

React 19 SPA on TanStack Router/Query, Tailwind 4, shadcn primitives over **Base UI**
(`@base-ui/react`) — not Radix. Vitest + Storybook 10 for tests, oxlint for lint and Biome for
format, Vite for the build. React Compiler runs at build time.

This file is the gotchas. Conventions that a look at the tree already tells you are not here.

## Commands

| Task | Command |
|------|---------|
| Dev server | `pnpm run dev` (port 4200) |
| Type check | `pnpm run typecheck:webapp` |
| Lint + format | `pnpm run check:webapp` |
| Tests | `pnpm run test:webapp` |
| Storybook | `pnpm --filter webapp run storybook` |

## Boundaries

**Always** — run `pnpm run check:webapp` before committing · export prop interfaces · colocate a
story with a new component · use the generated TanStack Query options.

**Ask first** — a new UI library dependency · editing a shadcn primitive in `src/components/ui/` ·
a new Zustand store · a global style.

**Never** — edit `src/api/` or `src/components/ui/` · add `useMemo`/`useCallback`/`React.memo` ·
use `React.FC` · call `fetch` directly.

## File naming

Two cases, and which one a file gets is decided by what it exports:

- **`PascalCase.tsx`** — a file whose export is a React component. The filename is the component
  name, so `AdminLlmUsagePage.tsx` exports `AdminLlmUsagePage`. Its `.test.tsx` and `.stories.tsx`
  siblings inherit the name. An acronym is a word, not a run of capitals: `LandingFaqSection`, not
  `LandingFAQSection`.
- **`kebab-case.ts`** — everything else: helpers, schemas, formatters, hooks, fixtures. `usageUtils.ts`
  is `usage-utils.ts`, `jobUtils.tsx` is `job-utils.tsx` (a `.tsx` that exports a small helper
  component alongside its formatters is still a helper module).

Colocation does not change the rule: a helper next to the component that uses it is named the same
way as one in `src/lib/`.

oxlint enforces the casing (`unicorn/filename-case`) over `src/components/**`, `src/lib/**`,
`src/hooks/**` and `src/integrations/**` — every directory this repo writes by hand. `src/routes/**`
is exempt: TanStack Router derives URL segments from the filenames there, so the router owns that
naming (`$workspaceSlug.tsx`, `-route.test.ts`). `src/api/**` and `routeTree.gen.ts` are generated and
excluded from linting entirely.

## Linting

**oxlint lints, Biome formats.** `.oxlintrc.json` is the whole rule set, and every rule it turns off
carries the reason beside it — read that before switching one back on.

- **Suppress with `// oxlint-disable-next-line <rule> -- <why>`**, above the line the diagnostic
  points at, spelling the rule the way oxlint printed it. A directive that stops suppressing anything
  fails the build, so a suppression cannot outlive its reason.
- **A rule that cannot resolve is a hard config error, not a silent skip.** oxlint refuses to start if
  `jsPlugins` names a module it cannot load, or if `rules` names a rule no loaded plugin defines. That
  is what makes the house rules impossible to drop silently (oxc-project/oxc#25203), so keep them
  named in `rules`.
- **There is no baseline file.** Every enabled rule reports zero, so a finding is always new and is
  always fixed rather than recorded. The only suppressions are a handful of `oxlint-disable-next-line`
  directives, each naming its rules and its reason; `reportUnusedDisableDirectives` fails the build
  when one stops suppressing anything.
- **House rules live in `tools/oxlint/`**, in TypeScript, so `pnpm run typecheck` covers them, with a
  `RuleTester` suite beside each (`oxlint/plugins-dev`).
- **These have no linter behind them and are on review**: an inline `<svg>` needs a `<title>`, an
  `aria-label` or `aria-hidden`; ARIA roles that duplicate an element's implicit role; `autoFocus` on a
  component rather than a DOM element; non-ASCII filenames.

## Which admin console a component belongs to

`src/components/admin/**` holds two different consoles, and **the directory name does not tell you
which**: `admin/llm/` is the instance-wide LLM console, `admin/ai/` is the per-workspace one. The
**component name** is what carries the scope, and it is the only thing that does:

- `Admin*` or `Instance*` — instance-wide. Configures the deployment for every workspace
  (`AdminLlmConnectionsTable`, `InstanceLlmSettingsCard`).
- `Workspace*` — scoped to one workspace (`WorkspaceLlmModelsTable`, `WorkspaceLlmProviderPanel`).
- **Unprefixed — shared by both, so it must not assume a scope.** `LlmConnectionFields`,
  `LlmModelFields` and `PriceModeEditor` are each imported by an `Admin*` dialog *and* a `Workspace*`
  one. Adding a scope-specific field, permission check or copy string to one of these breaks the
  other console silently, because nothing in the file says it has two callers.

Name a new component for its scope, and check the caller list before editing an unprefixed one.

Splitting these into `admin/instance/` and `admin/workspace/` has been considered and rejected: the
shared components in the third bullet have no scope to be filed under, so a split would need a
`shared/` directory anyway and the path still would not answer the question — while moving ~290 files.
The prefix answers it, and it also shows up in imports and in the Storybook sidebar.

## Container/presentation split

- **Routes** (`src/routes/**`): data fetching, loaders, auth guards, side effects.
- **Hooks** (`src/hooks/use-*.ts`): a route's fetching, factored out when more than one route needs it.
- **Components** (`src/components/**`): presentational, with no exception for a "cohesive section".
  They take their data as props and never import the query layer.

`node scripts/check-presentational-components.mjs` enforces it, and `pnpm run check` runs it. Two
halves, because they fail differently:

- **A component under `src/components/**` may not import `@/api/@tanstack/react-query.gen`,
  `@/api/sdk.gen` or the client, or call a TanStack query hook.** `@/api/types.gen` is pure types and
  stays allowed everywhere. Put the `useQuery` in the route file and pass plain props down; when two
  routes need the same call, move it to `src/hooks/use-*.ts` and let both routes call that.
- **A story file may not install MSW handlers.** Autodocs mounts every story of a file into one
  document and `msw-storybook-addon` installs on a single global worker, so the last story's handlers
  answer for the whole page — one error story silently breaks its siblings' Docs page while every
  isolated story, and therefore every test and every snapshot, stays green. That is not hypothetical:
  it is what made a screen's Docs page read "Couldn't load this feedback".

The allowlist inside that script is **shrink-only** — an entry that scans clean fails the build, so it
cannot go stale. Fix the file, do not add to it.

**A story renders with no network at all.** A story's job is to prove the component renders what it is
given. A wire contract — that the screen sends `?reviewTier=OFF`, that a filter reaches the query key —
is not a rendering fact and does not belong in a story mock; assert it in a route test, which owns the
query. Storybook is where you look at the component; the route test is where you check what it asks for.

## Seeding a form from props

Put the form body in its own component and `key` it on the subject being edited, so switching subjects
remounts it with fresh initial state. Never copy props into state from an effect: between the prop
change and the effect running, the form shows the *previous* subject's values under the new subject's
title.

## Data fetching

Spread the generated options; never hand-write a `queryKey`:

```typescript
const documentQuery = useQuery(getDocumentOptions({ path: { id } }));
queryClient.setQueryData(getDocumentQueryKey({ path: { id } }), updated);
```

Do not: call `fetch` · duplicate loading/error state in local state · hand-roll fetch mocks in tests.

| State | Where |
|---|---|
| Server data | TanStack Query |
| UI preferences | Zustand (`src/stores/**`) |
| Form state | React state / controlled components |
| URL state | TanStack Router search params |

## React Compiler

`vite.shared.ts` turns it on for every build of app source, so **do not add** `useMemo`,
`useCallback` or `React.memo` to new code. Existing usages stay — removing them changes compiler
output for no gain. Reach for manual memoization only when you need precise control over an effect's
dependencies.

## Name a component for the concept, not for the wire

A component, its file, its props type and its story title all name the same thing, and that name is
the one in `docs/contributor/practice-feedback-language.md` — the product's word, not the server's and
not the one the screen used to use. The unit a review detects is an **observation**; the unit delivered
to a developer is **feedback**. Neither is a "finding" or a "message".

Renaming the copy alone does not hold: a name that survives in the import list is the one the next
component gets named after. When a concept is renamed, the sweep is wire contract, route, component
file, exported symbols, props type, story title, story export names, then copy — in that order.
Stopping at copy leaves the old vocabulary somewhere a grep for the screen text will not find it.

## Component API and Storybook

Both live in the **`storybook-components` skill** (`.claude/skills/storybook-components/`). Load it
when you design a component's props, write or review a `*.stories.tsx`, or grade a webapp diff. It has
a routing table on the front page; its `RUBRIC.md` is the grading instrument for review.

## Routing

Declare routes with `createFileRoute`. Keep loaders side-effect free and prefer
`context.queryClient.ensureQueryData(...)` with the generated options. Put shared data (query client,
auth) on the router context. Never hand-edit `routeTree.gen.ts` — there is no `tsr` CLI here; the
TanStack Router Vite plugin regenerates it when the dev server runs.

## Role-based gating (OWNER > ADMIN > MEMBER)

Client-side gating is a UX affordance only — the server enforces authorization on every endpoint.
Never re-invent `role === "ADMIN"`; use the shared pieces:

- **Whole admin surfaces — gate by placement, not by a check.** Put workspace-admin pages under
  `src/routes/_authenticated/w/$workspaceSlug/admin/`; its `route.tsx` layout carries the `beforeLoad`
  role guard, so every route in the directory inherits it. A file that maps to an `/admin` URL without
  nesting under that layout silently skips the gate — that is the bug class `admin/-route.test.ts`
  exists to catch, by driving every admin URL in the generated route tree through the real router as a
  MEMBER (the leading `-` marks the file as a test rather than a route). Do not weaken it.
- **Individual controls**: `useWorkspaceAccess()` returns `role` and `isAdmin`; the role math is
  `hasMinimumWorkspaceRole` (`src/lib/workspace-roles.ts`). Pure role predicates live in `src/lib/`;
  QueryClient-coupled resolvers (`resolveWorkspaceMembership`, `workspaceMembershipQueryOptions`) live
  in `src/integrations/auth/guard.ts`. Fetch membership only via `workspaceMembershipQueryOptions` so
  every caller shares one cache entry and one `staleTime`.
- **Hide rather than disable** — for permissions specifically. Disabling is the better default for a
  control the user could still unlock, but ["hiding is recommended in cases where the user will never be
  able to use that feature due to their role or license"](https://www.uxtigers.com/post/inactive-buttons),
  which is this case. A disabled control would also be a poor explanation: a native `disabled` button is
  unreachable by keyboard, so a tooltip saying why can never be read.
- **Workspace role ≠ instance role.** `useWorkspaceAccess().isAdmin` is membership in *this* workspace;
  `useAuth().isAppAdmin` is instance-wide (ADR 0017). A workspace-role gate on a surface with no active
  workspace is always false.
- There is **no `<RequireRole>` wrapper component today**. Route placement covers whole surfaces, and
  the rest is a boolean the route reads once. That boolean is currently drilled: `canAdminister` passes
  through `TracePage` → `TraceRefusalAlert` / `TraceSignalTimeline` → `RefusalFixLink`, four components
  deep for one leaf. Before adding a fifth, render the gated leaf in the route and pass it down as
  `children`. When a role-assignment UI lands, its mutation must invalidate the membership query key.

## Styling

Tailwind utilities composed with `cn()` (`@/lib/utils`). Prefer the semantic tokens defined in
`src/styles.css` over hard-coded values: `bg-surface`, `bg-background`, `text-foreground`,
`text-muted`, `border-border`.

## Testing

`getByRole` > `getByLabelText` > `getByText` > `getByTestId`.

### No jest-dom matchers in a Vitest test

`@testing-library/jest-dom` is **not** registered in the Vitest unit project — `vite.config.ts` lists
one setup file, `./src/test/setup-msw.ts`, and it does not import the matchers. So
`expect(el).toBeInTheDocument()` in a `*.test.tsx` throws `Invalid Chai property: toBeInTheDocument`,
at runtime, with `tsc` perfectly happy. Assert on plain values instead:

| Instead of | Write |
|------------|-------|
| `expect(el).toBeInTheDocument()` | `expect(el).not.toBeNull()` |
| `expect(q).not.toBeInTheDocument()` | `expect(q).toBeNull()` (use `queryBy*`, which returns null) |
| `expect(el).toHaveAttribute("href", "/x")` | `expect(el.getAttribute("href")).toBe("/x")` |
| `expect(el).toBeDisabled()` | `expect((el as HTMLButtonElement).disabled).toBe(true)` |

The matchers **are** available in stories, because `expect` from `storybook/test` ships them. Copying
an assertion out of a story into a route test is exactly how this bites.

## TypeScript versions

Type checking runs on TypeScript 7 through the `typescript7` alias, invoked by path because both it
and `typescript` install a `tsc` bin and the winner is undefined. `typescript` itself stays on 6
because `@hey-api/openapi-ts` calls the TypeScript compiler API at runtime and 7 ships none — its
peer range accepts 7, so nothing warns you before the generator dies. A scoped `parent>typescript`
pnpm override does **not** help: peers resolve from this package, not the override. Collapse both
onto 7 when `openapi-ts` stops needing the old compiler API.

## Generated files (do not edit)

| Path | Regenerate with |
|------|-----------------|
| `src/api/**/*` | `pnpm run generate:api:application-server` |
| `src/routeTree.gen.ts` | TanStack Router plugin (automatic) |

Regenerating **empties** `src/api/`, so nothing hand-written survives there — not even a test about the
generated client. `src/test/response-transformers.test.ts` lives outside that directory for exactly
this reason.

### Dates from the API

A `format: date-time` field is a real `Date` by the time a component sees it: `openapi-ts.config.ts`
sets `transformer: true` on the `@hey-api/sdk` plugin, which wires the generated response transformers
into every SDK call. Two consequences:

- **Fixtures passed straight to a prop use `new Date(…)`** — that is the shape production sends.
  Fixtures served through **MSW** are JSON and use ISO strings; type those with `Wire<T>` from
  `@/lib/dates`, which turns every nested `Date` in a generated view into a `string`.
- **`asDate()`** (also `@/lib/dates`) is for values that did *not* come through the generated SDK — the
  Mentor SSE stream is hand-parsed in `use-mentor-chat.ts` because its operation is excluded from
  generation — and for optional fields, since it returns `undefined` instead of an Invalid Date.

`transformer: true` is load-bearing and invisible to `tsc`: without it the types still say `Date` while
the client hands back strings, which is how `.toLocaleDateString()` once shipped a crash.
`src/test/response-transformers.test.ts` is the guard; it calls the real SDK, so it fails whenever that
wiring is lost.

## Skills

| Skill | When |
|-------|------|
| `/storybook-components` | Component props, stories, play functions, a11y posture, grading a diff |
| `/composition-patterns` | Compound components, render props, React 19 API shape |
| `/web-design-guidelines` | UI accessibility review, UX patterns |
| `/react-best-practices` | Performance (~40% Next.js-specific — check applicability) |
