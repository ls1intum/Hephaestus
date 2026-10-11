---
"hephaestus": patch
---

AI usage without reported tokens remains unpriced when model prices change. Streamed usage already received is retained if a client disconnects, and operators can monitor served responses without usage through the `llm.proxy.usage.missing` counter.
