---
"hephaestus": minor
---

Workspace admins can now schedule a recurring check that reviews recent work even when nothing announced it. Until now a review only started when a provider sent an event, somebody asked by hand, or an admin ran a one-off campaign — so a pull request whose webhook was lost was never reviewed, and nothing recorded that it had been missed. Set a daily or weekly check under **Review past work**, choose how far back it reaches, and anything overlooked gets picked up on the next pass.

Work the check finds counts exactly like work a live event triggered, because the window is deliberately bounded to the last few days: at most twice the cadence, and never more than a week. Reviewing further back is still the separate one-off campaign, which stays out of your live trends. Work already reviewed is never paid for twice, so overlapping windows cost nothing.
