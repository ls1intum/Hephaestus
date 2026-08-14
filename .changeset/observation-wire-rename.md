---
"hephaestus": minor
---

Practice review screens now call a recorded review result an **observation** and the guidance written from it **feedback**. The words *finding* and *message* are gone from every screen, heading, filter and empty state under Practice reviews, so one thing is no longer named two ways depending on which page you are on.

The workspace-admin review API follows the same wording: `/practices/reviews/findings` is now `/practices/reviews/observations`, and the `findingId`, `findingCount` and `findings` fields are now `observationId`, `observationCount` and `observations`. No action on upgrade — the Hephaestus web app is this API's only client and ships in the same release. If you call these endpoints from your own tooling, use the new names.
