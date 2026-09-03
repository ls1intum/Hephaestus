---
"hephaestus": minor
---

Hosts can now keep themselves on a release instead of being deployed to. A host polls a channel naming the release it should run, verifies the release signature and the channel's own signature itself, and applies the digest-pinned stacks — so no deployment credential, and no way in to the machine, has to exist anywhere else. A failed upgrade stops and alerts rather than rolling back, because schema changes only ever move forward.

**Operators:** this is opt-in and nothing changes until you install the units in `docker/self-host/systemd/`. Set up the staleness alert described in the guide before relying on it: a host that stops updating is quiet, and that alert is what makes it loud.
