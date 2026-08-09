---
"hephaestus": minor
---

Only people the work belongs to can now ask for a review of it. A `/hephaestus review` comment on a merge request is carried out when the commenter is the author or an assignee of that merge request, or a workspace admin; anyone else's command is declined and logged. Previously any account that could comment could start a review of anybody's work, and the coaching it produced went to the author rather than to whoever asked.

Requested reviews are also now visible in the artifact trace, which says that a person asked and what came of it, and their findings are kept out of the live trend line — a review somebody asks for is about work they were already unsure of, so counting it alongside automatically triggered reviews made a workspace's numbers look worse than its work was.
