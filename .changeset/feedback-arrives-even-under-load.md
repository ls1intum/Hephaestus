---
"hephaestus": patch
---

Fixes practice feedback silently never arriving when a review finishes while a large provider sync is running. The feedback written for the developer themselves, and the mentor follow-ups, are now prepared on their own capacity, and anything that still slips past is picked up and prepared within the hour instead of being lost.
