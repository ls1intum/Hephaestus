---
"hephaestus": patch
---

Fixes two ways the instance AI console could leave you stuck without saying why.

Adding a model with a context window or max-output value the server won't accept now shows the reason under the field it belongs to. Before, "Add model" simply did nothing: the form rejected the value, no request was sent, and nothing appeared on screen.

Choosing who may use a model now highlights the option you picked — the whole card tints and takes a coloured border, instead of only a small radio dot changing.
