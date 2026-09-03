# deploy-state

Which release each environment runs. One file per channel, written and signed by the `Promote`
workflow, read by the reconciler on each host.

This branch carries no code and is never merged. Hosts compare commits here to tell a promotion from
a rewind, so **never force-push it**: rewriting this history is indistinguishable from a rollback
attack, and a host that has applied a commit will refuse an ancestor of it.

Documentation: https://docs.hephaestus.build/admin/pull-based-deployment
