## How to do it

- Before you ask for a review, find the acceptance criteria in the issue that your change closes. They can be a checklist, a list of musts, or “Given …, when …, then …” steps.
- In the description, after the closing reference, name each criterion that this change finishes. For example, write “Fixes #212. Done here: CSV export and the download button. Deferred to #215: PDF export.” instead of “Fixes #212”.
- Say that each criterion you leave for later is deferred. Add its follow-up issue if it has one.

## When it does not apply

- Your change does not say that it closes an issue.
- The issue that it closes has no criteria that you can check. It has only a title or a user story such as “As a …, I want …”. Or its text is prose with no list, checklist or acceptance section.
- An issue looks related, but your change does not name it.
- The issue number shows up only in an example, a template or code, and it names other work.

It still applies when the provider links the issue to close on merge, even if the description does not say so.

## Common mistakes

- Thinking that you must copy the issue’s checklist. A checklist is one way. Plain sentences that say which criteria are done and which are deferred work just as well.
- Ticking the boxes of the description template, such as a passing lint or added screenshots, and stopping there. Those boxes are about the form of the change. Instead, match the change to the criteria in the issue.
- Writing only the closing reference, or repeating the issue title. The issue then closes as if every item were done. Instead, state which criteria are done here and which are deferred.
- Leaving an open item out of the description. It disappears when the issue closes. Instead, say that it is deferred. Add its follow-up issue if it has one.

## Sources

- [Linking a pull request to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue), GitHub Docs. A closing keyword such as “Closes” closes the issue when the pull request merges into the default branch. A link from the side panel does too.
- [Managing issues](https://docs.gitlab.com/user/project/issues/managing_issues/), GitLab Docs. A closing pattern in a merge request closes the issue on merge.
- [The Scrum Guide](https://scrumguides.org/scrum-guide.html), Ken Schwaber and Jeff Sutherland, 2020. An item that does not meet the Definition of Done returns to the Product Backlog for future consideration.
