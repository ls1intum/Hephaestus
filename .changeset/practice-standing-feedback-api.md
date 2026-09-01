---
"hephaestus": minor
---

The application API can now answer where a developer stands in each practice group: the current standing and its guidance, how the group has developed across recently reviewed work, which kinds of work contributed feedback, a filterable observation history, and the complete review runs behind it. An undecided observation remains visible in that history without being presented as a verdict. Developers can also replace or delete their response to delivered feedback, recording whether it was helpful, how they handled it, and an optional explanation. Every endpoint answers only for the signed-in developer.

**Operators:** direct API callers must replace `/practice-areas` with `/practice-groups`, use the corresponding group schema and field names, replace `/practices/learner` with `/practices/reviewed`, and move from the older reaction endpoint to the combined response endpoint. Existing response history is preserved; `MIGRATION.md` lists the contract changes. The generated Hephaestus web client is updated in the same release.
