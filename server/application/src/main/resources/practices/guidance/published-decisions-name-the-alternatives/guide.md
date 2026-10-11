## How to do it

- Name each option that was really on the table, not only the one you chose.
- For each option you did not choose, explain why it lost. Say what limit it broke, what it would have cost, or what you would have had to give up. For example, instead of “We chose PostgreSQL”, write “We chose PostgreSQL. We ruled out SQLite because our imports need several writers at once.”
- Make the reason about that option. A sentence about what the chosen option does well does not say why the others were worse.
- If there was no real alternative, say so, and name the limit that left you with one option.
- Use the form that suits your team. A section with a heading, a table, and plain prose all work. No heading name is needed.

## When it does not apply

- The document does not name one decision and take a position on it.
- The document is a design overview, a runbook, a how-to guide, or a meeting note.
- The document is an index page, or a specification that decides nothing.

It still applies when the record is only a proposal, or when it follows no template.

## Common mistakes

- Treating the heading as the point. An “Alternatives considered” section that is empty, or that says only “none”, names nothing. Instead, name the options and why they lost. If there were none, explain why.
- Listing option names with no reasons. A list such as “Kafka, RabbitMQ, SQS” shows what you looked at, not why each one lost. Instead, explain why each option lost.
- Praising the winner instead of judging the others. “PostgreSQL is fast and well known” is a reason to pick it. Instead, say what was wrong with each option you did not pick.

## Sources

- [Maintain an architecture decision record (ADR)](https://learn.microsoft.com/en-us/azure/well-architected/architect-role/architecture-decision-record), Microsoft Azure Well-Architected Framework. A decision record includes the alternatives you ruled out, and a record without justification loses value over time.
- [Architecture decision records overview](https://docs.cloud.google.com/architecture/architecture-decision-records), Google Cloud Architecture Center. A decision record gives an overview of the key options and the reasons behind the accepted choice.
- [Markdown Architectural Decision Records (MADR)](https://adr.github.io/madr/), Oliver Kopp, Anita Armbruster and Olaf Zimmermann. The template lists the considered options and gives each one its arguments for and against.
