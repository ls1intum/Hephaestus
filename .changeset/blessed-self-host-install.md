---
"hephaestus": minor
---

You can now self-host Hephaestus on a single Linux server. `docker/self-host/` adds one
supported Docker Compose stack — application server, webhook receiver, PostgreSQL and NATS
behind a TLS reverse proxy — that reuses the maintainers' service definitions, so it has no
second copy to fall out of date. Follow the new [install guide](https://ls1intum.github.io/Hephaestus/admin/install);
GitHub App setup, manual webhook creation, and backup/restore each have a companion page.

Existing deployments are unaffected: the reference Compose files are unchanged apart from two
safe additions — the NATS JetStream limits become overridable (defaults unchanged), and a
release-pin sanity check that rejected every valid pin is fixed.
