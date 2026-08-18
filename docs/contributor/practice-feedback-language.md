# Practice feedback language

Use these product terms in user-facing interfaces, documentation, and release notes.

This page owns the vocabulary for **observations and the feedback built from them**. The
[practice review glossary](./practice-review-glossary.mdx) owns the vocabulary for **the review
operation, the evidence contract, and the exact API, Java, and persistence names**, including
*practice review*, *binding*, *signal*, *evidence stance* and *autonomy tier*. A term is defined in
one of the two and cited from the other; when the two disagree, that is a bug in one of them, not a
choice for the writer.

Within a surface already titled **Practice reviews**, shorten **practice feedback** to **feedback**.
Use the full term when the surrounding context does not establish which kind of feedback is meant.
Within **Practice setup** or **Practice catalog**, shorten **practice area** to **area**.

| Term                                | Meaning                                                                                                                                                    | Avoid for this concept                                               |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| **Practice**                        | A defined way of working used to review work                                                                                                               | rule, detector                                                       |
| **Practice area**                   | A group of related practices                                                                                                                               | category, goal, learning objective                                   |
| **Unassigned**                      | Practices that are not in a practice area                                                                                                                  | ungrouped, unbound                                                   |
| **Observation**                     | One recorded result of reviewing one practice against one piece of reviewed work                                                                           | finding, detection                                                   |
| **Practice feedback**               | Guidance written from observations and addressed to a developer — both the whole and the countable unit                                                    | message, AI feedback, feedback item, ledger unit                     |
| **Delivery**                        | Whether one piece of feedback was prepared, delivered, withheld, failed, or replaced                                                                       | placement, surface                                                   |
| **Channel**                         | Where one piece of feedback is intended to appear — a fact about that piece, not a workspace setting; the destinations are the `FeedbackChannel` constants | destination, surface, reach                                          |
| **Practice feedback about a habit** | Practice feedback prepared for one developer about what recurs across their work, readable by nobody else — the `IN_APP` channel                           | my feedback, private view, profile, reflection, reflection dashboard |
| **Reviewed work**                   | A pull request, merge request, issue, or conversation being reviewed                                                                                       | artifact, target                                                     |
| **Developer**                       | The person an observation is about                                                                                                                         | learner                                                              |
| **Contributor**                     | A repository role relevant to review eligibility                                                                                                           | user, when the role matters                                          |
| **Heph**                            | The conversational assistant                                                                                                                               | agent, bot                                                           |
| **Mentor**                          | The product area for conversations with Heph                                                                                                               |                                                                      |
| **Hephaestus**                      | The application, named only where the application itself is the subject — installing it, an account linked to it, a release of it                          | agent                                                                |
| **Hephaestus default**              | A practice or area bundled with the running Hephaestus release                                                                                             | shipped entry                                                        |
| **Instance catalog**                | The starting set copied into each new workspace                                                                                                            | curated catalog                                                      |
| **Workspace practices**             | Independent definitions used for reviews in one workspace                                                                                                  | workspace catalog                                                    |
| **Review rules**                    | Inputs and criteria that determine review behavior                                                                                                         | detector configuration                                               |
| **Developer guidance**              | Explanatory text that does not change review behavior                                                                                                      | learner guidance                                                     |
| **Customize**                       | Change a default or catalog-based definition                                                                                                               | override                                                             |
| **Include / exclude**               | Whether an instance entry is copied into new workspaces                                                                                                    | offer, retire                                                        |
| **No Hephaestus default**           | An instance-maintained entry with no bundled definition                                                                                                    | ours, yours                                                          |

Use provider-specific names such as **pull request** or **merge request** when the provider is known;
otherwise write **pull or merge request**.

**Feedback is the countable unit, and there is no other word for it.** Do not reach for *message*, *item*,
*note*, or *entry* to get a noun that pluralises; *feedback* is uncountable, so the fix is to phrase the
count so the noun is not needed rather than to invent a second word for the same thing.

| Instead of                   | Write                                                      |
| ---------------------------- | ---------------------------------------------------------- |
| 3 messages                   | 3 pieces of feedback                                       |
| Messages                     | Feedback — a heading naming the collection needs no plural |
| No messages yet              | No feedback yet                                            |
| This message                 | This feedback                                              |
| Message details              | Feedback details                                           |
| Findings behind this message | Observations behind this feedback                          |
| Queued for conversation      | Prepared for conversation                                  |

A column that counts per row is headed **Feedback** and the cell holds the number alone. Where a sentence
needs a singular subject, name what the feedback is *about* — "the feedback for this observation", not
"the message for this observation".

