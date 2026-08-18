---
"hephaestus": minor
---

You can now self-host Hephaestus on a single Linux server. One supported Docker Compose stack —
application server, webhook receiver, PostgreSQL and NATS behind a TLS reverse proxy — reuses the
maintainers' own service definitions, so there is no second copy to fall out of date. Follow the new
[install guide](https://ls1intum.github.io/Hephaestus/admin/install); GitHub App setup, manual
webhook creation, and backup/restore each have a companion page.

Existing deployments are unaffected: the reference Compose files are unchanged apart from making the
NATS JetStream limits overridable, with the defaults unchanged.
