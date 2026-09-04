---
"hephaestus": minor
---

Hosts can now keep themselves on a release instead of being deployed to. A host polls a channel naming the release it should run, verifies the release signature and the channel's own signature itself, and applies the digest-pinned stacks — so no deployment credential, and no way in to the machine, has to exist anywhere else. A failed upgrade stops and reports failure for host monitoring rather than rolling back, because schema changes only ever move forward.

Nothing changes until you install the units in `docker/self-host/systemd/`. If you do, set up the staleness alert the guide describes before relying on it: a host that stops updating is quiet, and that alert is what makes it loud.
