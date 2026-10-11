## How to do it

- Before you merge, look at the pull or merge request for open threads and for reviewers who requested changes.
- When a comment says “do not merge until the crash is fixed”, wait until its reviewer lifts it.
- Resolve each thread before you merge.
- When a reviewer requests changes, ask that reviewer to look again after your fix. For example, instead of merging right after you push the fix, wait for that reviewer to approve it.

## When it does not apply

- The pull or merge request did not merge.

It still applies when the merged code is correct.

## Common mistakes

- Merging because the code works. This practice is about the review, not about the code. Correct code can still skip the second look that a reviewer asked for. Instead, wait for their approval.
- Resolving threads after you merge. A thread you resolve after the merge was still open when the work landed. Instead, resolve it first, then merge.
- Treating every comment as a request to wait. A suggestion the reviewer leaves to you, or advice for later work, is not a request to hold the merge. Instead, look for what the reviewer asked of this merge, not how politely they wrote it. Still resolve its thread before you merge.

## Sources

- [About protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches), GitHub Docs. A repository can block a merge until every conversation is resolved. With required reviews, an admin who requests changes must approve before the merge.
- [Comments and threads](https://docs.gitlab.com/user/discussions/), GitLab Docs. A project can block a merge until every thread is resolved.
- [How to write code review comments](https://google.github.io/eng-practices/review/reviewer/comments.html), Google Engineering Practices. Reviewers can label a comment as a nit, optional, or for later. Then the author can tell required changes from suggestions.
- [The impact of code review coverage and code review participation on software quality](https://rebels.cs.uwaterloo.ca/confpaper/2014/05/30/the-impact-of-code-review-coverage-and-code-review-participation-on-software-quality.html), McIntosh and others, 2014. In Qt, VTK and ITK, components with low reviewer participation had more defects after release.
