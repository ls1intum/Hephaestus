---
"hephaestus": minor
---

The application API can now answer where a developer stands in each practice area: the current standing and the guidance behind it, how the area has developed across recently reviewed work, which kinds of work the feedback came from, a filterable list of that feedback, and the complete review moments that produced it. It also accepts one combined response to delivered feedback: whether it was helpful, what the developer decided to do with it, an optional explanation, and the option to take the whole answer back. The two questions are answered independently and each keeps its own history, so rating a piece of feedback helpful later does not erase a dispute recorded earlier. Every one of these answers only for the signed-in developer. There is no way to request another person's standing. Nothing changes in the app yet; the screens that read this arrive separately.

**Operators:** the older reaction endpoints on practice observations are removed in favour of the combined one. Stored reactions are untouched and need no migration; only a direct API caller has to be repointed, and `MIGRATION.md` names the replacement. The Hephaestus web app is unaffected.
