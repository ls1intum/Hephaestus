## How to do it

- When the author or another reviewer asks you to explain a choice, write your answer with your approval.
- If your project’s review rules ask reviewers to record what they checked, write it down with your approval.
- Name something that is in the change: a file, a function or a term that you checked. For example, a change sets who can edit a project. Instead of “LGTM” (looks good to me), write “`canEdit()` now checks the project role, so viewers cannot save”.
- If you’re still unsure, ask one real question about the change, such as which code calls the new check. A real question also shows your reasoning.
- Write it where your team reads it. An inline comment and a note in the discussion both work. One short comment is enough.

## When it does not apply

- Nobody has reviewed the change yet. It has no approval and no reviewer comment. This includes a draft.
- There is no review decision, and no reviewer has written a comment with substance.
- Nobody asked for a reason, no review rule needs one, and you approved without one.

## Common mistakes

- Treating size or risk alone as a reason you must explain. Instead, write your reason when someone asks for it or when your review rules need it.
- Answering a request for your reasoning with only “LGTM” or a thumbs-up. Instead, write one sentence that says what you checked and why it answers the concern.
- Giving a reason that fits any change, such as “Looks safe to me”. Instead, name the file, function or term that you checked, so others can check it too.
- Reading a teammate’s bare approval as proof that they skipped the change. Instead, ask for their reason if you need it.

## Sources

- [How to write code review comments](https://google.github.io/eng-practices/review/reviewer/comments.html), Google Engineering Practices. Saying why helps the author understand a comment. Google also says that you do not always need to.
- [Information needs in contemporary code review](https://research.tudelft.nl/en/publications/information-needs-in-contemporary-code-review), Pascarella and others, 2018. In open-source code review, reviewers most often asked about alternatives, their own understanding of the change, and its reason.
- [Expectations, outcomes, and challenges of modern code review](https://doi.org/10.1109/ICSE.2013.6606617), Bacchelli and Bird, 2013. At Microsoft, understanding the change was the key part of code review, and reviews spread knowledge and team awareness. Microsoft sells code review tools.
