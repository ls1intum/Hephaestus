---
"hephaestus": patch
---

Fixes valid observations being discarded when a model faithfully copied source text but normalized straight quotes, dashes, or spaces to typographic equivalents. Evidence verification now normalizes only that closed set before comparing the quote with its source; text that is not present in the source is still rejected.
