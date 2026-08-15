---
"hephaestus": minor
---

Practice reviews no longer go missing when Hephaestus was briefly unable to run them. A review that
could not start because the workspace was paused, its AI binding was switched off, its monthly AI
budget was spent, or its chosen model had been removed is now picked up automatically once the block
is lifted, instead of being silently dropped with nothing left to retrigger it.

A merge request that leaves draft while Hephaestus is catching up on missed activity is now reviewed
as ready. Previously that transition was noticed and then discarded, and because the merge request
already looked up to date afterwards, no later event could recover it.

Repeated deliveries of the same event from GitHub or GitLab no longer start the same review twice. A
redelivery used to be recognised only while the earlier review was still running, so one arriving
after it finished paid for the whole review again; the review cooldown minutes are now purely a rate
limit rather than the last line of defence.

How long a blocked review keeps waiting is configurable, and the defaults need no action: it is
re-attempted hourly and given up on after seven days.
