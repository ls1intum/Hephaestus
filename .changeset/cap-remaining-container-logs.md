---
"hephaestus": patch
---

Stops a container log from filling the host disk. The reverse proxy, the maintenance page, the
database and the release-pin fetcher were the last services still writing an unbounded log, so a
retry loop — an unreachable certificate authority, a failing signature check, a rejected database
connection — could grow until the disk was full and took the whole deployment down with it. Every
container in the stack now rotates its log with the same caps the application containers already
used. Existing logs are rotated from the next restart; nothing is lost that was going to be kept.
