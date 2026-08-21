---
---

Development tooling only: oxlint replaces Biome's linter over `webapp/`, and Biome keeps the
formatter and import sort. No endpoint, configuration or screen changes for operators or users. Two
shipped files were adjusted to satisfy rules Biome had no equivalent for — the mentor chat hook now
defers two `Date.now()` calls until TanStack Query needs them, and the sync-events stream detaches
its listeners through an `AbortController` — both behaviour-preserving and covered by tests.
