#### 🔴 The PostHog integration is removed

**Affected**: deployments that carry PostHog settings in their `.env` or pass them as deploy
secrets. Deployments that never configured PostHog need no changes.

**Before**: the stack read `POSTHOG_ENABLED`, `POSTHOG_API_HOST`, `POSTHOG_PROJECT_ID`,
`POSTHOG_PROJECT_API_KEY`, and `POSTHOG_PERSONAL_API_KEY`, and the webapp could load the PostHog
client and its cloud-backed surveys when they were set.

**After**: none of these variables are read anywhere; product feedback and surveys are stored in
the instance's own PostgreSQL and reviewed in **Administration → Feedback**. No replacement
variable exists.

**Migration**: delete the `POSTHOG_*` lines from your `.env` and remove any corresponding deploy
or preview secrets; leftover values are ignored but keep an unused credential in circulation, so
also revoke the PostHog personal API key in PostHog itself if one was ever issued. The schema
migration for the new feedback tables runs automatically.
