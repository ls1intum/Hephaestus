---
"hephaestus": patch
---

Webhook backlog metrics now count the deployment's current consumer names without including older names or another deployment whose name starts with the same prefix. Unconsumed current consumers remain visible. No stored messages or consumers are removed.
