---
"hephaestus": minor
---

Fixes self-hosted GitLab instances being pointed at someone else's GitLab. The setting for which
GitLab workspace creation and repository sync talk to is documented to follow your GitLab login URL
when you do not set it, but the shipped compose file pinned it to a specific university's server, so
that fallback never happened: an operator who configured only their GitLab login silently got a
GitLab they had never named. It now follows the login URL as documented.

**Operators:** if you run against a GitLab other than `gitlab.com` and have been relying on the
shipped default rather than setting `GITLAB_DEFAULT_SERVER_URL` yourself, set it (or
`GITLAB_OAUTH_BASE_URL`) before upgrading — otherwise sync will move to `gitlab.com`.
