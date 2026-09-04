---
"hephaestus": patch
---

Pull-request previews start again. The application refuses to talk to a database it cannot recognise
as local over an unencrypted connection, and a preview's database answers to a per-pull-request name
rather than the one on that list, so every preview's application server failed to start and retried
until the deployment was reported as failed. Previews now declare that their database is a sibling
container on the deployment's own private network, which is the trust every other stack already has.
