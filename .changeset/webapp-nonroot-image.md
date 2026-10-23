---
"hephaestus": minor
---

The webapp container and the maintenance page now run as an unprivileged user on port 8080, need no added capabilities and work with a read-only root file system. They write only to `/tmp/hephaestus-webapp`. The bundled Compose stacks already use these settings. **Operators:** if your own proxy, health check or `docker run -p` mapping sends traffic to the webapp container's port 80, change it to port 8080, and mount a directory that UID 101 can write at `/tmp/hephaestus-webapp`.
