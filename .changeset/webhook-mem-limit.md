---
"hephaestus": patch
---

Fixes the webhook-server crash-looping on startup. Two causes: its 1 GB memory
limit left too little heap for the Spring classpath-scan spike (it OOMed before
boot finished), and — because prod enables Tomcat file access logging to
`/var/log/hephaestus`, which the core stack gives no writable location — the
access-log valve failed and aborted the boot. The webhook now gets a 2 GB default
limit (`WEBHOOK_SERVER_MEM_LIMIT`) and its file access log is disabled (requests
are already logged by the reverse proxy and to stdout).
