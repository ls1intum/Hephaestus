---
"hephaestus": patch
---

The self-host setup script no longer generates a new encryption key on a host that already has a
Hephaestus database, and stops rather than guess when Docker cannot say whether one exists. A
generated key made every stored integration credential unreadable without warning; setup now names
the key to carry over, and the pull-based deployment guide lists the settings a host keeps when it
moves.
