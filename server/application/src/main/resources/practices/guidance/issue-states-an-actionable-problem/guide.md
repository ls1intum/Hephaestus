## How to do it

- Write a title that says which work the issue is about. For example, instead of “Search broken”, write “Search with a quote mark shows an error page”.
- For a bug, say what happened and what you expected to happen.
- For a bug, give a way to see it fail. Steps, a stack trace, the exact error or a failing test all work.
- For a request, describe the behavior you want, so that someone can start to build it. You can leave out who it helps, why, and a design.
- For an epic, an issue that groups related work, state the scope of the whole. The detail can go in the smaller issues under it.

## When it does not apply

- The issue only asks a question or opens a discussion. It reports no bug and asks for no new behavior.

It still applies when the title names a task, such as “Add a dark-mode toggle”, and the body is empty.

## Common mistakes

- Filling in a template that only repeats the title, even under a bug or feature label. A label names the kind of work, not the work. Instead, write the problem or the behavior in your own words.
- Writing “It does not work”. Instead, say what you saw and what you expected, so a maintainer knows what is wrong.
- Writing only your guess at the cause. Instead, report what you saw. Add your guess only as an extra.
- Pointing to a general document or a placeholder. Instead, link a document that describes this work, or write the work in the issue.

## Sources

- [Bug writing guidelines](https://bugzilla.mozilla.org/page.cgi?id=bug-writing.html), Mozilla. A good bug report has a summary that names the problem. It also gives steps to reproduce, the actual result and the expected result.
- [What makes a good bug report?](https://doi.org/10.1109/TSE.2010.63), Zimmermann and others, 2010. Developers at Apache, Eclipse and Mozilla found steps to reproduce, stack traces and test cases most helpful. Reporters found them hardest to give.
- [How to report bugs effectively](https://www.chiark.greenend.org.uk/~sgtatham/bugs.html), Simon Tatham, 1999. Let the programmer see the failure, and say exactly what you expected. Report the symptoms along with any guess.
