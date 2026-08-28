---
"hephaestus": minor
---

Pull request previews are now self-service. Add the `preview` label to a pull request in this repository and it deploys; every commit after that redeploys on its own. A preview waits only for its images to be published, never for the test suite, so it exists even when the tests are red — and it runs the same artifacts staging and production run, so what you see is what ships. A comment on the pull request carries the preview link, and GitHub's native deployment link opens it too. Removing the label, closing the pull request, or converting it back to draft removes the stack. Up to three previews run at once by default, and when the host is full the pull request comment names the ones holding the slots.

Each preview starts from a copy of staging's database, so workspaces and synced work are already there — and that copy is silenced before the app starts: review triggers, agent bindings and sweep schedules off, queued jobs cancelled, and the sign-in identity dropped so the preview issues its own tokens. It reads staging's event stream on a consumer of its own. Agent runs and inbound webhooks stay off. Previews never run for forks, nor for changes to the deployment workflows themselves. Stacked pull requests each get their own preview.

**Operators:** follow the preview runbook before enabling the Coolify application. It requires a `preview` repository label, a preview-only Coolify application, two scoped Coolify secrets, and the repository variables listed there — including the optional `PREVIEW_MAX_ACTIVE` limit. Previews must be deployed onto the staging host: they seed from its database and read its event stream. Keep Coolify's automatic repository webhook disabled.
