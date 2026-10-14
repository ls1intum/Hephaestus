---
"hephaestus": minor
---

Unverified usage no longer gains a confirmed cost when model rates are added. Usage measurements and missing prices are tracked separately, and budget notices now explain both causes without assigning a combined count to each cap. Historical costs are retained; historical unpriced usage without a verification decision stays unpriced and continues to affect current-month caps.

**Operators:** Use the [stopped-writer upgrade procedure](https://docs.hephaestus.build/admin/native-hibernate-json-cutover) for this accounting cutover. Do not mix runtime-role versions: an older repricer does not honor measurement verification.
