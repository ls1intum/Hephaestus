---
"hephaestus": patch
---

Removing a label from an issue or pull request no longer fails the sync that noticed it. Workspace
isolation had rejected the statement that unlinks a label, so a repository whose labels changed
upstream stopped following them.
