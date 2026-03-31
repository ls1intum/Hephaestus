# Lessons Learned

## GitLab Live Testing (2026-03-29)

### Webhook Configuration
- Staging webhook-ingest secret is in `server/webhook-ingest/.env`
- Set `hephaestus.webhook.external-url` and `hephaestus.webhook.secret` in application-local.yml
- The app auto-registers the webhook on startup via `GitLabWebhookService.registerWebhook()`
- Webhook URL format: `{externalUrl}/gitlab`

### Practice Detection Pipeline
- Only `PullRequestCreated`, `PullRequestReady`, `PullRequestSynchronized`, and `ReviewSubmitted` trigger detection
- `PullRequestUpdated` (description change) does NOT trigger detection — no listener in `AgentJobEventListener`
- Practices need explicit trigger events in `trigger_events` JSONB column

### Git Repository Clones
- Stale clones in `/tmp/hephaestus-git-repos/` from previous GitHub sessions conflict with GitLab repos
- Must delete stale clones when switching workspace providers
- The `GitRepositoryManager` fetches from existing remote — doesn't re-clone from correct URL

### Agent Behavior
- **CRITICAL**: Source branches get deleted after MR merge. Agent `git diff origin/target..origin/source` fails silently → empty findings
- **FIX**: Pre-compute diff in `PullRequestReviewHandler.computeAndStoreDiff()` using merge commit parents, inject as `.context/diff.patch` and `.context/diff_stat.txt`
- Diff computation strategy: (1) try branch-based diff, (2) find merge commit parents, (3) merge-base fallback
- GitLab webhook doesn't provide diff stats — DB fields stay at 0
- OpenCode agent uses `rg` (ripgrep) for code search — requires `/home/agent/.local` tmpfs to have `exec` flag (Docker tmpfs defaults to `noexec`)
- Agent config `allow_internet=true` needed when app-server runs on host (not Docker) for LLM proxy access via `host.docker.internal`

### Azure OpenAI Proxy
- LLM proxy base URL must include `/openai` path prefix AND `?api-version=...` query param
- Format: `https://ase-us03.openai.azure.com/openai?api-version=2025-04-01-preview`
- Without this, proxy sends to `/responses` instead of `/openai/responses?api-version=...` → 404
- The `buildUpstreamUrl()` method correctly merges base URL query params with incoming request path

### Findings Quality (ge94dez + go57jur, gpt-5.4-mini)
- **Mandatory all-practice evaluation**: Changed prompt from "omit irrelevant" to require 13/13 findings with NOT_APPLICABLE
- With pre-computed diff: 13/13 findings per MR, mix of POSITIVE/NEGATIVE/NOT_APPLICABLE
- Without diff (deleted source branches): 13/13 findings, mostly NOT_APPLICABLE (correct behavior)
- PE-verified accuracy: 9 ACCURATE, 3 MOSTLY_ACCURATE, 1 WRONG (6/10 overall)
- **Known issue**: Agent doesn't distinguish pre-existing issues from newly introduced ones
- **Known issue**: MR description body is empty on webhook-created PRs (not fetched from API)
- Confidence: 0.89-0.98 range, well-calibrated. Lower for subjective findings (view-decomposition: 0.89)
- MAX_AGENT_STEPS increased from 15 → 30 (15 was too low for 13-practice evaluation)

### Diff Note Delivery
- GitLab `createDiffNote` requires `newLine` within a diff hunk — full-file lines get "Line code can't be blank" error
- **FIX**: Prompt now explicitly instructs to use diff-hunk lines only
- **FIX**: Server-side fallback converts failed diff notes to regular MR comments with `file:line` header
- Agent still tends to pick full-file lines — fallback ensures 0 data loss
- Summary comment always works; diff notes are best-effort with fallback

### Webhook Testing Protocol
- To trigger fresh analysis: delete PR from DB → send webhook with `state: "opened"`, `action: "open"`
- `AgentJobEventListener` line 109 skips MERGED PRs before detection gate — must send `state: "opened"`
- Webhook `author_id` must match DB `user.native_id` exactly — UK constraint on login
- Concurrency limit (max=2 per config) works correctly — excess jobs queue
- Intermittent LLM safety refusals from gpt-5.4-mini (1 in ~4 runs) — retry succeeds

### Delivery Gate
- `deliverToMerged` property added to allow posting comments on merged PRs
- Split CLOSED vs MERGED check in `PracticeReviewDeliveryGate`
- All-positive findings → delivery suppressed (silence = positive signal for first analysis)

## Agent Prompt Engineering (2026-03-30)

### Claude Code (claude-sonnet-4-6)
- **Grade: A+ (4.111)** — achieved in v4
- Single-pass prompt in `CLAUDE.md` works well — no subagent overhead
- Key improvements: mrNote template, guidanceMethod severity defaults, method-content alignment
- MAX_TURNS must be ≥25 for full 13-practice evaluation
- Cost: ~$0.94/MR (high but quality is excellent)

