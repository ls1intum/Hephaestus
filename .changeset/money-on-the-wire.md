---
"hephaestus": patch
---

The API now states what its money actually is. Every amount and per-unit rate is marked `decimal` in the OpenAPI document, so a generated client binds it to an exact decimal type instead of a floating-point one, and the API description spells out the precision each figure carries and the rule that totals are read from the response rather than added up by the caller. Nothing on the wire changed shape, so no client needs updating.

The euro estimate on the AI usage screens also names its source again: the disclosure now reads "at the European Central Bank reference rate published on …" rather than "at the reference rate", and the rate's publisher travels in the response instead of being assumed by the page.
