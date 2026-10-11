## How to do it

- Before you merge, check which review asks your change leaves undone.
- When an ask waits, link an issue that tracks it in the thread. For example, instead of replying “Will do in the next change”, reply “Tracked in #482”.
- If no issue tracks the ask, open one from the review comment. The issue then shows where the ask came from.
- When you think an ask is not needed, ask the reviewer in the thread to accept your reason.
- When you do only part of an ask, track the part that is left.

## When it does not apply

- The pull request or merge request did not merge.
- Your change does every ask, or you gave a reason for each ask left undone and the reviewer accepted it.
- The comment asks nothing of this change. Examples are a question such as “what is this for?” and a remark for next time.
- The reviewer leaves the suggestion to your choice.

It still applies when an ask sounds polite, such as “could you” or “consider”, or when a review bot made it. It also applies when the reviewer approved on the condition that you do the ask later.

## Common mistakes

- Taking an approval as permission to drop the ask. “Approved, as long as you add the tests later” keeps the ask open. Open an issue for the tests and link it.
- Giving your reason and then merging. An approval with no word on your reason is not agreement. Ask the reviewer to confirm.
- Resolving the thread with “later”. A resolved thread does not say where the ask went. Link the issue first, then resolve the thread.
- Trusting yourself to remember. A promise in a thread is easy to lose after the merge. Put it in the tracker, where the team can see it.

## Sources

- [Handling pushback in code reviews](https://google.github.io/eng-practices/review/reviewer/pushback.html#later), Google Engineering Practices. File a bug for cleanup that must wait, so that it does not get lost.
- [Creating an issue from a comment](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/creating-an-issue#creating-an-issue-from-a-comment), GitHub Docs. You can open an issue from a pull request comment.
- [Move open threads to an issue](https://docs.gitlab.com/user/project/merge_requests/#move-open-threads-to-an-issue), GitLab Docs. An open thread can become a new issue that the merge request links.
- [Potential technical debt and its resolution in code reviews](https://doi.org/10.1145/3544902.3546253), Fu and others, 2022. In OpenStack and Qt, some potential technical debt that reviewers found was still unresolved when the code merged.
