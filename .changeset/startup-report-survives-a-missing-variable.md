---
"hephaestus": patch
---

Starting with a required environment variable unset now prints the configuration report that names
every missing setting, instead of ending at a stack trace about an unresolved placeholder. A value
such as `DATABASE_URL` is reported as the setting it stands for.
