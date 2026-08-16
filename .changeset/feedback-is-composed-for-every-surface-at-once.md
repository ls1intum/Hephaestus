---
"hephaestus": minor
---

Feedback for all three surfaces is now written in one deliberate step after the review has finished
measuring, instead of each surface deciding on its own. That step sees what the review just found,
what earlier reviews recorded about the same person, what has already been said to them, what is
still waiting to be read, and how each recurring problem moved — so it can say "this is the third
time", stay quiet about something that has not changed, or point out that last review's gap is
closed.

Each surface now gets what it is for. A note on the merge request is about this change and the one
edit to make before merging. The feedback written for you alone is about what keeps happening across
several pieces of your work and one habit to try next time, and it never re-quotes a line of code. A mentor
conversation opens with a question so you reach the diagnosis yourself, and holds the evidence back
until you have answered — the mentor still writes the words of the turn, so nothing goes stale
waiting to be raised.

A note on the merge request can only be placed on a line the change actually touches: the composition
step names an observation and one of its own recorded citations, and the server resolves the file and
line from that, so a note can no longer land somewhere the diff does not contain.

Fixes advice on an observation's detail page being read from another workspace's feedback when the
same observation was referenced from both.
