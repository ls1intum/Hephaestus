---
"hephaestus": minor
---

Refuses to start when the retention window for cached review evidence is set below one day. Zero was
accepted and read as the opposite of what it looks like: rather than switching the cleanup off, it
made every cached job directory eligible for deletion on the next sweep.

**Operators:** the shipped default of 30 days needs no change. If you set
`HEPHAESTUS_FABRIC_GC_RETENTION_DAYS`, it must now be `1` or more; `0` or a negative value stops the
server starting, with the limit in the message. There is no value that switches the cleanup off — set
a long window instead.
