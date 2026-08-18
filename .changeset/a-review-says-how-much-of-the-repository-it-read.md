---
"hephaestus": minor
---

A review that reads the repository around a change now says how much of it it actually read. Very large repositories are read up to a ceiling — 20,000 files and 32 MiB by default, skipping any single file over 10 MiB — and when a ceiling is reached the repository evidence is marked incomplete and names what was left out. Practices that judge work by something being absent from the repository are skipped on an incomplete read instead of answering from the part that was read, so "this does not exist anywhere in the repository" is only ever said about a repository that was read in full. Reviews of ordinary repositories are unaffected; very large ones cost less and no longer risk an unbounded bill.

The ceilings are optional and default to the values above; set `GIT_TREE_MAX_FILES`, `GIT_TREE_MAX_TOTAL_SIZE` or `GIT_TREE_MAX_FILE_SIZE` to raise or lower them.
