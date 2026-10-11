## How to do it

- Write a title that names the concrete change, as an instruction. A prefix such as a story ID or a tag, like “[Feat] US5.2:”, is fine.
- Say why the change exists, in the title or the description. Name the problem it solves, the effect for users, the goal, or a constraint. For example, instead of listing files, write “Let organizers export results as CSV, so they can share them”.
- Give the reason for the whole change, not only for one choice inside it. “Moved the helpers to match the naming” explains a choice. It does not say why the change exists.
- If the change answers an issue, also write its main point in the description. A link tells the reviewer where to look, but it does not say why.
- Before you ask for a review, replace every template placeholder with your own words.

## When it does not apply

- There is no usual case where it does not apply. Every pull or merge request needs a title and a description that say what changed and why.

It still applies when the change is small.

## Common mistakes

- Thinking you need a “Why” heading or words such as “because” or “so that”. Instead, name the problem or the goal anywhere in the title or the description. A bullet or a checklist item works too.
- Repeating the change as its reason. “I added the export screen as planned” says what, and “Sprint 3” says when. Instead, name the problem or the user goal that the change serves.
- Listing only the parts you built. The reviewer then knows what changed, but not why. Instead, name the problem or the goal that those parts serve. It can be part of the same sentence.
- Leaving an old reason in place after the change grew or changed direction. A reason for a different change sends the reviewer the wrong way. Instead, update the description when the work changes.

## Sources

- [Writing good CL descriptions](https://google.github.io/eng-practices/review/developer/cl-descriptions.html), Google Engineering Practices. The first line says what the change does. The description also says why, because code can show what it does but not why it exists.
- [Describe your changes](https://www.kernel.org/doc/html/latest/process/submitting-patches.html#describe-your-changes), Linux kernel documentation. Describe the problem and its effect on users, and make the description clear without links.
- [Confusion in code reviews: reasons, impacts, and coping strategies](https://doi.org/10.1109/SANER.2019.8668024), Ebert, Castor, Novielli and Serebrenik, 2019. Missing rationale was the reason for confusion that reviewers reported most often, and confusion delays the merge decision.
