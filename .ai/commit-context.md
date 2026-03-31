# Commit Context: agent-single-pass-diffsum

## What changed
- **Server-side diff decomposition**: `PullRequestReviewHandler.computeAndStoreDiffSummary()` splits annotated diff into per-file chunks with markdown index table + quick-scan tokens
- **Single-pass orchestrator**: Replaced multi-phase subagent spawning with 20-step single-pass prompt. Agent reads diff_summary.md once, evaluates all practices, outputs JSON.
- **Removed rule-based security scan**: The regex-based `computeAndStoreSecurityScan()` was replaced by `computeAndStoreDiffSummary()`. AI finds secrets natively from structured diff.
- **TMPDIR fix**: OpenCodeAgentAdapter sets TMPDIR=/home/agent/.local/tmp to avoid noexec /tmp breaking Node native addons.
- **Stale repo fix**: go72hog repo ID=8 had stale GitHub clone; deleted to force GitLab re-clone.

## Test results: go72hog !12 (31 files, 1644 lines)

| Version | LLM calls | Runtime | Cost | Findings | Secrets found |
|---------|-----------|---------|------|----------|---------------|
| v0: No diff (stale repo) | 5 | 38s | $0.05 | 2 (both POS) | 0 |
| v1: Regex scan + subagents | 14 | 5:34 | $0.37 | 11 (10 NEG, 1 POS) | 3 snippets |
| v2: Single-pass + diff_summary | 4 | 2:58 | $0.31 | 5 (all NEG) | 6 snippets |

## Open questions
1. v2 has fewer findings (5 vs 11) — is this better precision or lost recall?
2. Should the agent produce findings for ALL practices (even positive) or only flagged ones?
3. Two-step architecture: should analysis and delivery be separate phases?
4. Positive-finding bias: how to ensure the agent doesn't rubber-stamp student code?

## Infrastructure state
- App server: PID 2930595, port 38080, log /tmp/app-server41.log
- Webhook-ingest: PID 912062, port 4201
- NATS: staging.hephaestus.aet.cit.tum.de:4222
- DB: docker exec application-server-postgres-1 psql -U root -d hephaestus
