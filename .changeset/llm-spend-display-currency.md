---
"hephaestus": minor
---

LLM spend can now be shown with a euro estimate beside the US dollar figures. Dollars remain what is billed, capped and recorded — the euro number is a clearly labelled estimate converted with the European Central Bank's daily reference rate, and every screen states the date of the rate it used. A closed month is shown with a rate from inside that month, so its estimate never changes after the fact, and if rates cannot be refreshed for a week the estimate disappears rather than quietly drifting.

It is off unless you ask for it: set `HEPHAESTUS_LLM_DISPLAY_CURRENCY=EUR` to switch it on, and leaving it unset changes nothing. Once set, the application server fetches the ECB's free daily reference rates once each weekday — no API key, and no outbound request from the worker or webhook containers.
