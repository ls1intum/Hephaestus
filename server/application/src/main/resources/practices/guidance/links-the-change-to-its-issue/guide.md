## How to do it

- Before you open the pull or merge request, search the open issues for the one that this work answers.
- Name the issue by its number in the title, the description or the branch name. For example, instead of the title “Add login timeout” and nothing else, write “Related to #12” in the description.
- If you start the work from the issue, create the branch from it. On GitLab, start the branch name with the issue number, such as `12-add-login`, in the issue’s project.
- Use a closing keyword such as “Closes #12” only when the change completes the issue. A plain mention links the change to the issue but leaves the issue open.
- If the issue lives in another project, use its full reference, such as `group/project#12`.

## When it does not apply

- Your project does not track its work in issues.
- The change is a release or a version bump.
- The change is a dependency update that a bot opened.
- The change reverts an earlier change.
- The change merges one long-lived branch into another.

It still applies when a person changes the product and no issue exists for the work. Then the missing issue is the gap.

## Common mistakes

- Thinking that only a closing keyword counts. A plain mention such as “Related to #12” links the change too, even for part of an issue. Instead, keep the closing keyword for a change that finishes the issue.
- Leaving the number from the description template or an example in place. Instead, replace it with the number of your issue.
- Writing a number that is not the issue, such as a typo or a pull request number. Instead, open the link and check that it is the right issue.
- Opening product work with no reference while an open issue already describes it. Instead, name that issue.

## Sources

- [Linking a pull request to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue), GitHub Docs. A closing keyword in a pull request to the default branch links the issue and closes it when merged.
- [Crosslinking issues](https://docs.gitlab.com/user/project/issues/crosslinking_issues/), GitLab Docs. Mentioning an issue in a merge request description or commit message links the work to that issue.
- [Writing good CL descriptions](https://google.github.io/eng-practices/review/developer/cl-descriptions.html), Google Engineering Practices. Google suggests that a description include background such as bug numbers, for future readers.
- [Traceability in the wild: automatically augmenting incomplete trace links](https://doi.org/10.1145/3180155.3180207), Rath and others, 2018. Across six open-source projects, about 60% of commits named an issue, leaving gaps between issues and code.
