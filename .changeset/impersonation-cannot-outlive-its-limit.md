---
"hephaestus": patch
---

An impersonation session can no longer be extended past its own time limit. When a session was
refreshed close to that limit it could previously be renewed instead of ending, and the operator's
own session limit was not carried back out of the impersonation. Impersonation now stops at its
deadline and returns you to your own account, and it also ends immediately if the account being
impersonated is granted instance-admin rights while the session is open.
