---
"hephaestus": patch
---

Stops writing Tomcat file access logs in production. They were written to a
`/var/log/hephaestus` volume that wasn't shipped or aggregated anywhere, and the
requirement for a writable directory there made containers fail to start on a
fresh volume. Per-request logging is unchanged — the reverse proxy already
records every request at the edge. This also removes the now-unused log volumes
from the compose stacks.
