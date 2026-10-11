## How to do it

- Address each question or change request from a reviewer in a way that they can see. For example, instead of leaving “What does this attribute mean?” unanswered, reply with what it means.
- Choose a response that fits: a fix, a link to an issue for later work, or a question back.
- If you reply after a fix, name the commit, such as “Done, renamed it to `retryLimit` in 4f2e9a1”.
- After many small fixes in one push, post one note such as “Addressed all comments in 4f2e9a1”.
- When you do not agree, reply with your reason.

## When it does not apply

- Nobody else has left a comment yet, such as on a pull or merge request that you just opened.
- The comment is your own. Only comments from other people ask something of you.
- The comment asks nothing of the change. Examples are praise, thanks, “LGTM”, “+1”, an explanation, or help for someone else.
- The reviewer approved with advice for later work, such as “next time”. The same goes for a choice they left to you, such as “optional”.

It still applies when a request uses polite words, such as “consider” or “if possible”, or when an approval waits on a fix. It also applies when advice asks a question, or when a review bot asks for a change.

## Common mistakes

- Thinking that you must agree with every comment. You only need to respond, and a reason to decline is a response.
- Taking the reviewer’s approval as your answer. An approval shows that they accept the work, not that you answered them. Answer each open question.
- Counting any later commit on the file as your answer. A commit can touch the file and still miss the point. Instead, make the change that was asked, or reply with what changed.
- Leaving a comment from a review bot without a response. Fix the point, or say why it does not apply.

## Sources

- [Handling reviewer comments](https://google.github.io/eng-practices/review/developer/handling-comments.html), Google Engineering Practices. Ask the reviewer when you do not understand a comment, and explain your reasons when you disagree.
- [Respectful changes](https://chromium.googlesource.com/chromium/src/+/HEAD/docs/cl_respect.md), Chromium project. Answer each question, and adopt, defer, or push back on each suggestion with more information.
- [Jetpack code review etiquette](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/code-review.md), AndroidX project. Follow up on every comment, with the fix or with your reason for not making it.
- [An empirical study of overlooked code review comments in OSS projects](https://doi.org/10.1109/TSE.2026.3681776), Li and others, 2026. In 80 Java projects, about 7.5% of pull requests had a comment with no reply and no fix. They took longer to review and were accepted less often.
