---
"hephaestus": patch
---

The self-host setup script no longer generates a new encryption key on a host that already has a
Hephaestus database. A generated key made every stored integration credential unreadable without
warning; setup now refuses and names the key to carry over, and the pull-based deployment guide lists
the secrets a host keeps when it moves.
