---
"hephaestus": patch
---

Removing a label from an issue or pull request no longer fails the sync that noticed it. Workspace
isolation rejected the statement that unlinks a label, so a repository whose labels changed upstream
stopped following them; the check now recognises that statement as one keyed entirely by ids the
server already holds, and keeps rejecting anything broader.