**Say what happens, not who does it.** On a practice surface, and in the footer of a delivered review
comment, the subject is the operation and its results — *a practice review*, *a review*, *feedback*, *an
observation* — not the application performing them. "What a review does on its own", not "What Hephaestus
does on its own"; "Practice review", not "Hephaestus Agent". Two reasons, and both outlive the current copy:
the product name adds a second voice to a screen that is already inside the product (and, on a provider, the
bot account the comment is posted under has already attributed it), and reaching for a name for the doer is
exactly what pulls a writer towards *agent*, the word this vocabulary bans. **Hephaestus** stays the right
word where the application itself is the subject — installing it, linking an account to it, a release of it,
and in the compound terms **Hephaestus default** and **No Hephaestus default** — and it is still the actor a
contributor doc names when explaining what the system does. This rule is about the surfaces a developer or a
workspace administrator reads.

**All three channel names say where the feedback lands, and nothing else.** `IN_CONTEXT` lands on the work
itself — a pull request summary or inline note, an issue comment. `IN_CHAT` lands in a turn of a
conversation, wherever that conversation runs: the in-app mentor, or Slack. `IN_APP` lands on the
developer's own practice pages, where nobody replies to it.

The mentor also renders inside the app, so the line between the last two is *dialogic or not*: ask **is it
a turn?** before **which screen?** A channel names a destination, never a cognitive level and never what
the developer is supposed to do about it. The three do line up with the task, self-regulation and process
levels of Hattie & Timperley's model (ADR 0029), but a level is a claim about content that a destination
cannot enforce, so do not use the level as the name.

`IN_APP` is the code noun — the enum constant, the `chk_feedback_channel` value, and the endpoint
`GET /workspaces/{workspaceSlug}/practices/feedback/in-app`. It has been renamed three times
(`REFLECTION_DASHBOARD`, `PROFILE`, `REFLECTION`) and this is the last one; ADR 0029 records why each earlier
name failed. Do not reintroduce
any of them: *profile* is the public page at `/user/{username}`, *private view* describes a property
rather than naming one, and *reflection* names what the developer is supposed to do rather than where the
feedback lands — an outcome nothing in the system can observe. On an operator surface, where the question
is where a piece of feedback landed, the place reads **On their practice pages**.

**Feedback waiting for a mentor conversation is *prepared*, never *queued*.** A queue implies somebody has
to work it; nothing does — `FeedbackDeliveryState.PREPARED` auto-advances to `DELIVERED` on the next chat
turn that links the feedback. The label is **Prepared for conversation**. *Prepared* is also not available
for a suppressed observation: at the **Propose** tier no feedback is composed at all, so nothing is prepared
there either.

**Observation, not finding**, for the measurement — in copy, URLs, API schema, field names, and Java.
Delivery uses `FeedbackAnchor` and `InlineFeedbackChannel`; the mentor uses `link_observation` and
`data-observation`. The schema and wire protocol have no aliases for the retired vocabulary. The only
compatibility surface is an HTTP redirect from the former reviews URL so existing bookmarks do not break;
it carries no data contract and new links never use it.

Everything else is an observation, including the read APIs and the reviews UI — the surfaces an operator
actually reads are exactly where the banned word does the most damage. Those names are
`ReviewObservation`, `ReviewObservationDetail`, `ReviewBoundObservation`, `observationId`, and the
workspace-admin route `/workspaces/{workspaceSlug}/practices/reviews/observations`. Developer-scoped
reads live under `/workspaces/{workspaceSlug}/practices/observations`. Apart from the web-route redirect, a
*finding* in this subsystem is a bug.

For how much a workspace lets the system do on its own about a practice — including whether it is
reviewed at all — use **autonomy tier** and its three values **Off**, **Propose**, **Deliver**, as
the [practice review glossary](./practice-review-glossary.mdx#product-terms) defines them. Do not write
*used in new reviews*: it is a boolean where the concept has three values, so it cannot express the tier
between silent measurement and unasked delivery. "Not reviewed at all" is the **Off** tier, not a separate
concept. **Propose** reviews the work and records everything it sees and sends nothing; do not write that
it prepares, drafts or previews feedback, because at that tier no feedback is ever composed. Do not write
*loudness tier*: it reads as a volume control, and the tier is not one — it is the whole delivery decision,
so **Deliver** means delivered wherever the reviewed work allows. There is no second setting narrowing
where a delivering practice may speak; do not write *feedback reach*, which named one that no longer exists.

A tier is also rarely a fact about one practice alone. Most practices hold no tier and inherit their area's,
and most areas inherit the workspace's, so write **effective tier** for the one in force and **override**
for a tier set at the level being discussed. *Inherited* is about the level being described, not about
which level answered.

For whether an instance entry is copied into new workspaces, use **include / exclude** (see the table
above). Do not use *shipped*, *offered*, *retired*, *ours*, *yours*, or *here* in catalog UI copy;
those terms expose implementation or depend on who is reading.
