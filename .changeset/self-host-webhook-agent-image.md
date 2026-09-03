---
"hephaestus": patch
---

The webhook receiver in a self-host install now starts instead of crash-looping with
`hephaestus.agent.image.reference must be digest-pinned`, so incoming pull request, issue and chat
events are received again. The stack was handing the verified release lock's agent image digest to
the API server and the worker but not to the receiver, which fell back to naming that image by tag —
and Hephaestus refuses a tag there, because the tag can move to an image built from a different
commit than the one you installed. Every container now reads the same digest from the lock. Nothing
to set: reinstall or upgrade as usual.
