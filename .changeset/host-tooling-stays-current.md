---
"hephaestus": patch
---

Once a host that pulls its own releases applies a release that carries this change, it runs that
release's deployment tooling and keeps its two systemd units matching it, so applying a release
also brings the tooling forward — a host whose tooling had fallen behind previously failed every run
until an operator logged in and updated it by hand. The tooling an operator installs or upgrades by
hand, and releases older than this change, are the exceptions: the host keeps the tooling it has
while running one. A host installed before this release needs the one-time upgrade steps in the
pull-based deployment guide.
