---
applyTo: "**/*.ts,**/*.tsx"
---

# Project coding standards for TypeScript and React

Apply the [general coding guidelines](./general-coding.instructions.md) to all code.

## TypeScript Guidelines
- Use TypeScript for all new code
- Follow functional programming principles where possible
- Use interfaces for data structures and type definitions
- Prefer immutable data (const, readonly)
- Use optional chaining (?.) and nullish coalescing (??) operators

## React Guidelines
- Use functional components with hooks
- Follow the React hooks rules (no conditional hooks)
- Use React.FC type for components with children
- Keep components small and focused
- Use TailwindCSS for component styling
- Use Tanstack Query for data fetching and caching
- Use the generated Tanstack Query @hey-api/openapi-ts API client (src/api/@tanstack/react-query.gen.ts)
- Use Tanstack Router for routing
- Use Shadcn for UI components
- Use Storybook for component documentation
- Components in `src/components/` are purely presentational and should not contain any logic tied to context or state management (fetching, auth, etc.)
- Container components in routes (`src/routes/`) should handle data fetching and application state management

### Tanstack Query @hey-api/openapi-ts API Client

### Queries

Queries are generated from GET and POST endpoints. The generated functions follow the naming convention of SDK functions and append `Options`, e.g. `getPetByIdOptions()`.

```ts
const { data, error } = useQuery({
  ...getPetByIdOptions({
    path: {
      petId: 1,
    },
  }),
});
```

### Infinite Queries

Infinite queries are generated from GET and POST endpoints if we detect a pagination parameter. The generated functions follow the naming convention of SDK functions and append `InfiniteOptions`, e.g. `getFooInfiniteOptions()`.

```ts
const { data, error } = useInfiniteQuery({
  ...getFooInfiniteOptions({
    path: {
      fooId: 1,
    },
  }),
  getNextPageParam: (lastPage, pages) => lastPage.nextCursor,
  initialPageParam: 0,
});
```

Infinite queries are recognized by having one of these keywords in the endpoint's parameters:

- after
- before
- cursor
- offset
- page
- start

### Mutations

Mutations are generated from DELETE, PATCH, POST, and PUT endpoints. The generated functions follow the naming convention of SDK functions and append `Mutation`, e.g. `addPetMutation()`.

```ts
const addPet = useMutation({
  ...addPetMutation(),
  onError: (error) => {
    console.log(error);
  },
});

addPet.mutate({
  body: {
    name: 'Kitty',
  },
});
```

### Query Keys

Query keys are generated for both queries and infinite queries. If you have access to the result of query or infinite query options function, you can get the query key from the `queryKey` field.

```ts
const { queryKey } = getPetByIdOptions({
  path: {
    petId: 1,
  },
});
```

Alternatively, you can access the same query key by calling `QueryKey` or `InfiniteQueryKey` function.

```ts
const queryKey = getPetByIdQueryKey({
  path: {
    petId: 1,
  },
});
```