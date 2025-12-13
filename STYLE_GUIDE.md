# Style Guide

## Philosophy

- Immutable by default
- Early exit, flat code
- One reason to change per unit
- Composition over inheritance

## General

- Single responsibility per function/component
- Guard clauses at the top, happy path at the bottom
- Flat is better than nested
- Explicit is better than clever
- Delete code before you write code

## Absolutes

- **NO** `else` statements — use early returns
- **NO** `any` type — use `unknown` if truly dynamic
- **NO** `let` — use `const`
- **NO** nested ternaries
- **NO** try/catch except at error boundaries
- **NO** magic strings/numbers — use constants
- **NO** commented-out code — delete it
- **NO** `console.log` in production code

## TypeScript / React

- Functional components only
- Props via single object, not destructured in signature
- Callbacks sync unless truly async
- `mutate()` over `mutateAsync()`
- Direct prop spread over destructure-reassemble
- Single-word variable names where clear
- Colocate tests and stories with source
- Semantic HTML: `<section>`, `<article>`, `aria-*`
- `interface` for props, `type` for unions

## TanStack Query

- `onSuccess`/`onError` for cache updates and toasts
- Optimistic updates via `onMutate` + rollback
- Invalidate queries after mutations
- Short mutation names: `syncMutation` not `syncSlackConnectionMutation`

## Java / Spring

- Constructor injection only
- Records for DTOs
- Optional for nullable returns
- `@RestControllerAdvice` for global exception handling
- No `else` after `return`
- No verbose Javadoc on obvious methods
- Controller → Service → Repository, keep controllers thin

## Naming

- Components: `PascalCase`
- Functions/variables: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Files: `kebab-case.tsx` or `PascalCase.tsx` (match component)
- Single-word names when unambiguous: `query`, `mutation`, `status`

## Testing

- Colocate with source: `*.test.ts` next to `*.ts`
- One assertion per test when possible
- Descriptive test names: `it('returns null when disabled')`
- No mocking what you don't own

## Storybook

- Minimal config — autodocs handles most
- One story per meaningful state
- No verbose argTypes
- `fn()` for action props
