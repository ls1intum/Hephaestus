---
"hephaestus": patch
---

A deployment that leaves the bundled proxy stack out and fronts the application with its own
proxy reading the stacks' routing rules served without `Strict-Transport-Security`, while every
other security header, set by the responses themselves, was present. The header comes with the
application stacks' routing rules, so such a deployment sends it too. Nothing changes for a
deployment that runs the bundled proxy stack.
