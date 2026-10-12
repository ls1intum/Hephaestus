---
"hephaestus": minor
---

Every record in Practice reviews, a review, an observation, a piece of feedback, a piece of work or a practice, opens in a sheet with the same structure: a title, one status line, What it is, State and history, the details, Technical and the actions. Each fact appears once, every time is a date and a time, and alerts are kept for what needs action now. A sheet links to the provider and to the setting behind an action. The one link to another record is on a review that a newer review replaced: it links to that newer review under its history, and keeps its own ID under **Technical**.

- A long title wraps to three lines and shows the whole name on hover.
- A review shows its history from the request to the first posted comment, where its time went, why it was canceled, and its own delivery checks. Each practice it reached shows its state as a coloured badge, its group in the group's colour, and the reason for its state. Its cost per attempt, model, tokens, retries, exit code and trace id are under Technical.
- An observation shows whether its review ran live, was requested or was backfilled, each dispute in the developer's words, each correction and restore, and when it stopped being current.
- A piece of feedback is titled by its whole opening line. Its history names who approved or rejected it and why it was withheld, links to each posted comment, and lists each delivery to the provider with how often it was tried, when it is tried next and the last error. The decision on a proposal is in the footer.
- A piece of work shows its author, branches, size and checks, and its history lists when it was opened, every moment recorded on it, and when it was merged or closed.
- A practice shows its full definition, as Practice setup does, every version of it with what changed, and links to its form there.

A sheet reads at 390 px without scrolling sideways.
