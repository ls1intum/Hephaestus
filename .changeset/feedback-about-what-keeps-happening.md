---
"hephaestus": minor
---

Hephaestus now produces a third kind of feedback: written for one developer, private to them, and
about what keeps happening across several pieces of their work rather than what is wrong in one.
Where a note on a pull request says what to change before merging, this says the habit behind the
notes, the pieces of work it showed up on, and one thing to try on the next change. It is not the
pull-request comments reorganised — it is composed separately, by its own step, after a review has
finished measuring, and it exists precisely to say the thing a comment on one change can never say.

**This release has no page of its own for it.** The feedback is composed, stored and available
through the API, and it is what a developer's practice pages will read; it is not reachable as a
standalone surface yet.

A message is only composed once the same problem has shown up on at least two separate pieces of
work, at most two habits are offered at a time, and the same habit stays quiet for two weeks after it
was raised. Feedback that judges the person rather than the work is refused here as it is in the
mentor chat.

**It is private.** Workspace admins and instructors can still see on the review surfaces that a
message was prepared, whether it was delivered, and why one was withheld — they cannot read what it
said. That is deliberate and it is the direction that can be revisited later; the reverse cannot.

Feedback from a retrospective backfill campaign is not composed here. A backfill is a snapshot of
finished work, and this feedback makes claims about what keeps happening, so a sweep over a year of
history will not arrive as a wall of advice on the day you run it.

Reviews of documentation pages continue to record what they find without delivering anything; turning
that on is a separate, deliberate step.

Practices that judge how somebody *reviews* a teammate's change — leaving specific comments, asking
rather than demanding, reading before approving — now say so, and a review that cannot name the
reviewer does not run rather than recording the observation against the author of the change, which
is what happened before. **Operators:** an occasion records who it judges, and a workspace set up
before this release still holds the old wording for those three practices until they are updated from
the catalogue on the practice-catalogue screen; until then they keep behaving as they did. Every other
practice is unaffected.
