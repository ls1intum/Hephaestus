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
├── UI: shadcn/ui + Radix primitives
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
- **Components** (`src/components/**`): Pure by default. A cohesive section may own data used only
  within that section; every story that renders it must mock its requests.

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
