---
"hephaestus": minor
---

The instance-admin console now opens on an **overview dashboard** instead of a blank page: whether
delivery is running, how many workspaces and memberships the instance has, and the latest
authentication activity — each tile linking to the page that manages it. The sidebar is grouped
(Access, Practices, AI, Operations) rather than one flat list.

A new **Instance settings** page carries the emergency **silent mode** switch. While it is engaged,
Hephaestus posts nothing outward anywhere on the instance — no practice feedback on pull requests,
merge requests or issues, no Slack messages, not even the 👀 acknowledgement on a `/hephaestus review`
comment — and a banner across the console says so, naming who engaged it and why. Engaging takes one
click and an optional reason; releasing asks you to type "release", because resuming delivery for
every workspace at once deserves more thought than pausing it. Both directions are recorded on the
audit log.

Silent mode holds feedback back rather than throwing it away: reviews keep running and their findings
are still saved and marked as withheld, so nothing is lost — but they are not posted retroactively
when you release it. Workspace settings are untouched throughout and apply again immediately.
