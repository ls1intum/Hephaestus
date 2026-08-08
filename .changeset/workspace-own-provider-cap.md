---
"hephaestus": minor
---

Workspace administrators can now cap what their own connected AI provider spends each month, under
Administration → "AI usage". It is their own money, so it is theirs to set, change, or remove — and it
is separate from the budget the instance administrator funds and sets for shared models. The two
never add up and never pause each other: if the shared-model budget runs out, work on the
workspace's own provider keeps running, and vice versa.

The usage page now shows each cap on its own meter, warns at 80% with a projection of when this
month's pace would reach it, tells you whose cap paused what and who can lift it, and reports the
average cost per review or mentor turn alongside the monthly totals. Raising or removing either cap
now resumes the work it paused within about a minute, instead of leaving it queued for up to an
hour. The instance overview gains a read-only column showing which workspaces have capped
themselves, how much of each cap is used, and which cap paused a workspace.

The budget an instance administrator sets for a workspace bounds only work on *shared* models — the
spend the instance is billed for. Work a workspace pays for through its own connected provider is
bounded by that workspace's own cap. Neither cap is a way to stop all AI work in a workspace; the
workspace's status and its feature switches are.
