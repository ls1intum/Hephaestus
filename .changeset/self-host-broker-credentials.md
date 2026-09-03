---
"hephaestus": patch
---

A fresh self-host install now starts instead of stopping with `required variable NATS_USERNAME is
missing a value`: `setup.sh` generates the message-broker credentials alongside the database password
and the other internal secrets. Upgrading an existing installation picks them up by rerunning
`docker/self-host/setup.sh`, which leaves every value you already set untouched.