### OpenCode (gpt-5.4-mini via Azure)
- **Best grade: A (3.950)** — achieved in v5
- Multi-agent architecture: orchestrator + per-practice subagents via `task` tool (sequential)
- GPT-5.4-mini guidance depth is STOCHASTIC — sometimes 300-1000 chars with code, sometimes 68-252 chars without
- Few-shot examples are the ONLY reliable way to get code snippets in guidance
- Detection thoroughness hints (Pattern E, code-hygiene) work reliably for detection recall
- **Pareto frontier**: Can't reliably get both 100% detection AND A-grade guidance in same run
  - Best detection: 8/8 NEGATIVE (v6-v8), but guidance collapses to C
  - Best guidance: 7/8 NEGATIVE + A guidance (v5), but misses state-ownership-misuse
- **REFLECTION method**: GPT-5.4-mini uses REFLECTION even when told not to. Must add explicit guard.
- Cost: ~$0.09/MR (10x cheaper than Claude Code)
- Prompt token budget matters: adding ~200 tokens to subagent prompt can shift output from long→short

### Webhook Testing Protocol (updated)
- Webhook payload must include `object_attributes.id` (not just `iid`) — processor rejects without it
- `action: "update"` triggers detection; `action: "open"` also works
- Webhook-ingest path is `/gitlab` NOT `/api/webhook/gitlab`
- Secret token in `server/webhook-ingest/.env` as `WEBHOOK_SECRET`

## Infrastructure (2026-03-30)

### NATS Endpoint Mismatch
- App server defaults to `nats://staging.hephaestus.aet.cit.tum.de:4222` (production NATS)
- Webhook-ingest uses `nats://localhost:4222` (local NATS)
- MUST start app server with `NATS_SERVER=nats://localhost:4222` for local testing
- Both must connect to the same NATS server or messages won't be delivered

### PR Upsert COALESCE Bug
- `PullRequestRepository.upsertCore()` had `html_url = EXCLUDED.html_url` (direct overwrite)
- GitLab webhooks provide null `webUrl`, overwriting synced data from GraphQL
- FIX: COALESCE for `html_url`, `head_ref_name`, `base_ref_name`, `head_ref_oid`, `base_ref_oid`
- Affects any webhook that processes an already-synced MR
- Also: webhook `last_commit.id` may be short SHA (12 chars), GraphQL provides full 40 chars

### Claude Code OAuth Hang
- Claude Code CLI v2.1.76 with `CLAUDE_CODE_OAUTH_TOKEN` sometimes hangs indefinitely
- No API calls made, no stderr output, multiple `claude` processes in container
- OAuth token is valid (env var set correctly), network is reachable
- Multi-config redundancy prevents this from blocking delivery — OpenCode completes in ~5 min
- Pattern: 100% of Claude Code OAuth jobs have hung in testing (3/3)

### Multi-Config Redundancy (VERIFIED)
- `AgentJobService.submit()` creates jobs for ALL enabled configs
- Idempotency keys are config-scoped: `{baseKey}:config:{configId}`
- Delivery dedup uses stable external identifiers (repo_full_name + pr_number)
- First to complete posts/creates; subsequent completions edit existing comment
- Live test: OpenCode completed in 5m53s while Claude Code hung → review still delivered

### Delivery Dedup (VERIFIED + BUG FIXED)
- `findPreviousDeliveryCommentId()` uses `metadata->>'repository_full_name'` + `CAST(metadata->>'pr_number' AS integer)`
- Survives PR deletion/recreation (different internal pull_request_id values)
- MR#22: New comment created (first delivery)
- MR#23: Existing comment edited (prior deliveries from previous sessions)
- MR#22 + MR#23 = separate comments (dedup is per-MR, not per-repo)
- **BUG FIXED**: If a previously-posted note is deleted externally, `updateGitLabNote()`/`updateGitHubComment()` now detect null return ID and fall back to creating a new note instead of silently succeeding with stale ID
- Symptom: job marked DELIVERED but no note visible on MR
- Root cause: GitLab `updateNote` mutation returns success + null note for deleted notes
- Fix in: `PullRequestCommentPoster.java` — `updateGitLabNote()` returns null on deleted note, callers fall back to `createGitLabNote()`

### Webhook Testing (description field)
- Synthetic webhook payloads MUST include `object_attributes.description` field
- Without it, `GitLabMergeRequestProcessor` stores `body=null` in DB → agent sees empty description
- This causes false `mr-description-quality` MAJOR findings
- Real GitLab webhooks always include `description`
- Use `/tmp/fire-webhooks-v2.py` which fetches and includes descriptions
- Updated test data in `/tmp/test-mrs-v2.json`
