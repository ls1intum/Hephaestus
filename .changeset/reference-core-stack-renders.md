---
"hephaestus": patch
---

The reference multi-host stack now deploys instead of stopping with `service "webhook-server"
depends on undefined service "postgres": invalid compose project`. That deployment brings the proxy,
core and app stacks up as three separate Compose projects, and the core stack had been asking Compose
to start the webhook receiver after a database that belongs to the app stack — an order Compose
cannot honour across projects, and one that made the core stack refuse to render at all. The receiver
reaches the database over the shared network as it always did, and retries until it answers. The
single-host install is unaffected: it runs everything as one project and still starts the receiver
after the database. Nothing to set on either — deploy or upgrade as usual.
