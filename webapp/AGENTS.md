# Webapp

React 19 single-page application with TanStack Router/Query and Tailwind CSS.

## Commands

| Task | Command |
|------|---------|
| Dev server | `npm run dev` (port 4200) |
| Build | `npm run build:webapp` |
| Type check | `npm run typecheck:webapp` |
| Lint + format | `npm run check:webapp` |
| Tests | `npm run test:webapp` |
| Storybook | `npm -w webapp run storybook` |
| Regenerate API | `npm run generate:api:application-server` |

## Boundaries

### Always
- Run `npm run check:webapp` before committing
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
- **Components** (`src/components/**`): Pure, rely solely on props

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

### Story Requirements

Cover for each component:
- Default state
- All variants
- Loading state
- Error state
- Edge cases (empty, long content, etc.)

Use play functions for interaction testing.

## Accessibility

- Follow shadcn/ui accessibility patterns
- Keep ARIA roles aligned with design
- Manage focus on dialog open/close
- Provide keyboard shortcuts via hooks

## Generated Files (Do Not Edit)

| Path | Regenerate With |
|------|-----------------|
| `src/api/**/*` | `npm run generate:api:application-server` |
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
| Edited generated file | Discard, run `npm run generate:api:application-server` |
| Route not appearing | Check file naming: `my-route.tsx` with `createFileRoute` |
| Added `useMemo` | Remove—React Compiler handles memoization |
| Manual `queryKey` array | Use generated `...Options()` helpers |
| Type error after API change | Run `npm run generate:api:application-server` |
