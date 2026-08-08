# Practice feedback language

Use these product terms in user-facing interfaces, documentation, and release notes.

This page owns the vocabulary for **observations and the feedback built from them**. The
[practice review glossary](./practice-review-glossary.mdx) owns the vocabulary for **the review
operation, the evidence contract, and the exact API, Java, and persistence names**, including
*practice review*, *binding*, *signal*, *evidence stance* and *loudness tier*. A term is defined in
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
| **Observation**       | One strength, improvement, or not-applicable result                             | finding, detection                 |
| **Practice feedback** | The collective guidance created from observations                               | AI feedback, feedback items        |
| **Message**           | One countable unit of feedback                                                  | ledger unit                        |
| **Delivery**          | A message's prepared, delivered, withheld, failed, or replaced outcome          | placement, surface                 |
| **Channel**           | Where feedback is intended to appear: reviewed work, Mentor, or Review activity   | destination, surface               |
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

**Observation, not finding.** This table told you to avoid *observation* until this pass, while the shipped
entity, the `observation` table and this table's own next row — feedback "created from observations" — all
used it. An observation is the recorded result of measuring one practice against one piece of reviewed work;
the guidance built from observations is *practice feedback*.

*Finding* survives on the delivery side, where it still names the right thing: `FindingAnchor` and
`InlineFindingChannel` are about a note pinned to a place in a diff, not about a measurement. They are in
scope for the delivery-side rename, not for this one — do not rename them here. The read APIs and the
reviews UI still say *findings* for the same reason: renaming a wire contract is its own change.

For how far a practice's result may travel in a workspace — including whether it is reviewed at all —
use **loudness tier** and its values **Off**, **Measure**, **Coach**, **Engage**, as the
[practice review glossary](./practice-review-glossary.mdx#product-terms) defines them. Do not write
*used in new reviews*: it named a boolean the API no longer carries, and it cannot express the tiers
between silent measurement and full delivery. "Not reviewed at all" is the **Off** tier, not a
separate concept.

For whether an instance entry is copied into new workspaces, use **include / exclude** (see the table
above). Do not use *shipped*, *offered*, *retired*, *ours*, *yours*, or *here* in catalog UI copy;
those terms expose implementation or depend on who is reading.
