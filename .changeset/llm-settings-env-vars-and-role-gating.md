---
"hephaestus": minor
---

The instance-wide AI settings are now configurable from the environment and validated at startup, and the AI admin endpoints are served only by the application server.

Three new optional variables, all with working defaults, are documented in the shipped configuration: `HEPHAESTUS_LLM_DISPLAY_CURRENCY` (unset), `HEPHAESTUS_LLM_EGRESS_ALLOW_LOOPBACK` (`false`; never turn this on in production) and `HEPHAESTUS_LLM_FX_DAILY_URL` (the European Central Bank's daily file; override only on an air-gapped instance mirroring it internally). A display currency this instance cannot convert to now fails startup with a message naming what is accepted, rather than booting and silently showing USD only — `EUR` is the one supported value today. No action is required to upgrade.
