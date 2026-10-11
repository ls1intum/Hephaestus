## How to do it

- After the merge, find the checkable items in each issue that your change implements.
- Record each delivered item with a tick or a sentence that names it.
- If only part of an item is done, tick or name that part.
- Name what remains and say that it is deferred. For example, instead of “Added the search screen”, write “Search box and filters are done. Saved searches move to #318.”
- Put this record in the issue, in your description, or in a comment.

## When it does not apply

- The pull request or merge request is not merged.
- Your change does not implement any linked issue. A plain mention of an issue does not count.
- The linked issue states no outcome you can check. It has no task list and no sub-issues. It has no section that says what counts as done or lists steps with expected results. A user story alone does not count.
- The linked issue was closed as not planned, as a duplicate, or as replaced by other work.

It still applies when the issue is already closed, and when your change delivers only part of an item.

## Common mistakes

- Thinking that you must tick every box. A plain sentence that says what is done and what is deferred works just as well.
- Letting the closed issue speak for you. On GitHub, a closing keyword closes the linked issue when you merge into the default branch. Its boxes stay as they were. Instead, tick the delivered items or add a closing sentence.
- Ticking template boxes such as “builds”, “reviewed” or “lint clean”. They name no item of the issue. Instead, tick boxes whose words match the issue’s items.
- Naming only the feature, such as “added the search screen”. Instead, name the items that are done and the ones that move on.

## Sources

- [The Scrum Guide](https://scrumguides.org/scrum-guide.html), Ken Schwaber and Jeff Sutherland, 2020. In Scrum, work that fails the team’s agreed quality checks returns to their list of future work.
- [Task lists](https://docs.gitlab.com/user/markdown/#task-lists), GitLab Docs. You can mark tasks complete, incomplete or inapplicable in issues, merge requests and comments.
- [Linking a pull request to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue), GitHub Docs. A closing keyword closes the linked issue when the pull request merges into the default branch.
