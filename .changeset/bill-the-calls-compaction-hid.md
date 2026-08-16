---
"hephaestus": patch
---

Fixes AI spend being under-reported, often several-fold, on long reviews. A review's own token report only covered the part of its conversation still in memory at the end, so calls the agent made earlier went unbilled while the proxy had already sent them upstream. Spend is now billed from whichever record saw more, and each entry says which one that was.

Fixes a workspace staying blocked for the rest of the month after a single AI call could not be priced. Add the missing model price and the block now clears by itself within fifteen minutes; the unpriced-events count on the AI spend page also stops disagreeing with what is actually holding the cap shut.
