---
"hephaestus": patch
---

A GitLab merge request that becomes ready before GitLab has worked out its diff now gets its review once the diff is known. Before, Hephaestus dropped that review without notice. If the merge request changes before then (a new commit, target branch, title, description or linked issue), Hephaestus does not review the old state. A later change can start a new review.

A `/hephaestus review` comment on a merge request asks for a review again. The same people who can request a review on the work page can use it: the author, an assignee, or a workspace admin. It checks the same settings and limits. If GitLab sends the comment more than once, Hephaestus starts at most one review. A comment asks for a review only when the merge request's diff is available. If GitLab has not worked out the diff yet, request the review again later.
