## How to do it

- When the issue says “as decided” or “per the design”, find the document that holds that design or decision.
- Link that document. For example, instead of “follow the agreed rollout plan”, write “follow the billing rollout plan” and link it.
- Say in your own words what the linked document decides. Some readers cannot open the link.
- If nobody wrote the decision down, record it before you link to it.

## When it does not apply

- The issue is a clear bug report that stands on its own.
- The issue asks for a small change and describes it fully.
- The issue asks a question.

It still applies when an issue says “as decided” or “per the design” and does not say where to find it.

## Common mistakes

- Adding links to look thorough. More links do not help. A link to a document without the decision does not help either. Instead, link the document that holds the decision the issue leans on.
- Linking a whole wiki space or a folder. The reader must still search for the decision. Instead, link the document that holds it.
- Writing “as we discussed” and trusting that the reader was there. The person who picks up the issue may not have been. Instead, link the record of what was decided.

## Sources

- [Writing good CL descriptions](https://google.github.io/eng-practices/review/developer/cl-descriptions.html), Google Engineering Practices. Google advises linking design documents in a change description. It also advises enough context in the text, because some readers cannot open a link.
- [Documenting architecture decisions](https://www.cognitect.com/blog/2011/11/15/documenting-architecture-decisions), Michael Nygard, 2011. Without the recorded reason for a decision, a new teammate can only accept it blindly or change it blindly.
- [Information needs in collocated software development teams](https://doi.org/10.1109/ICSE.2007.45), Ko, DeLine and Venolia, 2007. Developers often put off design questions, such as why code was written a certain way. The only source was a coworker who was away. Two of the authors work at Microsoft, which sells developer tools.
