---
"hephaestus": patch
---

Fixes practices created before this release failing to save with "Evidence source cannot satisfy CURRENT requirements". Their stored rules required a currentness guarantee that the source contract no longer makes, so editing or enabling any existing practice was rejected. A migration brings stored rules in line with the contract; review results recorded under the old rules are marked for re-evaluation.

The evidence editor now states each requirement as a condition in plain language — "fully captured, not cut off by a size limit", "taken from the exact commit under review", "not empty" — instead of naming contract states, and no longer reserves an empty column beside each source.
