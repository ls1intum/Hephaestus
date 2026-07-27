---
"hephaestus": minor
---

Instance administrators can now register OpenAI and other OpenAI-compatible endpoints — including
self-hosted gateways such as vLLM — under Instance admin → AI models, set a
price per model, and share individual models with workspaces. Workspaces can instead connect their
own provider ("bring your own AI provider") to run practice review and the mentor on their own
account. API keys are never exposed to a workspace or a sandboxed agent — they stay server-side
behind the LLM proxy, which is now the only path a sandbox has to a provider.
Interactive mentor conversations reuse a healthy sandbox for faster follow-up turns and replace it
when its binding changes or its lease expires.

Monthly budget totals now count only verifiable priced usage. A started attempt with no trustworthy
usage counters is never folded in as if it cost nothing: it is counted separately and the total says
how many runs it is missing ("2 runs aren't counted in these totals"), so an understated figure
declares itself instead of reading as complete.

**Operators:** remove `HEPHAESTUS_WORKER_LLM_BASE_URL`, `HEPHAESTUS_WORKER_LLM_API_KEY`,
`HEPHAESTUS_SANDBOX_LLM_PROXY_ENABLED`, and every `AGENT_DEFAULT_CONFIG_*` variable from your
deployment (they are now ignored), then register your OpenAI-compatible endpoint(s) under Instance
admin → AI models.
