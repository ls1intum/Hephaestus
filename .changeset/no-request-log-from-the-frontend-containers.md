---
"hephaestus": patch
---

The containers that serve the web frontend and the maintenance page no longer write a line per
request. Those lines recorded the URL of every page view, and a link to a person's profile page
carries their username, so the request log was a per-request record of who looked at whom — kept for
however long the container's log rotation happened to hold it. Turning it off restores what the
deployment always claimed: no layer of the stack writes a per-request record. Startup problems and
HTTP errors still appear in `docker logs`, and nothing else changes: container health checks and the
reverse proxy never read that log.
