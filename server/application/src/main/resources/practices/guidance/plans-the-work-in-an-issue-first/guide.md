## How to do it

- Before your first commit, open an issue that says what you plan to change. For example, instead of committing a first draft and then filing the issue, file the issue first.
- You can start your branch from the issue page. GitHub and GitLab can both create a branch there.
- Compare the issue’s opening time with the author date of your earliest commit in the change.

![Open the issue and say what you plan. You can then start a branch from the issue page. Your first commit comes after the issue opens.](figures/issue-to-commit.svg)

## When it does not apply

- Your pull request or merge request carries out no issue. You name none, or you say that each named issue is other work, such as “Not related to #12”.
- The change is a release, a dependency update from a bot, or a revert.
- You mention the issue only as a plain number, or only inside a quote, code or a hidden comment.
- The issue belongs to another project.

It still applies when you name the issue only with “Related to” and the change does part of that issue’s work.

## Common mistakes

- Opening the issue just before you open the pull request or merge request. Your first commit counts, not the moment you ask for review. Instead, open the issue before you commit.
- Waiting to open the issue until the plan is complete. Only the order counts here, not the length of the issue. Instead, open a short issue first and add details as you learn.
- Comparing the issue with only the commit that adds its work. Earlier commits in the same change still count. Instead, compare with your earliest commit.

## Sources

- [How to contribute to open source](https://opensource.guide/how-to-contribute/), GitHub Open Source Guides. Before you work on a large contribution, open an issue to ask about it.
- [Merge requests workflow](https://docs.gitlab.com/development/contributing/merge_request_workflow/), GitLab Docs. For a new feature idea, GitLab asks contributors to create an issue first, if there is not one already.
- [Creating a branch to work on an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/creating-a-branch-for-an-issue), GitHub Docs. You can create a branch from the issue page, and a pull request from that branch links to the issue.
- [Create a merge request](https://docs.gitlab.com/user/project/merge_requests/creating_merge_requests/), GitLab Docs. From an issue, you can create a branch and a merge request together, both marked as related to that issue.
