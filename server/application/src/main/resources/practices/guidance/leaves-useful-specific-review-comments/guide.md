## How to do it

- Point at the exact place. Comment on the line, or name the file, function or value.
- Say what is wrong and what to change. Instead of “Fix this”, write “`retries` never resets, so reset it after a successful call”.
- If you are not sure of the fix, name the problem or ask a question that the author can answer.
- Give a note on the conversation the same detail as a note on a line. Name the file or function in it.
- Mark an optional suggestion with “Optional:”. Then the author knows which changes you need.

## When it does not apply

- The practice looks at your comments, not at the code. A large pull or merge request with little review is not a lapse.
- The pull or merge request has no substantive review comment yet.
- The note is the author’s own note.
- The note comes from a bot, an app or an automated review service.
- The note is only an approval, such as “LGTM” or “+1”.
- The note reports what someone did and asks for no change.
- The note is off-topic and is not about the change.

It still applies when you write the note on the conversation and not on a line.

## Common mistakes

- Softening a point until it says nothing, such as “Maybe look at this again”. Instead, name the problem and the change.
- Leaving a bare “?” on a line. Instead, write the question you want answered, such as which input reaches this branch.
- Writing “Needs cleanup” on the conversation. Instead, name the file or function and say what to clean up.

## Sources

- [How to write code review comments](https://google.github.io/eng-practices/review/reviewer/comments.html), Google Engineering Practices. Google advises a balance between pointing out problems and giving direct guidance. It also suggests labels that tell required changes from optional suggestions.
- [Characteristics of useful code reviews: an empirical study at Microsoft](https://www.microsoft.com/en-us/research/publication/characteristics-of-useful-code-reviews-an-empirical-study-at-microsoft/), Bosu, Greiler and Bird, 2015. Comments that authors found useful often led to a change near the lines that they pointed at. Microsoft sells code review tools.
- [Predicting usefulness of code review comments using textual features and developer experience](https://doi.org/10.1109/MSR.2017.17), Rahman, Roy and Kula, 2017. Useful comments named relevant code elements and shared more words with the changed code.
