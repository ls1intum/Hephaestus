---
"hephaestus": minor
---

High-risk instance-admin actions now ask you to confirm it's really you. Starting an impersonation,
changing someone's instance role, or editing a login provider requires a sign-in from the last few
minutes; if yours is older, a **Confirm access** dialog walks you back through your identity provider
and the action then proceeds. The check is enforced on the server, so a stolen browser session cannot
reach these actions on its own — completing the confirmation needs the live session at your identity
provider, which a copied session does not include. Blocked attempts appear on the audit trail next to
the successful ones.

**Operators:** the window defaults to 5 minutes and needs no configuration. Set
`HEPHAESTUS_AUTH_STEP_UP_MAX_AGE` (an ISO-8601 duration, e.g. `PT15M`) to widen or narrow it. A
non-positive value is rejected at startup, because it would lock every admin out of these actions.
