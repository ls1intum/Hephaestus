# Self-updating host units

A host that runs these keeps itself on the release its channel names, verifying both signatures
itself. Nothing connects to the host.

| File | Purpose |
| --- | --- |
| `hephaestus-reconcile.service` | one reconcile pass |
| `hephaestus-reconcile.timer` | runs a pass every minute |
| `hephaestus-reconcile-notify@.service` | posts the journal of a failed pass |
| `reconcile.env.example` | the host's configuration |

Installation, operation and the staleness alert you must configure are in
[Pull-Based Deployment](https://docs.hephaestus.build/admin/pull-based-deployment).

`Type=oneshot` here is deliberate and `RemainAfterExit=` is deliberately absent: it would leave the
unit active after the first pass, and systemd does not restart a unit that is already active when
its timer elapses, so every later tick would do nothing.
