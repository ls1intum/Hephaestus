---
"hephaestus": patch
---

A start that cannot read one of its settings — a variable referenced but never provided, or one that
refers to itself — now prints the configuration report naming that setting, instead of ending at a
stack trace about an unresolved placeholder. A setting that cannot be read is never answered with its
default either, so a security switch whose value is unreadable is reported as needing attention
rather than as satisfied, and the reason is logged.
