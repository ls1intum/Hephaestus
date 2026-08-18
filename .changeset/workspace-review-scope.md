---
"hephaestus": minor
---

A workspace can now say which of its work gets reviewed at all. Under the practice-review settings,
name the target branches and the repositories in scope; a review only starts when the pull request or
issue matches. Leave a list empty and that axis is unrestricted, so nothing changes for a workspace
that never touches this.

This is the setting for "we only review merges into main" or "review the two repositories that matter,
keep syncing the rest". A practice cannot express it, because a practice is shared and cannot know
whether your trunk is called `main`, `master` or `develop` — that is a fact about your deployment.

Names are matched exactly: there are no wildcards, and there is no path filter, because the files a
pull request changes are not yet known at the point where the decision to review is made. A branch
list does not restrict issue reviews, since an issue has no target branch.
