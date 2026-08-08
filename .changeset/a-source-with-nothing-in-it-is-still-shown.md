---
"hephaestus": patch
---

A review can now tell the difference between evidence it checked and found nothing in, and evidence that was never gathered. Some sources — a pull request nobody commented on, a project with no other tracked work, a change that links no documentation — were previously left out of the review's workspace entirely when they turned up empty, which looks exactly like a source that failed to collect. Reviews could read that as licence to conclude nothing, or as licence to conclude too much. Those sources are now always present and simply empty, which removes a class of findings that were confidently right or confidently wrong for the same reason.
