---
"hephaestus": minor
---

Adds a "Review this now" button to a piece of work's review activity page, so you can ask for a review instead of waiting for one. Only the work's author or assignees, or a workspace admin, can ask — a review's feedback goes to the author, not to whoever asked for it. When no review starts, the page says why in the same words the rest of the product uses, rather than reporting an error.

Asking is rate limited twice: a second ask about the same piece of work inside the workspace's review cooldown is turned down, and one person can ask for at most 5 reviews an hour in a workspace.

The per-person hourly allowance is `HEPHAESTUS_PRACTICE_REVIEW_MAX_REQUESTS_PER_REQUESTER_PER_HOUR` (optional, default 5; 0 switches it off), and the per-work limit reuses the review cooldown you already configure.
