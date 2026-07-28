---
"hephaestus": minor
---

The audit log now shows when an instance administrator opened a workspace they are not a member of.
Reaching a workspace through instance-admin elevation was previously indistinguishable from a member
opening their own workspace, so the one access worth reviewing looked like ordinary traffic. These
entries appear as **Workspace elevation** under Administration → Audit log and can be filtered for
directly. Repeat visits within a short window collapse into one entry, so browsing a workspace does
not flood the trail.
