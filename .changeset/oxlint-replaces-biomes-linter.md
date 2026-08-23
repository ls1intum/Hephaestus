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
- GitLab sub-issue sync could delete parent links it had never looked at. When a page walk stopped
  early — an error, or a repository past the pagination ceiling — the cleanup step still ran against
  the partial result and cleared the parent of every issue whose link lived on a page it never
  fetched, then reported the sync as completed.
- Server errors now keep their original stack trace. Fifty-two places caught an exception and threw a
  new one without attaching the cause, so the log recorded where the failure was reported rather than
  where it happened. Sign-in, token validation and Slack preference failures were all affected.
- Scrollbars inside scrollable panels rendered 2px wide with no border instead of the intended 10px.
- A checkbox or radio that is switched off now looks switched off: its label kept full contrast, and
  the control itself showed neither the dimming nor the blocked cursor.
- The primary button gave no hover feedback. The style was written so that it only applied when the
  button was rendered as a link, so the most-used button in the app looked inert under the cursor.
- A mentor attachment that failed to upload disappeared with no message, leaving the sender believing
  it was attached. The failure is now reported.
- A disabled accordion section still opened when clicked.
- Copying a mentor reply to the clipboard failed silently when the browser refused.
- Countdowns now advance while the page is open: an Outline token's expiry, and the wait shown while a
  sync is rate-limited, previously only moved when something else on the page happened to redraw.
- Screen readers no longer hear an orientation announced on grouped toggle buttons, which is not
  something a group can meaningfully have.
- The achievements API described an unlock time as always present, even for an achievement nobody
  has earned. It is now reported as absent, which is what the server was already sending.
