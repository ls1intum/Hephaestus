## How to do it

- Before you merge, check that a person other than the author approved the pull request or merge request. Approvals from the author or a bot do not count.
- If you see only a thumbs-up or a “looks good” comment, ask for an approval in the review.
- If an approval was dismissed, or the same reviewer later requested changes, ask for a new approval.
- After you fix a person’s request for changes, ask that reviewer to approve again. For example, instead of merging when your fix passes the tests, wait for their approval.
- Then merge. You can press merge on your own work.

## When it does not apply

- The pull request or merge request did not merge.
- You work alone on the project. Nobody was asked to review this work, and it has no review or review comment.

It still applies when a reviewer was asked, or when someone left a review or a review comment, even a bot.

## Common mistakes

- Thinking that the author must not press merge. Authors often merge their own work. Instead, make sure that someone else approved it first.
- Merging on an old approval. An approval that the same reviewer later withdrew by requesting changes, or that someone dismissed, no longer stands. Instead, ask for a new approval after your rework.
- Merging while a person’s request for changes still stands. Another person’s approval does not cancel it. Instead, wait until the reviewer who asked for changes approves. A bot’s request does not block a person’s approval.
- Counting a bot’s approval as a review. A bot is not a second person. Instead, ask a person to approve.

## Sources

- [About protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches), GitHub Docs. A protected branch can require approval before merge and dismiss old approvals after changes.
- [Merge request approval settings](https://docs.gitlab.com/user/project/merge_requests/approvals/settings/), GitLab Docs. A project can stop authors from approving their own merge requests, and remove approvals when you add changes.
- [Code-Review check](https://github.com/ossf/scorecard/blob/main/docs/checks.md#code-review), OpenSSF Scorecard. The check looks for human review before merge and excludes bot reviews.
- [An empirical study of the impact of modern code review practices on software quality](https://doi.org/10.1007/s10664-015-9381-9), McIntosh, Kamei, Adams and Hassan, 2016. In Qt, VTK and ITK, the share of changes that were reviewed had a significant link with defects after release.
