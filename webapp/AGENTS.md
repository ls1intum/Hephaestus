# Webapp

React 19 single-page application with TanStack Router/Query and Tailwind CSS.

## Commands

| Task | Command |
|------|---------|
| Dev server | `pnpm run dev` (port 4200) |
| Build | `pnpm run build:webapp` |
| Type check | `pnpm run typecheck:webapp` |
| Lint + format | `pnpm run check:webapp` |
| Tests | `pnpm run test:webapp` |
| Storybook | `pnpm --filter webapp run storybook` |
| Regenerate API | `pnpm run generate:api:application-server` |

## Boundaries

### Always
- Run `pnpm run check:webapp` before committing
- Export prop interfaces from components
- Create colocated stories for new components
- Use generated TanStack Query options

### Ask First
- Adding new UI library dependencies
- Modifying shadcn/ui primitives
- Creating new Zustand stores
- Adding global styles

### Never
- Edit files in `src/api/` or `src/components/ui/`
- Add manual memoization (compiler handles it)
- Use `React.FC` type annotation
- Call `fetch` directly (use generated client)

## Tech Stack

```
React 19 + React Compiler (auto-memoization)
├── Routing: TanStack Router v1 (file-based, type-safe)
├── Data Tables: TanStack Table v8
├── Server State: TanStack Query v5 (generated @hey-api client)
├── Client State: Zustand v5 (src/stores/**)
├── UI: shadcn/ui on Base UI (`@base-ui/react`) — NOT Radix, see the rubric below
├── Styling: Tailwind CSS v4 (design tokens in styles.css)
├── Testing: Vitest + Testing Library + Storybook 10
└── Build: Vite + Biome
```

## Project Structure

```
src/
├── routes/          # Container components (data fetching, loaders, guards)
├── components/      # Presentational components
│   └── ui/          # shadcn/ui primitives (DO NOT EDIT)
├── stores/          # Zustand stores
├── hooks/           # Custom hooks
├── api/             # Generated OpenAPI client (DO NOT EDIT)
├── lib/             # Utilities
└── styles.css       # Tailwind design tokens
```

### File naming

Two cases, and which one a file gets is decided by what it exports:

- **`PascalCase.tsx`** — a file whose export is a React component. The filename is the component
  name, so `AdminLlmUsagePage.tsx` exports `AdminLlmUsagePage`. Its `.test.tsx` and `.stories.tsx`
  siblings inherit the name.
- **`kebab-case.ts`** — everything else: helpers, schemas, formatters, hooks, fixtures. `usageUtils.ts`
  is `usage-utils.ts`, `jobUtils.tsx` is `job-utils.tsx` (a `.tsx` that exports a small helper
  component alongside its formatters is still a helper module).

Colocation does not change the rule: a helper next to the component that uses it is named the same
way as one in `src/lib/`.

### Which admin console a component belongs to

`src/components/admin/**` holds two different consoles, and **the directory name does not tell you
which**: `admin/llm/` is the instance-wide LLM console, `admin/ai/` is the per-workspace one. The
**component name** is what carries the scope, and it is the only thing that does:

- `Admin*` or `Instance*` — instance-wide. Configures the Hephaestus deployment for every workspace
  (`AdminLlmConnectionsTable`, `InstanceLlmSettingsCard`).
- `Workspace*` — scoped to one workspace (`WorkspaceLlmModelsTable`, `WorkspaceLlmProviderPanel`).
- **Unprefixed — shared by both, so it must not assume a scope.** `LlmConnectionFields`,
  `LlmModelFields` and `PriceModeEditor` are each imported by an `Admin*` dialog *and* a `Workspace*`
  one. Adding a scope-specific field, permission check or copy string to one of these breaks the
  other console silently, because nothing in the file says it has two callers.

Name a new component for its scope, and check the caller list before editing an unprefixed one.

Splitting these into `admin/instance/` and `admin/workspace/` directories has been considered and
rejected: the shared components in the third bullet have no scope to be filed under, so a split
would need a `shared/` directory anyway and the path still would not answer the question — while
moving ~290 files. The prefix answers it, and it also shows up in imports and in the Storybook
sidebar, which a directory name does not.

