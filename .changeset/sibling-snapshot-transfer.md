---
"hephaestus": patch
---

Practice reviews avoid an extra byte-for-byte copy of sibling repository checkouts when the filesystem supports moving them into the review folder. The same files, history and refs remain available. When that move is unsupported, the existing copy path is used.
