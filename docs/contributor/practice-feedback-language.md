# Practice feedback language

Use these product terms in user-facing interfaces, documentation, and release notes. Practice-evidence terms and
their exact API, Java, and persistence names are defined in the
[practice assessment glossary](./practice-assessment-glossary.mdx).

Within a surface already titled **Practice reviews**, shorten **practice feedback** to **feedback**.
Use the full term when the surrounding context does not establish which kind of feedback is meant.
Within **Practice setup** or **Practice catalog**, shorten **practice area** to **area**.

| Term                  | Meaning                                                                         | Avoid for this concept             |
| --------------------- | ------------------------------------------------------------------------------- | ---------------------------------- |
| **Practice**          | A defined way of working used to review work                                    | rule, detector                     |
| **Practice area**     | A group of related practices                                                    | category, goal, learning objective |
| **Unassigned**        | Practices that are not in a practice area                                       | ungrouped, unbound                 |
| **Practice review**   | One assessment of reviewed work against the practices selected when the review starts | run                          |
| **Finding**           | One strength, improvement, or not-applicable result                             | observation                        |
| **Practice feedback** | The collective guidance created from findings                                   | AI feedback, feedback items        |
| **Message**           | One countable unit of feedback                                                  | ledger unit                        |
| **Delivery**          | A message's prepared, delivered, withheld, failed, or replaced outcome          | placement, surface                 |
| **Channel**           | Where feedback is intended to appear: reviewed work, Mentor, or Review activity   | destination, surface               |
| **Reviewed work**     | A pull request, merge request, issue, or conversation being assessed            | artifact, target                   |
| **Developer**         | The person a finding is about                                                   | learner                            |
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

Use **used in new reviews** for workspace participation. Do not use *shipped*, *offered*, *retired*,
*ours*, *yours*, or *here* in catalog UI copy; those terms expose implementation or depend on who is
reading.