Biome enforces filename casing (`style/useFilenamingConvention`) over `src/components/**`, `src/lib/**`,
`src/hooks/**` and `src/integrations/**` — every directory whose files this repo writes by hand.
`src/routes/**` is exempt: TanStack Router derives URL segments from the filenames there, so the
router owns that naming (`$workspaceSlug.tsx`, `-route.test.ts`), not this rule. `src/api/**` and
`routeTree.gen.ts` are generated and are excluded from Biome entirely.

## TypeScript Conventions

```typescript
// Use `type` for composition, `interface` for extension/declaration merging
type UserWithPosts = User & { posts: Post[] };

interface MyComponentProps {
  title: string;
  onSubmit: (data: FormData) => void;
}

// Export prop types for reuse
export interface CardProps { ... }
```

### General Rules

- Default to `const`, mark collections `readonly` when practical
- Use optional chaining (`?.`) and nullish coalescing (`??`)
- Import with `@/*` alias (defined in tsconfig.json)
- Use discriminated unions or Zod schemas for runtime validation
- Use `satisfies` operator instead of broad casts

## React Patterns

### Component Definition

```typescript
// Named function export with explicit props type
export function UserCard(props: UserCardProps) {
  const { user, onSelect } = props;
  return <div>...</div>;
}

// Avoid React.FC - annotate props explicitly
```

### Container/Presentation Split

- **Routes** (`src/routes/**`): Data fetching, loaders, auth guards, side effects
- **Hooks** (`src/hooks/use-*.ts`): A route's fetching, factored out when more than one route needs it
- **Components** (`src/components/**`): Presentational, with no exception for a "cohesive section".
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

### Seeding a form from props

Put the form body in its own component and `key` it on the subject being edited, so switching
subjects remounts it with fresh initial state. Never copy props into state from an effect: between
the prop change and the effect running the form shows the *previous* subject's values under the new
subject's title.

## Data Fetching (TanStack Query)

Always spread generated options from the @hey-api client:

```typescript
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getDocumentOptions,
  getDocumentQueryKey,
  updateDocumentMutation,
} from "@/api/@tanstack/react-query.gen";

export function useDocumentEditor(documentId: string) {
  const queryClient = useQueryClient();

  const documentQuery = useQuery({
    ...getDocumentOptions({ path: { id: documentId } }),
  });

  const updateDocument = useMutation({
    ...updateDocumentMutation(),
    onSuccess: (updated) => {
      queryClient.setQueryData(
        getDocumentQueryKey({ path: { id: documentId } }),
        updated,
      );
    },
  });

  return { documentQuery, updateDocument };
}
```

### Do Not

- Write manual `queryKey` arrays
- Call `fetch` directly
- Duplicate loading/error state in local state
- Hand-roll fetch mocks in tests (use generated helpers)

## State Management

| State Type | Where |
|------------|-------|
| Server data | TanStack Query |
| UI preferences | Zustand stores (`src/stores/**`) |
| Form state | React state or controlled components |
| URL state | TanStack Router search params |

### Zustand Pattern

```typescript
// src/stores/preferences.ts
import { create } from "zustand";
import { persist } from "zustand/middleware";

interface PreferencesStore {
  theme: "light" | "dark";
  setTheme: (theme: "light" | "dark") => void;
}

export const usePreferencesStore = create<PreferencesStore>()(
  persist(
    (set) => ({
      theme: "light",
      setTheme: (theme) => set({ theme }),
    }),
    { name: "preferences" }
  )
);
```

## React Compiler

The webapp uses React Compiler (`babel-plugin-react-compiler`).

**Do not add for new code:**
- `useMemo()`
- `useCallback()`
- `React.memo()`

The compiler handles memoization automatically. Existing usages can remain.

### Name a component for the concept, not for the wire

