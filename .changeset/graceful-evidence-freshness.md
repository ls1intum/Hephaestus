---
"hephaestus": patch
---

Hephaestus no longer skips a review because the pull request has been quiet. A pull request or issue that nobody has touched recently is now reviewed normally, instead of being treated as out-of-date evidence — this previously skipped every review of an established repository, and every review that was not triggered by a live webhook.

Reviews are also no longer skipped for hitting a size limit. A pull request with hundreds of review comments, threads, or linked issues is assessed from what was collected, and the review says what it saw rather than declining.

An issue reference pointing outside the repository, such as one tracked elsewhere, is now listed as unresolved instead of disabling the practices that read linked work.

Practice authors see plainer descriptions of what each evidence source contains, with the real limits stated.
