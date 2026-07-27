---
"hephaestus": minor
---

The AI area of the API now says one thing one way. Every address in it is either `llm/…` (the models
and what they cost) or `agents/…` (the things that run them); the `ai-settings` container is gone, and
"BYO" is gone from every address, request field and label — a workspace's own connected provider is
called exactly that, in the API as well as on screen. (The audit log is append-only, so entries it
already wrote keep the name they were written under.) The two monthly spend caps are also the same
instrument: an instance admin's cap on a workspace and that workspace's cap on its own provider have
the same address shape and the same request body, `{ "monthlyBudgetUsd": … }`, differing only in who
is allowed to set them.

**Operators:** the addresses below existed in the previous release and have moved or been removed.
There are no redirects or aliases — a script calling an old address gets a 404, so update it before
upgrading.

| Was | Now |
| --- | --- |
| `GET /workspaces/{slug}/agent-jobs…` | `GET /workspaces/{slug}/agents/jobs…` |
| `GET /workspaces/{slug}/ai-settings`, `PATCH …/ai-settings/practice-review` | `GET`/`PATCH /workspaces/{slug}/practices/review-settings` |
| `/workspaces/{slug}/agent-configs…`, `PUT …/ai-settings/practice-config`, `PUT …/ai-settings/mentor-config` | removed — a workspace's AI setup is one binding per purpose now |

If you read `GET /ai-settings` for `practicesEnabled` / `mentorEnabled`, take them from the workspace
itself (`GET /workspaces/{slug}`); the review-settings response carries the review policy only.

The rest of the AI area is new in this release rather than moved, so nothing calls it yet:
`GET /admin/llm/usage`, `PUT /admin/workspaces/{slug}/llm/budget`, `GET /workspaces/{slug}/llm/usage`,
`PUT /workspaces/{slug}/llm/budget`, `GET /workspaces/{slug}/llm/settings`, and the per-purpose
bindings — `GET /workspaces/{slug}/agents` to list them, `PUT`/`DELETE
/workspaces/{slug}/agents/{purpose}` to set or clear one.

The workspace console's retired `/w/{slug}/admin/ai/*` browser URLs no longer redirect either; those
pages have been at `/w/{slug}/admin/models` and `/w/{slug}/admin/practices` since the previous
release.
