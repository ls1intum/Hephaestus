---
"hephaestus": patch
---

The Slack channel settings now keep your monitored channels and their actions when Slack cannot list channels to add. That failure appears in the Add channel dialog with Retry, and you can still paste a channel link or ID. If access to the monitored channels is refused, the page says so, closes any open channel dialog, and does not bring it back when the channels load again. If a refresh fails after the channels loaded, they stay visible with a notice and Retry. While a channel is being added, activated or removed, its dialog stays open with your entry until the request finishes, unless access to the channels is refused.
