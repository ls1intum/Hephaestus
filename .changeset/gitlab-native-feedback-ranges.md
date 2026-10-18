---
"hephaestus": patch
---

GitLab line feedback about several lines now opens one thread on that whole range in the merge request diff, instead of a thread on its last line only. When GitLab cannot place feedback on the diff, or the feedback is about a whole file, it now opens a thread on the merge request overview, so the author can reply and resolve it. Feedback that was already posted on its last line stays in place and is not posted again.
