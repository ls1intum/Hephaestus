---
"hephaestus": patch
---

Screen readers now announce what every dropdown list is for. The status, timeframe, work-type,
rows-per-page and model pickers each opened a list of options with no name attached, so the list
itself was announced as unlabelled.

Also fixed, all found by a stricter type and lint gate over the web app:

- A review schedule saved with a time that had no minutes (`9` rather than `09:00`) stored no minute
  at all instead of falling back to the hour's start.
- Audit-log entries and the curated-catalogue version panel printed `[object Object]` for any field
  whose value was not plain text.
- A cookie-consent choice was read back from browser storage without checking it, so a corrupted
  entry could be treated as a decision.
- A theme, a workspace role or a feature flag that the browser or server reported as something this
  build does not recognise is now ignored rather than trusted: the theme falls back to the default,
  the role is refused, and the flag reads as off.