A component, its file, its props type and its story title all name the same thing, and that name is
the one in `docs/contributor/practice-feedback-language.md` — the product's word, not the server's
and not the one the screen used to use. The unit a review detects is an **observation**; the unit
delivered to a developer is **feedback**. Neither is a "finding" or a "message".

Renaming the copy alone does not hold: a name that survives in the import list is the one the next
component gets named after. When a concept is renamed, the sweep is wire contract, route, component
file, exported symbols, props type, story title, story export names, then copy — in that order.
Stopping at copy leaves the old vocabulary somewhere a grep for the screen text will not find it.

## Component design rubric

Fourteen rules, each with the source you can check it against. They are short because a rubric nobody
finishes reading is not a rubric. Where a rule is ours rather than received practice it says so —
"house policy" means you may argue with it in review; a cited normative rule you may not.

### Shape of the props

**1. An atom takes the domain object, not five scalars.** `<StatusBadge def={…} />`, not `label` +
`variant` + `icon`. A caller holding the pieces can combine pieces that do not belong together — a
"Delivered" label wearing the destructive variant — and no type catches it. Where the answer depends
on several fields at once, take the record: `deliveryOutcome(feedback)` reads channel, state and
reason together, because a state without its channel can produce a sentence that never happens.
`Pick<WireType, …>` for the prop type, so every read model that has those fields fits as it is. This
also catches the quieter version: four scalars that are four spellings of one identity —
`OccasionLifecycle` took `idPrefix`, `groupId`, `occasionLabel` and `errorId`, all derivable from the
occasion's position, and now takes an `occasion`. *"Many props is a signal that a component is solving
too many problems or is too opinionated"* —
<https://github.com/Shopify/polaris/blob/main/polaris.shopify.com/content/contributing/components.mdx>

**2. One canonical registry per enum, and no component defines its own copy.** The unit is the enum,
not the file: `review-status-defs.ts` correctly holds two registries for two different enums
(`REVIEW_STATUS_DEFS`, `SUMMARY_POST_DEFS`). Each is `{ label, icon, badgeVariant, description }` per
value, as a total `Record` over the generated wire union, so a value the server adds fails
`typecheck:webapp` rather than rendering blank. Badges, facet options, select items and empty states
all read that one entry. A second copy of an enum's labels is how a filter dropdown ends up as grey
text beside a table of coloured tags. `icon` is required, not optional. The normative rule is WCAG 2.2 SC 1.4.1: *"Color is not used as
the only visual means of conveying information, indicating an action, prompting a response, or
distinguishing a visual element."* Its Understanding page adds, informatively, that where content
relies on differentiating a colour *"an additional visual indicator will be required regardless of
the contrast ratio between those colors"* — so contrast is not the escape hatch. Within one enum no
two entries may share an icon either, because two values that look identical are one value.
<https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html>

**3. Make impossible states unrepresentable: an async surface takes one discriminated-union `state`
prop.** `{ status: "loading" } | { status: "error"; … } | { status: "empty"; filtered: boolean } |
{ status: "ready"; … }`. The container turns query flags into one value; the presentational component
renders one branch. Never pass `isLoading` and `items` and `error` as parallel props — that shape can
express "loading with an error and three rows", and a story then has to reproduce a combination
production never sends. *Every branch a query can actually reach must be in the union* — a `loading |
empty | ready` union with no error branch is this rule broken while appearing to follow it, and is
exactly why `FeedbackResultsState` is **not** the exemplar to copy.
<https://react.dev/learn/choosing-the-state-structure>

