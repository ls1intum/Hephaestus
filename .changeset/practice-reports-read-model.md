---
"hephaestus": minor
---

Developers can now read a practice report: per-practice cards showing why a practice matters, what good looks like, where they stand, the specific feedback to act on, and what they already do well — the same feedback that arrives in pull requests and from the mentor, reorganised for reflection.

Workspace admins get a matching roster of everyone with recent activity, sorted so people who may need support come first, and a per-developer drill-down showing exactly the cards that developer sees. Nothing on these surfaces ranks people against each other: there is no score, no rank and no total, only where someone stands against each practice's own standard and how that has moved since the previous window.

A new workspace health view shows, per practice area, how many developers stand at each status. It never names anyone, and counts are withheld whenever publishing them could identify individuals — when the group is too small, or when everyone sits at the same status. Admins and owners see it by default.

Opening someone else's report is recorded on an append-only trail, and **you can read your own copy of it**: the data export in your account settings now lists every time an admin or owner opened your report or the roster you were on — when, in which workspace, and who did it. Reading your own report records nothing. Nothing in the product shows what a given administrator has been reading; the audience for these records is the person they are about. The trail is kept for 12 months and is erased when a workspace is purged or a person is deleted.

**Operators:** a report covers the last 28 days by default. Set `PRACTICE_REPORT_WINDOW_DAYS` higher for a low-volume workspace whose reports come out thin. Opening the health view to all members is a workspace setting (`healthVisibility`) that is available on the API ahead of the screens that render it.
