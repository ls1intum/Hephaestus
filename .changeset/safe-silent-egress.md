---
"hephaestus": minor
---

Instance Silent Mode now fails closed and enforces the brake at every GitHub, GitLab, and Slack
delivery gateway. Suppressed feedback remains auditable but is never replayed when the brake is
released, and stale admin pages can no longer release a newer incident response.

**Operators:** New installations and upgrades whose Silent Mode setting was never explicitly changed
start engaged. On production, verify workspace delivery settings before releasing the brake from
**Instance admin → Settings**; leave it engaged on staging clones and during disaster-recovery drills.
