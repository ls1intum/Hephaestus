---
"hephaestus": patch
---

The two AI cost pages now use one vocabulary. The number a host grants a workspace is called
**shared-model budget** everywhere — in the instance console's table, its row action, and the dialog
that edits it. Previously one click path called it four different things ("Set instance cap" → "Set
shared-model budget" → "Save budget" → "Remove cap").

Cost figures now say **run** rather than "call" or "event", which is what they actually count: an
un-priced review shows as "2 runs aren't counted in these totals", and the breakdown tables have a
"Run type" and a "Runs" column.

Other copy is clearer about what to do next:

- When a shared-model budget is reached, the banner now says practice detection and Mentor can keep
  running on your own models, and links straight to AI models.
- "Bound model cannot run" is now "Practice detection's model is unavailable", with a plain reason.
- A workspace's status in the instance table names the money stream that stopped it ("Paused ·
  shared models" / "Paused · own provider") instead of an internal cap name.

The instance console also gained the burn-rate warning the workspace console already had: expanding
a workspace that is past 80% of a budget now shows when this month's pace would reach it.
