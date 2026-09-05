---
"hephaestus": patch
---

Removing a label from an issue or pull request no longer fails the sync that noticed it. Workspace
isolation rejected the statement that unlinks a label, so a repository whose labels changed upstream
stopped following them; the check now recognises that one statement, on the join tables Hibernate
declares, and keeps rejecting everything broader.
