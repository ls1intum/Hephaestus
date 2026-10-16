---
"hephaestus": patch
---

When an admin loses access to the instance settings, the admin pages stop showing the silent mode banner, its reason and who turned it on, and the silent mode controls. They show the refusal instead, and show the settings again only after a new read succeeds. During a temporary outage, the last known state, the banner and Turn on silent mode stay available, and Turn off still waits for the current settings.
