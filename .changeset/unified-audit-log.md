---
"hephaestus": minor
---

The instance-admin console now has a single "Audit log" with two tabs, "Access" and "Settings",
instead of two separate pages, so there is one place to answer "who did this,
and when". Both tabs share the same filter bar: filters accept several values at once (for example
feature-flag *and* role changes in one view) and the whole selection now lives in the address bar, so
a filtered view can be pasted into a ticket or a chat and reopens exactly as it was — including links
shared before a filter value was renamed, which now open the log unfiltered rather than an error page.
