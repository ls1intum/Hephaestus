---
"hephaestus": minor
---

The application API can now answer where a developer stands in each practice area: the current standing and its guidance, how the area has developed across recently reviewed work, which kinds of work contributed feedback, a filterable observation history, and the complete review runs behind it. An undecided observation remains visible in that history without being presented as a verdict. Developers can also record whether delivered feedback was helpful, how they handled it, and an optional explanation, or withdraw the response. Usefulness and resolution retain their histories independently, and a comment stays with the answer it explained. Every endpoint answers only for the signed-in developer.

**Operators:** the older reaction endpoint on delivered feedback is replaced by the combined response endpoint. Stored reactions need no migration; only a direct API caller has to be repointed, as described in `MIGRATION.md`. The Hephaestus web app is unaffected.
