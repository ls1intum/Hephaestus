---
"hephaestus": minor
---

The AI area of the API now says one thing one way. Every address in it is either `llm/…` (the models
and what they cost) or `agents/…` (the things that run them); the `ai-settings` container is gone, and
"BYO" no longer appears anywhere — a workspace's own connected provider is called exactly that, in the
API as well as on screen. The two monthly spend caps also became the same instrument: an instance
admin's cap on a workspace and that workspace's cap on its own provider now have the same address
shape and the same request body, differing only in who is allowed to set them.

**Operators:** several endpoints moved. There are no redirects or aliases — a script calling an old
address gets a 404, so update it before upgrading.

| Was | Now |
| --- | --- |
| `GET /admin/llm-usage` | `GET /admin/llm/usage` |
| `PUT /admin/workspaces/{workspaceId}/llm-budget` | `PUT /admin/workspaces/{workspaceSlug}/llm/budget` |
| `GET /workspaces/{slug}/llm-usage` | `GET /workspaces/{slug}/llm/usage` |
| `PUT /workspaces/{slug}/llm-usage/byo-budget` | `PUT /workspaces/{slug}/llm/budget` |
| `GET|PUT|DELETE /workspaces/{slug}/agent-bindings[/{purpose}]` | `GET|PUT|DELETE /workspaces/{slug}/agents[/{purpose}]` |
| `GET /workspaces/{slug}/agent-jobs…` | `GET /workspaces/{slug}/agents/jobs…` |
| `GET /workspaces/{slug}/ai-settings`, `PATCH …/ai-settings/practice-review` | `GET`/`PATCH /workspaces/{slug}/practices/review-settings` |

Three further changes to responses and bodies:

- The instance cap is now addressed by workspace **slug** rather than numeric id, like everything else
  in the API, and both cap endpoints take `{ "monthlyBudgetUsd": … }` instead of two differently named
  fields.
- `GET /admin/llm/usage` returns `{ month, fx, workspaces: [...] }` instead of a bare array. The
  exchange rate is reported once for the response rather than repeated on every row.
- Spend fields are named for the purse they belong to: `pricedTotalCostUsd` → `instanceTotalCostUsd`
  and `byoTotalCostUsd` → `ownProviderTotalCostUsd`, with `byoMonthlyBudgetUsd`, `byoBudgetVerdict`,
  `byoPaused` and `instanceFundedPaused` renamed to match.

The retired `/admin/llm` and `/admin/ai/*` browser URLs no longer redirect either; the pages have been
at `/admin/models` and `/admin/practices` since the previous release.
