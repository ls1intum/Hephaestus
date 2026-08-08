---
"hephaestus": patch
---

Fixes a fresh install failing to start on its very first boot. The application server wrote a
per-request access log into a `/var/log/hephaestus` volume, and a newly created volume is owned by
root, so the server could not write there and aborted instead of coming up. Nothing shipped or
collected those files anyway; the log and the volumes that held it are gone from the compose stacks.

No layer of the stack now writes a line per request, which is the posture the deployment always
claimed. Startup problems, errors and sync activity still appear in `docker logs`.
