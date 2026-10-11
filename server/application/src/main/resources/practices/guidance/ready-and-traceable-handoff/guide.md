## How to do it

- While the work is unfinished, open the pull or merge request as a draft.
- When your work is finished, remove title or description words that say work is left or that review should wait.
- Finish each open to-do item, or say that it moves to follow-on work outside this change.
- Name the issue that the change answers in the title, the description or the branch name. For example, instead of the title “Fix login timeout” and nothing else, write “Fixes #482” in the description.
- Use a closing keyword, such as “Closes” or “Fixes”, when the merge should close the issue. A plain mention links the change to the issue, but the issue stays open.

![If the work is unfinished, three steps follow in order. First, open a draft. Next, finish the work and settle your own to-dos. Last, mark it ready to ask for review now.](figures/draft-to-ready.svg)

## When it does not apply

- There is no usual case where it does not apply. Every pull or merge request hands work to a reviewer.

It still applies when the change is a small chore with no tracking issue. Then only its readiness for review counts.

## Common mistakes

- Treating a draft as a fault. A draft is a normal state for unfinished work. Instead, keep unfinished work in draft with its issue reference.
- Marking a change ready while your own words say work is left. An example is “still need to add the error state”. Instead, finish that work first, or keep the change in draft.
- Holding back a small chore because it has no issue. A dependency bump, a formatting fix, a rename or a docs fix can stand on its own. Instead, mark it ready when the work is done.
- Reading a merge condition as a hold. “Do not merge until approved” asks for a review. It does not ask reviewers to wait.

## Sources

- [About pull requests: draft pull requests](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests#draft-pull-requests), GitHub Docs. A draft shares work in progress without a formal review request. Marking it ready requests reviews from code owners.
- [Linking a pull request to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue), GitHub Docs. A closing keyword such as “Fixes” closes the issue when the change merges into the default branch.
- [Crosslinking issues](https://docs.gitlab.com/user/project/issues/crosslinking_issues/), GitLab Docs. A mention in a description, a commit or a branch name links the issue. Closing it on merge is a separate choice.
- [Writing good CL descriptions](https://google.github.io/eng-practices/review/developer/cl-descriptions.html), Google Engineering Practices. Relevant bug numbers and other background help reviewers and later readers understand the change.
