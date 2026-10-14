---
"hephaestus": minor
---

You can review changes and scan repositories on their monitored default branch with the same workspace practices.
Results show Met, Not met, Does not apply or Could not determine.
Not met results list problems, each with its own severity and evidence.
Scan reports distinguish files reviewed, reused results, withheld work and work not reviewed.
You can export a scan report as SARIF.
An incomplete scan does not claim that problems were fixed or files disappeared.

Practice definitions now have fixed versions.
The editor separates Review on, Applies when, About, Reviewed by and Feedback mode.
Renaming a practice or moving it between groups does not invalidate its results.
Off stops feedback at every destination while eligible reviews can still record results.
Choose People or Nobody under Reviewed by to prevent automated review of a practice.

You can edit practice scripts in the app without an external editor download.
Retrying an editor error preserves the form draft.
Scripts can decide from captured records, ask a review model, or call assigned decision, embedding and reranking models.

Every script uses the same result contract and evidence checks.
A script failure creates no claim about the work and never falls back to the built-in script.
Direct model calls follow the developer's AI choice and the review's stricter model tier.
Operators can inspect script execution, model use and cost for each practice.

People have one account across connected identities and workspaces.
Roster records can exist before sign-in.
AI and external-feedback choices apply across your workspaces.

Feedback responses keep resolution and usefulness separate.
A disagreement does not change the result by itself.
Admins can mark results incorrect or accepted, and remove those marks.
An accepted mark removes the result from open-problem lists without changing its outcome or removing an incorrect mark.

Operator review views keep outcomes and delivery history visible when source access cannot verify text or evidence.
External clients must handle absent summaries and evidence in that state.

**Operators:** This release changes the database, practice scripts and API together.
Drain workers, stop all runtime roles, and verify a database backup before the upgrade.
Upgrade all roles, the agent image, the webapp, browser extensions and custom clients together.

Rewrite changed custom scripts for the new contract.
Review practices left Off if you intended to prevent automated review.
Preview databases from earlier pushes of this branch must be reset.
Follow the migration guide for the complete procedure and the new token and search-unit limits.
