---
"hephaestus": patch
---

A pull request preview now refuses to start unless the safeguards it promises are actually in place. Previews begin from a copy of a live database, and the step that pauses reviews and replaces the copied sign-in configuration could fail without saying so, leaving a preview running against real data with its review triggers still enabled. That step is now checked against the database itself, and a preview that does not pass it fails its deployment instead of coming up.
