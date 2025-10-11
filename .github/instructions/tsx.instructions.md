---
applyTo: "**/*.ts,**/*.tsx"
---

# TypeScript + React project standards

Follow the [general coding guidelines](./general-coding.instructions.md) in addition to the rules below.

## TypeScript
- Ship new code as TypeScript. Reach for `type` aliases when composing shapes and `interface` when you need declaration merging or extension.
- Default to `const`, mark collections `readonly` when practical, and avoid mutating function arguments.
- Rely on optional chaining (`?.`) and nullish coalescing (`??`) when working with generated API responses.
- Export prop and return-value types so other modules can reuse them (for example `export interface MentorCardProps`).
- Import with the `@/*` alias defined in `tsconfig.json`. Keep relative paths shallow.
- Use discriminated unions or `zod` schemas for request and response guards. Assert intent with the `satisfies` operator instead of broad casts.
- Model async flows with `PromiseSettledResult` helpers and exhaustively handle states.

## React fundamentals
- Author components as named functions (`export function Component(props: ComponentProps)`). Avoid `React.FC`; annotate props explicitly.
- Obey the [Rules of Hooks](https://react.dev/reference/rules). No conditional hook calls or hook invocations inside loops.
- Keep render logic pure—never mutate stores, perform navigation, or touch the DOM during render.
- Split responsibilities: container routes in `src/routes/**` perform data fetching, auth guards, and side effects; presentational components in `src/components/**` stay pure and rely solely on props.
- Prefer named exports for components and hooks so tree shaking stays predictable.

## Data, state, and networking
- Fetch data with TanStack Query v5. Spread the generated option helpers from `src/api/@tanstack/react-query.gen.ts` into `useQuery`/`useMutation` so request typing, retries, and query keys stay in sync with the backend. See `useDocumentArtifact.ts` for a full example.
- Use the shared `QueryClient` provided by `src/integrations/tanstack-query/root-provider.tsx`. Invalidate with the generated `...QueryKey()` helpers instead of literal arrays.
- Keep feature state in the relevant Zustand store under `src/stores/**` and subscribe with selectors to avoid broad rerenders.
- Reuse the preconfigured OpenAPI `client` (set up in `main.tsx`) rather than introducing alternative HTTP clients.
- Derive loading and error states from TanStack Query instead of duplicating flags in local state.
- Persist long-lived state with Zustand middleware only when the UX needs it. Document persisted keys in the store file.

## Routing and layout
- Define routes with `createFileRoute` and file-based routing. Use router loaders for redirects and pre-navigation checks rather than manual `useEffect` navigation.
- Pass shared context (query client, auth data) through the router context instead of importing singletons into components.
- Co-locate `Route.useLoader` logic with the route file and keep loaders side-effect free.
- Wrap route-level suspense boundaries with design tokens from `src/components/ui/**` instead of one-off spinners.

## Styling and UI kit
- Tailwind CSS 4 is the default styling system. Keep utility classes in JSX and rely on the token scale defined in `src/styles.css`/`tailwind.config`.
- Build new primitives on top of the shadcn/ui components under `src/components/ui/**` before adding external UI libraries.
- Compose class names with `clsx` or `tailwind-merge` helpers when props influence styling. Avoid bespoke string concatenation.
- Prefer design tokens like `bg-surface` and `text-muted` over hard-coded hex values.

## Tooling integrations
- Keep stories colocated (`Component.stories.tsx`) and represent the real UI states that Chromatic validates.
- The React Compiler is enabled through `babel-plugin-react-compiler`. Write pure components, avoid conditional hooks, and remove hand-written memoization unless profiling demands it. Use `'use no memo'` only while debugging.
- Format and lint with Biome (`npm run check`) before pushing. Fix warnings or explain them in the pull request.
- Keep Vitest configs minimal. Add path-specific overrides in `vitest.workspace.js` when a suite needs a browser runtime.

## Testing and stories
- Write component tests with Vitest and `@testing-library/react`. Prefer queries that mirror user intent such as `findByRole` or `getByLabelText`.
- Mock network requests with `vi.mock` plus the generated TanStack Query helpers. Do not hand-roll fetch mocks.
- Cover happy path, loading, and error outcomes in both tests and stories. Stories double as Chromatic snapshots.
- Use Storybook play functions for interactive flows instead of end-to-end tests when the surface is presentational.

## Accessibility and UX
- Follow the shadcn/ui accessibility patterns. Keep ARIA roles aligned with design reviews.
- Manage focus on dialog open and close. Use the provided primitives for traps and focus return.
- Provide keyboard shortcuts through hooks under `src/hooks/**` and document them in the component stories.

## Performance and telemetry
- Memoise derived values only after measuring with the React Profiler. Trust the React Compiler for common cases.
- Defer analytics to an effect or a TanStack Query observer so renders stay pure. Reuse the shared PostHog client.
- Use Sentry error boundaries around async routes and surfaces that depend on remote data.

### Using the generated TanStack Query client

```ts
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
