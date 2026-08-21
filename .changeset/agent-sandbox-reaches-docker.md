---
"hephaestus": minor
---

Practice reviews can now actually start. The application server and worker run unprivileged, so every attempt to launch an agent sandbox was refused by the Docker socket with a permission error and no review ever ran. They now join the host's Docker group.

**Operators:** set `DOCKER_GROUP_ID` to the group id that owns `/var/run/docker.sock` on your host — `getent group docker | cut -d: -f3` prints it. There is no portable default, so a deployment that leaves it unset keeps failing the same way it does today.
