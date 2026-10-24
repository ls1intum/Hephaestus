---
"hephaestus": patch
---

Background work stays single when two application servers share one database. Historical backfill and the GitLab webhook check run on one server at a time, so GitLab no longer receives a duplicate webhook. A long background task can no longer make another server mark a running sync as failed.
