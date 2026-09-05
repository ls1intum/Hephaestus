---
"hephaestus": patch
---

Serving an instance on more than one hostname is entirely the reverse proxy's job: the extra names,
their shared certificate, and the redirect that sends a browser back to `APP_HOSTNAME` all come from
`APP_HOST_MATCH`. An instance that names a single hostname routes and serves exactly as before, and
one fronted by a proxy that does not read this stack's routing configuration answers on whatever
names that proxy sends it — configure the extra names and their redirect there instead.
