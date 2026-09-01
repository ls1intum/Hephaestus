---
"hephaestus": minor
---

Instance administrators can publish workspace-targeted surveys and review survey responses and product feedback
without sending data to an external analytics service. Contributors can send feedback, respond to surveys, or
permanently dismiss them; submissions remain in the instance database.

**Operators:** the PostHog integration is removed entirely. `POSTHOG_ENABLED`, `POSTHOG_API_HOST`,
`POSTHOG_PROJECT_ID`, `POSTHOG_PROJECT_API_KEY`, and `POSTHOG_PERSONAL_API_KEY` are no longer read
and can be deleted from your `.env`; no replacement variable is needed and no other action is
required.
