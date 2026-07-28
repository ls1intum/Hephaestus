---
"hephaestus": patch
---

Adding a second AI model that points at the same provider model id now says so, instead of failing with a generic server error. The message names the id and the connection, so the fix is obvious: rename the upstream id, or edit the model that already claims it.