**4. Enum over boolean, and separate components over flag arguments.** Two booleans are four states,
of which you render two. `variant="compact" | "full"` reads at the call site; `compact` +
`showHeader` does not, and grows a third boolean next quarter. When the flag makes the component do a
different job, ship two components instead — a caller that must pass a literal `true` to pick the
behaviour is asking for a different function. Boolean visibility props are where
configuration collapses (<https://nathanacurtis.substack.com/p/configuration-collapse>). The one
exception is a flag a caller *derives* rather than types — Fowler's own concession
(<https://martinfowler.com/bliki/FlagArgument.html>).

**5. The accessible name is part of the props type.** A component with no visible label must *require*
`aria-label` or `aria-labelledby` in its props — not accept it, require it — so a caller cannot ship
an unnamed control. The same goes for a name that has to disambiguate two instances on one screen:
`OccasionLifecycle` takes the occasion because two occasions otherwise present two identically named
groups. <https://react-aria.adobe.com/quality>

**6. `className`, the remaining DOM props and `ref` reach the root element, always.** A stability
contract, not a configuration knob: a screen that needs one margin here must not have to fork the
component. **Exempt from rule 12** — these do not need two callers, and they do not get deleted for
having one. `React.ComponentProps<"div">` (or of the primitive being wrapped), minus the props you
own, is the type. <https://github.com/carbon-design-system/carbon/blob/main/docs/style.md>

### Composition

**7. Slots go through Base UI's `render=`, never `asChild`.** This kit is Base UI (`@base-ui/react`),
not Radix. `<Item render={<Link to="…" />}>`, `<PopoverTrigger render={<Button …/>} />`. Anything
copied from a Radix-based registry — including most "shadcn Timeline" snippets — will not drop in;
port the markup and rewire the slot.

**8. A slotted element keeps four obligations, and syntax is the easy one.** The primitive hands you
its behaviour and steps back; what it hands over is *unrendered*, so your element must (a) forward
its `ref`, (b) spread **every** prop it receives onto the real DOM node — dropping `aria-*`,
`role`, `id` or the handlers is how a trigger stops announcing its popup — (c) render exactly one
root element, never a fragment, and (d) stay the element type the primitive expects, since a `div`
where a `button` was expected loses Enter/Space and the tab stop. Radix puts it plainly: *"it is your
responsibility to ensure it remains accessible and functional"*.
<https://base-ui.com/react/handbook/composition>,
<https://react-aria.adobe.com/customization>,
<https://www.radix-ui.com/primitives/docs/guides/composition>

**9. Pass JSX as `children` before reaching for a prop, and for a prop before reaching for context.**
A `title` prop that only ever receives a `<span>` should have been `children`; a "props drilled three
levels" problem is usually one `children` away from not existing. Context is the last step, not the
first — it is for what genuinely has no owner in the tree.
<https://react.dev/learn/passing-data-deeply-with-context>

**10. Make the common case configurable and the uncommon case composable — and know where to stop.**
The ladder runs primitives → composed parts → a configured component; move up it only when a real
second caller disagrees. The counterweight is honest: every step toward composition moves the
accessibility work onto the consumer, and a kit of parts with no configured default is a kit where
every screen re-derives the same aria wiring slightly differently. If it is a decision the product
makes once, configure it. Curtis names the target directly — *"Make the common configurable, make the
uncommon composable"* (<https://nathanacurtis.substack.com/p/configuration-collapse>); Capozzi lists
the accessibility burden as a cost of composition
(<https://maecapozzi.com/blog/composition-vs-configuration/>), as does Atlassian, which reaches for a
pre-built component before a primitive for exactly that reason
(<https://atlassian.design/get-started/develop/composition/>).

**11. Controlled or uncontrolled is a decision you state, not one you leave to the reader.** Default
to **controlled** for anything whose value the URL, a form, or a server mutation also holds — which
is nearly everything we own. Use uncontrolled with a `defaultValue` only for state that never leaves
the component. Never both: a `value` that is ignored unless `onChange` is present is a bug waiting for
its story. Say which one it is in the component's doc comment, and name the pair `value`/`onValueChange`
+ `defaultValue`, matching Base UI. <https://react.dev/learn/sharing-state-between-components>

### House policy

**12. A prop needs two real call sites, or it dies (house policy).** No design system publishes a
number; two is ours. One caller usually means the value belongs inline at that caller, where it can
be read. A `variant`, a `display`, a `size` earns its place when two screens genuinely disagree —
otherwise it is a fork you pay to keep open in every story, snapshot and type. The same applies to a
whole component: delete it and inline it. **Two carve-outs.** Rule 6's `className`/`...rest`/`ref` are
a contract, not a knob, and are exempt. And one caller justifies a prop when that caller *derives* the
value from data rather than typing a literal — deleting it then just moves the branch somewhere worse.
<https://kentcdodds.com/blog/inversion-of-control>,
<https://martinfowler.com/bliki/FlagArgument.html>

**13. Badge the exception, not the norm (house policy).** A signal-to-noise heuristic, not received
practice: a badge on every row colours the baseline and hides the one row that is different.
`ObservationOriginBadge` renders nothing for `LIVE`; `ClaimCurrentnessBadge` renders nothing for
`CURRENT`; a tally of five delivery outcomes is a sentence, not five badges. **Rendering nothing must
not make the fact unavailable** — if the norm is something a reader needs, it stays in the row's
accessible text (a `sr-only` span, a `title`, or the surrounding sentence). "Silent because it is
ordinary" is a visual decision; it is not permission to drop the information. The words in whatever
does render still come from the registry — a count reading "not delivered" beside a badge reading
"Withheld" is rule 2 broken by the back door.

**14. A hover card carries supplementary content only, on a link whose destination is a superset of
the card.** It must never hold the only copy of a fact, or the only control that reaches one: hover is
unavailable on touch and awkward on keyboard. The normative rule is WCAG 2.2 SC 1.4.13 — content shown
on hover or focus must be **dismissible** (*"A mechanism is available to dismiss the additional content
without moving pointer hover or keyboard focus, unless the additional content communicates an input
error or does not obscure or replace other content"*), **hoverable** (*"the pointer can be moved over the
additional content without the additional content disappearing"*) and **persistent** (*"remains visible
until the hover or focus trigger is removed, the user dismisses it, or its information is no longer
valid"*). Everything in the card must also be on the page the link goes to.
<https://www.w3.org/WAI/WCAG22/Understanding/content-on-hover-or-focus.html>

### Before you build anything

Check `src/components/ui/` for what actually exists. The shadcn registry has no `timeline`, no
`stepper` and no `data-table` — three steps of a vertical rail is ~30 lines of border and rounded
spans (`DeliveryTrace.tsx`), not a dependency. And check the kit's own patches before working around
one: `toggle-group.tsx` and `field.tsx` are vendored *and ours*, so an upstream defect gets fixed
there once rather than hand-rolled at each call site.

## Routing (TanStack Router)

```typescript
// src/routes/users.$userId.tsx
import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/users/$userId")({
  loader: async ({ params, context }) => {
    // Use generated query options
    return context.queryClient.ensureQueryData(
      getUserOptions({ path: { id: params.userId } })
    );
  },
  component: UserPage,
});

function UserPage() {
  const user = Route.useLoaderData();
  return <UserProfile user={user} />;
}
```

### Rules

- Define routes with `createFileRoute`
- Keep loaders side-effect free
- Use router context for shared data (query client, auth)
- Never hand-edit `routeTree.gen.ts`

## Role-Based Gating (OWNER > ADMIN > MEMBER)

Client-side gating is a UX affordance only — the server enforces authorization on every
endpoint. Never re-invent `role === "ADMIN"` checks; use the shared pieces:

- **Whole admin surfaces — gate by placement, not by a check.** Put workspace-admin pages under
  `src/routes/_authenticated/w/$workspaceSlug/admin/`; its `route.tsx` layout carries the
  `beforeLoad` role guard, so every route in the directory inherits it. A file that maps to an
  `/admin` URL without nesting under that layout silently skips the gate — that is the bug class
  `admin/-route.test.ts` exists to catch, by driving every admin URL in the generated route tree
  through the real router as a MEMBER (the leading `-` marks the file as a test rather than a
  route). Run it with `pnpm run test:webapp`; do not weaken it.
- **Individual controls**: `useWorkspaceAccess()` returns `role` and `isAdmin`; the role math is
  `hasMinimumWorkspaceRole` (`src/lib/workspace-roles.ts`). Pure role predicates live in
  `src/lib/`; QueryClient-coupled resolvers (`resolveWorkspaceMembership`,
  `workspaceMembershipQueryOptions`) live in `src/integrations/auth/guard.ts`. Fetch membership
  only via `workspaceMembershipQueryOptions` so every caller shares one cache entry and one
  `staleTime`.
- **Hide rather than disable** — for permissions specifically. Disabling is the better default for
  a control the user could still unlock, but ["hiding is recommended in cases where the user will
  never be able to use that feature due to their role or license"](https://www.uxtigers.com/post/inactive-buttons),
  which is this case. A disabled control would also be a poor explanation: a native `disabled`
  button is unreachable by keyboard, so a tooltip saying why can never be read.
- **Workspace role ≠ instance role.** `useWorkspaceAccess().isAdmin` is membership in *this*
  workspace; `useAuth().isAppAdmin` is instance-wide (ADR 0017). They are separate axes — a
  workspace-role gate on a surface that has no active workspace is always false.
- There is **no `<RequireRole>` wrapper component today**: route placement covers whole surfaces
  and a boolean from the hook covers the one control that needs it (`AppSidebar`'s admin nav), so
  a wrapper would be a third way to say the same thing. Add one when a real call site needs it.
  When a role-assignment UI lands, its mutation must invalidate the membership query key.

## Styling (Tailwind CSS v4)

```typescript
// Use utility classes in JSX
<div className="flex items-center gap-4 bg-surface text-foreground">

// Compose with clsx/tailwind-merge for conditional classes
import { cn } from "@/lib/utils";

<button className={cn(
  "px-4 py-2 rounded",
  isActive && "bg-primary text-primary-foreground",
  isDisabled && "opacity-50 cursor-not-allowed"
)}>
```

### Design Tokens

Prefer semantic tokens over hard-coded values:
- `bg-surface`, `bg-background`
- `text-foreground`, `text-muted`
- `border-border`

Tokens defined in `src/styles.css`.

## Testing (Vitest + Testing Library)

```typescript
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

it("submits form on click", async () => {
  const user = userEvent.setup();
  const onSubmit = vi.fn();

  render(<MyForm onSubmit={onSubmit} />);

  await user.type(screen.getByLabelText(/email/i), "test@example.com");
  await user.click(screen.getByRole("button", { name: /submit/i }));

  expect(onSubmit).toHaveBeenCalled();
});
```

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
an assertion out of a story and into a route test is exactly how this bites.

### Query Priority

`getByRole` > `getByLabelText` > `getByText` > `getByTestId`

### Mock TanStack Query

```typescript
vi.mock("@/api/@tanstack/react-query.gen", () => ({
  getUserOptions: () => ({
    queryKey: ["users", "1"],
    queryFn: () => Promise.resolve(mockUser),
  }),
}));
```

## Storybook

Stories double as Chromatic visual tests. Co-locate with components:

```typescript
// src/components/Button.stories.tsx
import type { Meta, StoryObj } from "@storybook/react";
import { Button } from "./Button";

/**
 * Primary button component for user actions.
 * Supports multiple variants and sizes.
 */
const meta = {
  component: Button,
  parameters: { layout: "centered" },
  tags: ["autodocs"],
  args: { children: "Click me" },
} satisfies Meta<typeof Button>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Default button appearance */
export const Default: Story = {};

/** Destructive action button */
export const Destructive: Story = {
  args: { variant: "destructive" },
};

/** Loading state */
export const Loading: Story = {
  args: { loading: true },
};
```

`satisfies Meta<typeof Button>` is **enforced**, not just conventional: `.biome/typed-story-meta.grit`
fails `check:webapp` on a meta that names a `component` but is typed as a bare `Meta`, because that
type parameter is what checks `args` against the component's real props. A gallery meta that names no
component — several components documented on one page — is the one case bare `Meta` is correct, and
the rule leaves it alone.

### Titles and the sidebar

Two conventions live side by side, and which one applies is a property of the component, not a taste:

- **Omit `title` by default.** `.storybook/main.ts` sets no `titlePrefix`, so a story with no title is
  filed by its path under `src` — `components/admin/ai/ModelPicker`. Most stories should be here: the
  file tree *is* the grouping, and it cannot go stale.
- **Declare a `title` when the file layout cannot express where a reader looks for the thing.** A
  product surface assembled from several directories, or one an admin knows by the screen it is on,
  earns an explicit title: `Workspace admin/Practices/Review/How much`. Do not rename an existing
  explicit tree into auto-titles — the path would file it under `components/`.
- **Sentence case throughout**, for both segments and story names: `Practice trace/Outcome badge`,
  not `PracticeTrace/OutcomeBadge`. A leaf named after its component is still sentence case —
  `Common/Filter toolbar`, not `Common/FilterToolbar`. Product terms and acronyms keep their capitals
  (`AI mentoring`, `Hephaestus default panel`).
- **There are exactly two admin consoles, so there are exactly two admin namespaces.**
  `Workspace admin/…` for anything reached under `w/$workspaceSlug/admin/**`, `Instance admin/…` for
  anything under the `isAppAdmin` routes in `_authenticated/admin.*`. Never a bare `Admin/…`: the
  reader cannot tell which console it is, and every file that used to sit there was a workspace
  surface. A presentational component **both** consoles render belongs to neither — file it under
  `Shared/…`, as `Shared/Practice catalog/Area visual picker` already does.
- **A leaf and a folder must not share a name.** If `Foo` gains children, the leaf becomes
  `Foo/Overview`.
- **Every top-level segment must appear in `storySort.order` in `.storybook/preview.ts`.** One that
  is missing sorts alphabetically after every named one, which silently buries it.
- **A cross-cutting regression suite is not a component.** A file with no `component`, covering
  several primitives at once, belongs under `Tests/` — see `src/components/ui/overlay-reflow.stories.tsx`.

### Args, Controls and play functions

- **A `render` that ignores `args` disables the Controls panel**, which is most of a story's
  documentation value. Prefer `args` alone; when a story needs a wrapper, spread:
  `render: (args) => <Harness {...args} />`. Keep at least one story per file driven by meta args
  (`export const Default: Story = {};`).
- **`autodocs` publishes the `component`'s props.** If the stories render a test harness rather than
  the component, either point `component` at the real component or drop `autodocs` — do not publish
  the harness's props as if they were the API.
- **An expectation must not be recomputed from what it is checking.** Writing
  `expect(rows).toHaveLength(FIXTURE.length)`, or rebuilding the component's own branching to derive
  the URL it should have produced, makes a wrong component and a wrong test agree. Write the expected
  values out; if the literal table risks drifting, assert that its *keys* match the source's.
- **Use the `canvas` play argument** rather than re-deriving `within(canvasElement)` in every story.
- **`getBy*` is already the assertion.** It throws when it finds nothing, so
  `expect(canvas.getByRole("button")).toBeInTheDocument()` — or `.toBeTruthy()` — adds a matcher that
  can only ever run against an element that exists. Write the query on its own line. This is enforced:
  `.biome/no-redundant-in-the-document.grit` is a Biome plugin that fails `pnpm run check`, so CI
  catches a reintroduction. Absence still needs `expect(queryBy(…)).not.toBeInTheDocument()`, and
  `await findBy*` is its own assertion — the rule leaves both alone.

### JSDoc on stories

Storybook renders these blocks as Markdown (`markdown-to-jsx`). Separate paragraphs with a blank
comment line — a Java-style `<p>` emits a stray empty paragraph before each one.

A block above `meta` or above an exported story is **published prose**, not a code comment: with
`autodocs` it is the Docs page. So it earns its place only if it records a rejected alternative, a
real trap, or a why the reader cannot derive — and it is addressed to somebody reading the component,
not to somebody reading the test. Restating the story's name is the common failure (`/** Moving an
area's worth in one action */` above `BulkSet`); notes about how the assertion reaches the DOM are
the other, and those belong in a `//` inside the play function.

If the file has no `autodocs`, none of it renders. Either say why in the meta block — see
`SortableCatalogTree.stories.tsx`, which opts out because the stories render a harness — or turn
`autodocs` on, so the thing worth writing down is actually published.

### Story Requirements

Cover for each component:
- Default state
- All variants
- Loading state
- Error state
- Edge cases (empty, long content, etc.)

Use play functions for interaction testing.

### Play functions: portals and transitions

Dialogs, popovers, selects and toasts render into a portal, so they are on `document` and not in the
story canvas — query them with `screen`, not `within(canvasElement)`.

Do not reach for a bare `toBeVisible()` on an overlay you have just opened. Base UI mounts the panel
with `data-starting-style` and clears it a frame later, so for that one frame the panel computes to
`opacity: 0` and a mounted element reads as invisible. This is not an animation *duration* problem —
the Playwright context already requests `reducedMotion: "reduce"`, the media query matches, and
forcing every duration to 1ms does not fix it.

Use `expectSettledVisible` from `@/test/overlay`, which waits for the starting-style frame to pass
and for the enter transition to finish before asserting. It takes the element you actually care
about, not the panel: the assertion target is usually a `<dt>` or a `<p>` well inside the popup, and
it is the *ancestor* that is transparent, so the helper looks upward for both signals. Reach for
`settledPopup()` from the same module when you need the panel itself — the measuring assertions do,
because a mid-flight `scale(.95)` would let a too-wide popup pass.

## Accessibility

- Follow shadcn/ui accessibility patterns
- Keep ARIA roles aligned with design
- Manage focus on dialog open/close
- Provide keyboard shortcuts via hooks
- A field marked `aria-invalid` also points `aria-describedby` at the element carrying its message.
  `aria-invalid` announces *that* a field is wrong and never *why*, so a reader tabbing back to it
  hears "invalid" alone (WCAG 2.2 SC 3.3.1). Use `aria-describedby`, not a live region — a live
  region re-announces on every keystroke.

## Generated Files (Do Not Edit)

| Path | Regenerate With |
|------|-----------------|
| `src/api/**/*` | `pnpm run generate:api:application-server` |
| `src/routeTree.gen.ts` | TanStack Router plugin (automatic) |

Regenerating **empties** `src/api/`, so nothing hand-written survives there — not even a test about
the generated client. `src/test/response-transformers.test.ts` lives outside that directory for
exactly this reason.

### Dates from the API

A `format: date-time` field is a real `Date` by the time a component sees it: `openapi-ts.config.ts`
sets `transformer: true` on the `@hey-api/sdk` plugin, which wires the generated response
transformers into every SDK call. Two consequences worth knowing:

- **Fixtures passed straight to a prop use `new Date(…)`** — that is the shape production sends.
  Fixtures served through **MSW** are JSON and use ISO strings; type those with `Wire<T>` from
  `@/lib/dates`, which turns every nested `Date` in a generated view into a `string`.
- **`asDate()`** (also `@/lib/dates`) is for values that did *not* come through the generated SDK —
  the Mentor SSE stream is hand-parsed in `use-mentor-chat.ts` because its operation is excluded
  from generation — and for optional fields, since it returns `undefined` instead of an Invalid Date.

The `transformer: true` setting is load-bearing and invisible to `tsc`: without it the types still
say `Date` while the client hands back strings, which is how `.toLocaleDateString()` once shipped a
crash. `src/test/response-transformers.test.ts` is the guard; it calls the real SDK, so it fails
whenever that wiring is lost.

## Available Skills

| Skill | When to Use |
|-------|-------------|
| `/composition-patterns` | Refactoring boolean prop proliferation, compound components |
| `/web-design-guidelines` | UI accessibility review, UX patterns |
| `/react-best-practices` | Performance optimization (~40% Next.js-specific, check applicability) |

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Edited generated file | Discard, run `pnpm run generate:api:application-server` |
| Route not appearing | Check file naming: `my-route.tsx` with `createFileRoute` |
| Added `useMemo` | Remove—React Compiler handles memoization |
| Manual `queryKey` array | Use generated `...Options()` helpers |
| Type error after API change | Run `pnpm run generate:api:application-server` |
