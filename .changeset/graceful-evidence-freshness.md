---
"hephaestus": patch
---

Practice reviews are no longer skipped because a pull request or issue has not changed recently. Hephaestus previously treated a record that had not been modified upstream in the last five minutes as out-of-date evidence, which skipped automated review for established repositories and for every review not started by a webhook.

Reaching a collection limit no longer skips a practice either. A pull request with several hundred review comments, review threads, or linked issues is now reviewed from the evidence that was collected, and the review records that the evidence was partial.

An issue reference that points outside the repository, such as one tracked in another system, is now reported as unresolved instead of marking the evidence incomplete and skipping the practices that read linked work.

Practice authors now see clearer descriptions of what each evidence source contains, including the limits that apply to it.
