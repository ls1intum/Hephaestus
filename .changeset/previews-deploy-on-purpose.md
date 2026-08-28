---
"hephaestus": minor
---

Pull request previews are now self-service. Add the `preview` label to a pull request in this repository and it deploys; every commit after that redeploys on its own. A preview waits only for its images to be published, never for the test suite, so it exists even when the tests are red — and it runs the same artifacts staging and production run, so what you see is what ships. A comment on the pull request carries the preview link, and GitHub's native deployment link opens it too. Removing the label, closing the pull request, or converting it back to draft removes the stack. Up to three previews run at once by default, and when the host is full the pull request comment names the ones holding the slots.

Preview stacks use their own database, message broker, credentials, and Docker networks, and reach neither the staging Docker socket, its data, nor any integration credential. Previews never run for forks, nor for changes to the deployment workflows themselves. Stacked pull requests each get their own preview.

**Operators:** follow the preview runbook before enabling the Coolify application. It requires a `preview` repository label, a preview-only Coolify application, two scoped Coolify secrets, a forced-command SSH cleanup key, and the repository variables listed there — including the optional `PREVIEW_MAX_ACTIVE` limit. Keep Coolify's automatic repository webhook disabled.
