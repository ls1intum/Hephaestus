---
"hephaestus": minor
---

Adding, editing or removing a login provider is now recorded on the instance audit trail. Until now
these three actions — the ones that decide how everybody signs in to the instance — left no entry at
all, so an unexpected change to a sign-in method could not be traced back to who made it. Each entry
names the provider, whether it ended up enabled, and which fields a change touched; a rotated client
secret is listed as having changed, but its value is never stored. Instance administrators find the
entries alongside role changes and impersonation under Administration → Audit log.
