---
"hephaestus": minor
---

Instance administrators can now see what each workspace spent on AI in a given month, and set a
monthly spending cap per workspace. Once a workspace reaches its cap, practice detection and mentor
replies pause for the rest of the month — so one runaway workspace can no longer quietly consume the
whole instance's AI budget — and they resume on their own when the next month begins or when an
administrator raises the cap. Changes to a cap are recorded in the audit log alongside other
administrative changes.

Workspace administrators get a matching view for their own workspace under Administration →
"Usage": total spend for the month, a breakdown by day and by kind of work (pull-request reviews,
issue reviews, conversation reviews, and mentor conversations), and their current cap, which they can
see but not raise. Mentor conversations are included in these totals for the first time. Where a
model has no price on record, the affected calls are counted separately and flagged, so it is clear
when a total is understated rather than silently wrong.
