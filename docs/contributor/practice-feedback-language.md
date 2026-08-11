# Practice feedback language

Use these product terms in user-facing interfaces, documentation, and release notes.

This page owns the vocabulary for **observations and the feedback built from them**. The
[practice review glossary](./practice-review-glossary.mdx) owns the vocabulary for **the review
operation, the evidence contract, and the exact API, Java, and persistence names**, including
*practice review*, *binding*, *signal*, *evidence stance*, *autonomy tier* and *feedback reach*. A term is defined in
one of the two and cited from the other; when the two disagree, that is a bug in one of them, not a
choice for the writer.

Within a surface already titled **Practice reviews**, shorten **practice feedback** to **feedback**.
Use the full term when the surrounding context does not establish which kind of feedback is meant.
Within **Practice setup** or **Practice catalog**, shorten **practice area** to **area**.

| Term                  | Meaning                                                                         | Avoid for this concept             |
| --------------------- | ------------------------------------------------------------------------------- | ---------------------------------- |
| **Practice**          | A defined way of working used to review work                                    | rule, detector                     |
| **Practice area**     | A group of related practices                                                    | category, goal, learning objective |
| **Unassigned**        | Practices that are not in a practice area                                       | ungrouped, unbound                 |
| **Observation**       | One recorded result of reviewing one practice against one piece of reviewed work | finding, detection                 |
| **Practice feedback** | The collective guidance created from observations                               | AI feedback, feedback items        |
| **Message**           | One countable unit of feedback                                                  | ledger unit                        |
| **Delivery**          | A message's prepared, delivered, withheld, failed, or replaced outcome          | placement, surface                 |
| **Channel**           | Where feedback is intended to appear; see [feedback reach](./practice-review-glossary.mdx#feedback-reach) for which destinations exist | destination, surface |
| **Reviewed work**     | A pull request, merge request, issue, or conversation being reviewed             | artifact, target                   |
| **Developer**         | The person an observation is about                                              | learner                            |
| **Contributor**       | A repository role relevant to review eligibility                                | user, when the role matters        |
| **Heph**              | The conversational assistant                                                    | agent, bot                         |
| **Mentor**            | The product area for conversations with Heph                                    |                                    |
| **Hephaestus**        | The application that runs reviews and prepares feedback                         | agent                              |
| **Hephaestus default** | A practice or area bundled with the running Hephaestus release                  | shipped entry                      |
| **Instance catalog**  | The starting set copied into each new workspace                                  | curated catalog                    |
| **Workspace practices** | Independent definitions used for reviews in one workspace                     | workspace catalog                  |
| **Review rules**      | Inputs and criteria that determine review behavior                               | detector configuration             |
| **Developer guidance** | Explanatory text that does not change review behavior                           | learner guidance                   |
| **Customize**         | Change a default or catalog-based definition                                     | override                           |
| **Include / exclude** | Whether an instance entry is copied into new workspaces                          | offer, retire                      |
| **No Hephaestus default** | An instance-maintained entry with no bundled definition                      | ours, yours                        |

Use provider-specific names such as **pull request** or **merge request** when the provider is known;
otherwise write **pull or merge request**.

**Observation, not finding**, for the measurement. *Finding* names the right thing only on the delivery
side, where it means a note pinned to a place in a diff (`FindingAnchor`, `InlineFindingChannel`) rather
than a measurement. The read APIs and the reviews UI say *findings* because renaming a wire contract is its
own change; that is a reason to leave them alone, not a licence to write *finding* in new copy.

For how much a workspace lets the system do on its own about a practice — including whether it is
reviewed at all — use **autonomy tier** and its three values **Off**, **Propose**, **Deliver**, as
the [practice review glossary](./practice-review-glossary.mdx#product-terms) defines them. Do not write
*used in new reviews*: it is a boolean where the concept has three values, so it cannot express the tier
between silent measurement and unasked delivery. "Not reviewed at all" is the **Off** tier, not a separate
concept. **Propose** reviews the work and records everything it sees and sends nothing; do not write that
it prepares, drafts or previews feedback, because at that tier no feedback is ever composed. Do not write
*loudness tier*: it named a ladder that mixed how much the system does with where it says it, and the
second half is now **feedback reach**, a separate workspace setting.

A tier is also rarely a fact about one practice alone. Most practices hold no tier and inherit their area's,
and most areas inherit the workspace's, so write **effective tier** for the one in force and **override**
for a tier set at the level being discussed. *Inherited* is about the level being described, not about
which level answered.

For whether an instance entry is copied into new workspaces, use **include / exclude** (see the table
above). Do not use *shipped*, *offered*, *retired*, *ours*, *yours*, or *here* in catalog UI copy;
those terms expose implementation or depend on who is reading.
