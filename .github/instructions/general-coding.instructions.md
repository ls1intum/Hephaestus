---
applyTo: "**"
---
# Project general coding standards

## Naming Conventions
- Use PascalCase for component names, interfaces, and type aliases
- Use camelCase for variables, functions, and methods
- Use ALL_CAPS for constants
- A leading `_` marks something the language or a tool reads that way, not something private:
  an intentionally unused binding (`no-unused-vars` ignores `^_`), a server field name (`_id`), a
  test hook, a runtime global. Private members carry no prefix.

## Error Handling
- Use try/catch blocks for async operations
- Implement proper error boundaries in React components
- Always log errors with contextual information

## Testing Expectations
- Name Spring integration tests with the `*IntegrationTest` suffix and reuse the shared abstract bases (for example, `AbstractWorkspaceIntegrationTest` + `WebTestClient` for workspace controllers) so security and persistence assertions stay consistent.
