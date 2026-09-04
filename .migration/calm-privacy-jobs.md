#### 🔴 LLM usage accounting older than the retention window is deleted after upgrade

**Affected**: every deployment that has recorded LLM usage for longer than the window — 400 days
unless you set your own — and any operator whose commercial or tax retention obligations cover that
accounting data.

**Before**: rows in the LLM usage ledger — per-run token counts and cost, attributed to a workspace —
were kept indefinitely.

**After**: a daily sweep deletes usage older than the configured window, default 400 days. The
deletion is irreversible. Each pass deletes in batches for at most five minutes, so the first sweep
after upgrade begins clearing the historical backlog and later sweeps finish it; a pass that stops
with rows still expired reports the `incomplete` privacy-job outcome.

**Migration**: if your accounting obligations require a longer window, set
`HEPHAESTUS_LLM_USAGE_RETENTION` (an ISO-8601 duration, for example `P3650D`) before deploying this
release. No action is needed to keep the default.
