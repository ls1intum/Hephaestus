---
"hephaestus": minor
---

Instance administrators can now register any OpenAI-compatible, Anthropic, or Azure OpenAI
provider — including self-hosted gateways such as vLLM — under Instance admin → AI models, set a
price per model, and share individual models with workspaces. Workspaces can instead connect their
own provider ("bring your own AI provider") to run practice review and the mentor on their own
account. API keys are never exposed to a workspace or a sandboxed agent — they stay server-side
behind the LLM proxy, which is now the only path a sandbox has to a provider.

Monthly budget totals now count only usage with a known price. When some usage has no price on
record, the total is now shown as "at least $X" instead of silently reporting a lower number —
unpriced usage is never folded in as if it cost nothing.

**Operators:** remove `HEPHAESTUS_WORKER_LLM_BASE_URL`, `HEPHAESTUS_WORKER_LLM_API_KEY`, and
`HEPHAESTUS_SANDBOX_LLM_PROXY_ENABLED` from your deployment (they are now ignored) and register
your provider(s) under Instance admin → AI models instead.
