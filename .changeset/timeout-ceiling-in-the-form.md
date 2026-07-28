---
"hephaestus": patch
---

A per-run timeout longer than an hour is now caught by the form under Administration → AI models,
with the reason beside the field: runs stop after an hour, so a longer timeout would never be
honoured. Before, the value was sent and came back rejected with nothing on the form to say which
number was wrong or why.
