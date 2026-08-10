# Migration Guide

This document helps you upgrade between versions of Hephaestus. For what a version number promises
(public contract, upgrade guarantee, support statement), see the
[Compatibility Policy](https://ls1intum.github.io/Hephaestus/admin/compatibility-policy).

> ⚠️ **Pre-1.0 Notice**: We are in active development. Minor versions (0.x.0) may contain breaking changes. Always test in staging before production.

## Quick Reference

| Symbol | Meaning |
|--------|---------|
| 🔴 | **Breaking**: Action required before upgrade |
| 🟡 | **Deprecated**: Works now; removed in a later release — the release notes say which |
| 🟢 | **New**: No action needed |

## Check Your Version

Run these from the directory you deploy from — for a self-hosted install that is
`/opt/hephaestus/docker/self-host`, the same directory as your `.env`. Running them from the
repository root points at a different Compose project and reports nothing.

```bash
# Deployed version (the image tag your containers run; APP_VERSION is derived from it)
docker compose images application-server

# Latest release
curl -fsSL https://api.github.com/repos/ls1intum/Hephaestus/releases/latest \
  | grep -m1 '"tag_name"'
```

Signed in, you can also read the running version straight off the app: production shows it in the
header, linked to its release notes.

---

## Pre-1.0 Development (Current)

During pre-1.0, we follow [Semantic Versioning 0.x conventions](https://semver.org/#spec-item-4):

> Major version zero (0.y.z) is for initial development. Anything MAY change at any time.

### What This Means

| Version Bump | May Contain |
|--------------|-------------|
| `0.x.0` → `0.y.0` | Breaking changes |
| `0.x.y` → `0.x.z` | Bug fixes, minor features |

### Upgrade Checklist

Before upgrading to any new `0.x.0` version:

1. ✅ Read the [release notes](https://github.com/ls1intum/Hephaestus/releases)
2. ✅ Check this migration guide for breaking changes
3. ✅ Verify in staging first (auto-deployed on every release)
4. ✅ Approve production deployment after staging verification

---

## Version History

Entries exist only for releases that need operator action. Everything else is in the
[release notes](https://github.com/ls1intum/Hephaestus/releases).

### Next release

#### 🔴 `GIT_STORAGE_PATH` is now `HEPHAESTUS_FABRIC_ROOT`

**Affected**: deployments that set `GIT_STORAGE_PATH` to anything other than `/data/git-repos`. Check
with `grep GIT_STORAGE_PATH` over your deployment configuration before you upgrade. Deployments that
use the shipped Compose files unchanged are **not** affected: those files already pinned this path to
`/data/git-repos` and now pass the new name for the same directory, mounted from the same volume.

**Before**: `GIT_STORAGE_PATH` (`hephaestus.git.storage-path`) named the directory holding repository
working copies, and the rest of the on-disk cache — content-addressed evidence blobs and per-job
manifests — fell back to it whenever `HEPHAESTUS_FABRIC_ROOT` was unset.

**After**: `HEPHAESTUS_FABRIC_ROOT` is the only name for that directory. `GIT_STORAGE_PATH` is read
nowhere and has no alias.

**Nothing warns you.** Everything under this root is a rebuildable cache, so an instance that keeps
only the old variable starts, passes its health check and reviews normally — it simply writes to
`/data/git-repos` instead of the path you chose. If that path is not a mounted volume on your
deployment, it is the container's own writable layer: it grows with every clone, is discarded on
every restart, and presents as repeated full re-clones and a container disk filling up. The tree at
your old path is left where it is, no longer read and no longer swept.

**Migration**: before starting the new version, set `HEPHAESTUS_FABRIC_ROOT` to the value
`GIT_STORAGE_PATH` had, then remove `GIT_STORAGE_PATH`. The directory layout beneath the root is
unchanged, so the existing contents are picked up as they are and nothing has to be re-fetched.

#### 🔴 The evidence-cache retention window must be at least one day

**Affected**: deployments that set `HEPHAESTUS_FABRIC_GC_RETENTION_DAYS`
(`hephaestus.fabric.gc-retention-days`). The shipped default is `30` and is valid; if you have not
set this, there is nothing to do.

This is the number of days a review's cached evidence and job manifest are kept before the daily
sweep removes them. `0` was previously accepted, and did the opposite of what it looks like: instead
of switching the sweep off it made every cached job directory eligible for deletion on the next run.
A value below `1` is now rejected.

**Migration**: if you set it to `0` or a negative number, set a real window before upgrading.
Otherwise the server role does not start and reports:

```
hephaestus.fabric.gc-retention-days must be positive
```

No value switches the sweep off; set a long window instead.

#### 🔴 Practice-review API uses one vocabulary

**Affected**: API clients that configure practices, read findings, or manage AI bindings.

Update clients in the same deployment as the server and webapp. The old names have no aliases:

| Was | Now |
| --- | --- |
| AI purpose `PRACTICE_DETECTION` | `PRACTICE_REVIEW` |
| `evidenceRequirements` | `automatedReviewPolicy` |
| `evidenceSupport` | `evidenceSufficiency` |
| practice `active` and `/active` | `reviewTier` and `/review-tier` (see below) |
| practice-area `active` | `visibleInPracticeDashboards` |
| finding `claimStatus` | `claimCurrentness` |

Database values and columns migrate automatically. This change removes ambiguous uses of “active,” “support,” and
“detection”; it does not change historical finding results.

#### 🔴 A practice has a loudness tier, not an on/off switch

**Affected**: API clients that turn practices on or off.

`PATCH /workspaces/{slug}/practices/{practiceSlug}/used-in-new-reviews` with
`{"usedInNewReviews": true|false}` is now
`PATCH /workspaces/{slug}/practices/{practiceSlug}/review-tier` with `{"reviewTier": "..."}`. The
practice payload carries `reviewTier` instead of `usedInNewReviews`, and the catalogue list filter is
`?reviewTier=<TIER>` instead of `?usedInNewReviews=<bool>`. There are no aliases.

The four tiers are `OFF`, `MEASURE`, `COACH` and `ENGAGE`. Existing data maps exactly and needs no
decision from you: a practice that was used in new reviews becomes `ENGAGE`, one that was not becomes
`OFF`, and the migration runs automatically. `MEASURE` and `COACH` are new ground — the review still
runs and every observation is still recorded, and only the delivery is held back.

#### 🟢 A workspace can restrict review to some branches and repositories

**Affected**: nobody, unless you want it. Workspaces that do not configure a scope are unchanged.

The practice-review settings resource carries a `reviewScope` of two exact-match lists,
`targetBranches` and `repositories`. An empty list means no restriction on that axis. Exact names
only — there are no glob patterns, and there is no path scope, because the changed files of a pull
request are not known at the point where the decision to review is made.

#### 🔴 The "Skip drafts" workspace setting is gone

**Affected**: deployments that set `PRACTICE_REVIEW_SKIP_DRAFTS`, and workspaces that had "Skip
drafts" switched on.

Remove `PRACTICE_REVIEW_SKIP_DRAFTS` from your environment; it is no longer read, and the workspace
toggle no longer appears. Whether a draft occasions a review is now stated by each practice's own
occasions rather than by a switch that silenced all of them at once. One shipped practice ("Ready and
traceable handoff") asks for drafts, so a workspace that previously skipped them will start seeing
that practice's feedback on draft pull requests; no other practice reviews a draft. The stored
per-workspace override is retained unread for one release and removed after that.

#### 🔴 Outline connections require approved origins

**Affected**: deployments that enable the Outline integration.

Set `HEPHAESTUS_INTEGRATION_OUTLINE_ALLOWED_ORIGINS` to the comma-separated HTTPS origins whose operator role,
region, transfer basis, retention, and AVV status have been reviewed. An empty list blocks Outline connections,
sync, webhook collection, evidence projection, and identity linking. Set the same value on server, worker, and
webhook roles and restart all three. Disconnect connections for removed origins; remove all grants for
`outline.documents` until residual mirrored data has been erased.

#### 🔴 Untouched instances start with Silent Mode engaged

**Affected**: deployments where the instance Silent Mode setting has never been explicitly changed.

The upgrade engages the instance-wide outbound brake before any new GitHub, GitLab, or Slack delivery
can leave the application. Practice review, persistence, synchronization, webhooks, OAuth, and administration
continue normally; suppressed feedback is recorded and is never replayed.

On production, verify each workspace's practice delivery settings and provider targets, then open
**Instance admin → Settings** and release Silent Mode. Leave it engaged on staging clones and during
disaster-recovery drills. Instances whose operator had already changed the setting keep that explicit
choice.

**API clients:** The Silent Mode update operation is now
`PATCH /admin/settings/silent-mode`. Replace calls to the removed `PUT` operation before upgrading.

#### 🔴 Practice-feedback delivery field renamed

**Affected**: API clients that read or write user settings, or consume account-export JSON.

The personal practice-feedback field was renamed from `aiReviewEnabled` to
`practiceFeedbackDeliveryEnabled` so the API matches its actual scope: issue, pull-request, and
merge-request comments plus related Slack reminders. There is no alias for the old field.

Update request and response handling for `/user/settings`, and update the `preferences` object in
account exports, before deploying the new release. No database or environment change is required.

#### 🔴 Workspace purge moved to the owner-only deletion endpoint

**Affected**: automation that sets a workspace status to `PURGED`.

`PATCH /workspaces/{slug}/status` now accepts only `ACTIVE` and `SUSPENDED`. Replace a purge request
with `DELETE /workspaces/{slug}` and authenticate as that workspace's owner. The old request returns
`409 Workspace lifecycle violation`.

#### 🔴 LLM provider configuration moved from env vars to the admin console

**Affected**: any deployment setting `HEPHAESTUS_WORKER_LLM_BASE_URL`, `HEPHAESTUS_WORKER_LLM_API_KEY`, `HEPHAESTUS_SANDBOX_LLM_PROXY_ENABLED`, or an `AGENT_DEFAULT_CONFIG_*` variable.

**Before**: the worker pod's LLM upstream/key were passed through env vars (`HEPHAESTUS_WORKER_LLM_BASE_URL` / `HEPHAESTUS_WORKER_LLM_API_KEY`), and the LLM proxy could be toggled per pod with `HEPHAESTUS_SANDBOX_LLM_PROXY_ENABLED` (`hephaestus.sandbox.llm-proxy.enabled`).

**After**: OpenAI and other OpenAI-compatible endpoints are registered at runtime under **Instance admin → AI models**, with an explicit Chat Completions or Responses API contract, per-model pricing, and optional sharing with workspaces. Workspaces can also connect their own compatible endpoint. The LLM proxy — the only path a sandbox has to a provider key — now runs automatically wherever the worker/sandbox capability is on (`hephaestus.runtime.worker.enabled`, default true), which is both the worker pod and the application-server replica that serves interactive mentor sandboxes. It has no standalone enable flag. The three env vars above are no longer read.

**Migration** — step 1 is a configuration edit, so make it in the same pass that sets the new
`IMAGE_TAG`. Steps 2–6 run against the upgraded instance, once it has booted and applied its schema
migration: that migration is what writes the deploy-log lines steps 4–6 quote, so start the new
version first and keep its startup log to hand.

1. Remove `HEPHAESTUS_WORKER_LLM_BASE_URL`, `HEPHAESTUS_WORKER_LLM_API_KEY`,
   `HEPHAESTUS_SANDBOX_LLM_PROXY_ENABLED`, and every `AGENT_DEFAULT_CONFIG_*` variable from your
   deployment. They are no longer read. Remove them by grepping your deployment configuration rather
   than relying on startup diagnostics.
2. Register your OpenAI-compatible endpoint(s) under Instance admin → AI models (or have a workspace admin connect their own under the workspace's Administration → AI models). Each page tests the connection before you save it, so you learn the endpoint answers without waiting for a review to fail.
3. **Review and re-enable each workspace's carried-over AI configuration.** The upgrade copies every
   agent configuration that was in use — endpoint, model name, encrypted API key, timeout,
   concurrent-run limit and internet setting — into that workspace's AI models page, named after the
   old configuration. "In use" means one a workspace explicitly pointed at **or** any configuration
   that was simply switched on: an unset pointer never meant unused, it meant *fall back*, and the
   mentor fell back to the workspace's oldest enabled configuration while practice review ran on
   every enabled one. Configurations created from `AGENT_DEFAULT_CONFIG_*` are exactly that shape.
   No key you were using has to be re-issued. Everything arrives **disabled**, so practice review
   and the mentor stay stopped until an administrator opens the page and switches them on. That is
   deliberate: in the default PROXY credential mode the endpoint a configuration actually called came
   from an instance-wide environment variable rather than from the configuration row, so re-enabling
   automatically could silently re-point a workspace's traffic — and its key — at a different host.
   Until someone does, that workspace's practice review and mentor are simply idle: nothing errors,
   so there is nothing to notice. A workspace is done when its AI models page shows an enabled
   connection and a model bound to each purpose it uses.
4. **Fix what the upgrade could not determine.** These cases need a value typed in before they will
   work, and the deploy log names the affected workspaces (`AI configuration carried over with a
   placeholder endpoint, model id or non-OpenAI protocol in these workspaces: …`):
   - A configuration whose provider was **Azure OpenAI** with no base URL recorded: no
     instance-independent endpoint exists for it, so the carried-over connection holds the
     placeholder `https://endpoint-not-migrated.invalid/v1`. Replace it with your Azure resource URL.
   - A configuration whose provider was **Anthropic**: the new catalog speaks the OpenAI Chat
     Completions and Responses contracts only. Its key is preserved, but you need an
     OpenAI-compatible endpoint (or a gateway in front of Anthropic) for it to run.
   - A configuration that **never named a model**: the model carries the placeholder id
     `model-not-migrated`, which keeps the configuration's timeout, concurrency and internet limits
     attached to a real binding. Replace it with the model id you want. Such a configuration could
     not run before the upgrade either.
5. **Check any workspace where review ran on several configurations at once.** A workspace with no
   explicit practice-review pointer ran reviews on *every* enabled configuration. The new model binds
   one model per purpose, so practice review is bound to the oldest of them and the deploy log names
   the workspace. That log line is written by the migration itself and still uses this release's old
   word for the purpose: `practice detection ran on SEVERAL configurations at once in these
   workspaces: …`.
   Nothing is lost — the other configurations are all there as connections and models — but pick the
   one you want, or delete the rest.
6. **Revoke the keys of configurations that are dropped.** A configuration that was both switched off
   *and* unreferenced is not carried over: nothing could reach it, so it configured nothing. It is
   dropped with the old table, and the deploy log lists each one as `workspace/name` so you can
   revoke its API key at the provider if you want to.

#### 🔴 Agent job queue moved from NATS to PostgreSQL

**Affected**: any deployment setting `AGENT_NATS_ENABLED`, `HEPHAESTUS_AGENT_NATS_SERVER`, `AGENT_NATS_MAX_ACK_PENDING`, or `AGENT_NATS_FETCH_BATCH_SIZE`.

**Before**: the practice-review agent job queue was delivered over a NATS JetStream stream (`AGENT`); a worker pulled a job id off the stream, then loaded the job from PostgreSQL to execute it. Interactive mentor turns were and remain request-affine; they do not use `agent_job`.

**After**: workers poll `agent_job` directly and claim a batch with `FOR UPDATE SKIP LOCKED` — PostgreSQL, already the source of truth for job state, is now also the delivery mechanism. `AGENT_NATS_ENABLED` is replaced by `AGENT_ENABLED` (default `false`). New optional tuning: `AGENT_POLL_INTERVAL` (default `1s`), `AGENT_CLAIM_BATCH_SIZE` (default `5`), `AGENT_MAX_RETRIES` (default `5`), `AGENT_PAYLOAD_RETENTION` (default `P14D`), and `AGENT_ROW_RETENTION` (default `P90D`). NATS itself is unaffected everywhere else — it remains required for webhook ingest and SCM/Slack sync. See [ADR 0025](https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0025-agent-job-queue-on-postgresql.md).

**Migration**:

1. Set `AGENT_ENABLED=true` (replacing `AGENT_NATS_ENABLED=true`) on **every** role that needs to submit, execute, or recover jobs — not just the role that claims and runs them. In a split-pod deployment that means **both** `application-server` (submits jobs from PR/issue events and runs the orphan-recovery sweep — both gate on this same flag, independent of the worker role) **and** `application-worker` (claims and executes them, additionally gated on the worker role); `docker/compose.app.yaml` already sets the same `AGENT_ENABLED` value on both services. In the monolith, set it once. No profile turns it on for you: a pod you do not set it on claims nothing — including a `worker`-profile pod you start outside the shipped Compose files, which in earlier releases turned itself on.
2. Confirm the flag actually took, on each side. Once the upgraded server is up, the `agent.queue.depth`, `agent.queue.oldest_age_seconds` and `agent.queue.running` metrics exist; if `AGENT_ENABLED` never reached that pod they are absent altogether rather than reading zero — which is the difference between "the queue is idle" and "the queue was never switched on". For the worker side, open a pull request and watch `agent.queue.oldest_age_seconds`: it should rise and fall. An age that only ever climbs means the server is submitting and no worker is claiming.
3. Remove `AGENT_NATS_ENABLED`, `AGENT_NATS_MAX_ACK_PENDING`, and `AGENT_NATS_FETCH_BATCH_SIZE` from your deployment. They are no longer read. Leave `NATS_SERVER` alone: it is still live for webhook and sync ingest.
4. Optional cleanup, after the upgraded instance has run long enough that you are not rolling back: the `AGENT` JetStream stream is no longer read from or written to. Delete it with `nats stream rm AGENT` if you want to reclaim its storage; leaving it in place is harmless.
5. Do not remove NATS itself or `NATS_ENABLED` — webhook ingest and SCM/Slack sync still require it.

#### 🔴 AI endpoints renamed to one vocabulary

**Affected**: any script or integration calling the workspace AI, agent-job, or LLM spend endpoints. No
action is needed for the web UI, which ships updated in the same release.

**Before**: the AI area of the API was `agent-configs` (named execution profiles), `ai-settings` (an
aggregate container that also held the practice-review policy and the two config pointers), and
`agent-jobs`.

**After**: every address is either `llm/…` (models and what they cost) or `agents/…` (the things that
run them). `agent-configs` and `ai-settings` are gone: a workspace's AI setup is a per-purpose binding
under `agents/…`, and the practice-review policy moved next to the practice catalogue. Both monthly
spend caps are `…/llm/budget` with the body `{ "monthlyBudgetUsd": … }`, addressing a workspace by
slug.

**There are no redirects or aliases.** An old address returns 404.

| Was | Now |
| --- | --- |
| `GET /workspaces/{slug}/agent-jobs[/{jobId}[/cancel\|/delivery/retry]]` | `GET /workspaces/{slug}/agents/jobs[/…]` |
| `GET /workspaces/{slug}/ai-settings` | `GET /workspaces/{slug}/practices/review-settings` |
| `PATCH /workspaces/{slug}/ai-settings/practice-review` | `PATCH /workspaces/{slug}/practices/review-settings` |
| `GET`/`POST` `/workspaces/{slug}/agent-configs`, `GET`/`PATCH`/`DELETE` `…/agent-configs/{configId}` | removed — see the AI-configuration section above |
| `PUT /workspaces/{slug}/ai-settings/practice-config`, `PUT …/ai-settings/mentor-config` | removed — see the AI-configuration section above |

Everything else in the AI area is **new** in this release, not a renamed address, so no existing call
site points at it:

| New | What it is |
| --- | --- |
| `GET /workspaces/{slug}/agents` | list a workspace's per-purpose bindings |
| `PUT`/`DELETE /workspaces/{slug}/agents/{purpose}` | set or clear one binding (`{purpose}` has no `GET`) |
| `GET /admin/llm/usage` | instance-wide LLM spend, `{ month, fx, workspaces: [...] }` |
| `PUT /admin/workspaces/{slug}/llm/budget` | the instance admin's monthly cap on a workspace |
| `GET /workspaces/{slug}/llm/usage` | that workspace's own spend view |
| `PUT /workspaces/{slug}/llm/budget` | the workspace's cap on its own connected provider |
| `GET /workspaces/{slug}/llm/settings` | the instance LLM policy as it applies to this workspace |
| `GET`/`POST`/`PATCH`/`DELETE /admin/llm/connections`, `…/models` | the instance model catalogue |

**Migration**:

1. Update every call site in the first table to its new address. `server/openapi.yaml` is the
   authoritative list.
2. If you read `GET /ai-settings` for `practicesEnabled` / `mentorEnabled`, take them from the workspace
   itself (`GET /workspaces/{slug}`); the review-settings response returns the review policy only.
3. If you called `/agent-configs` or the two `ai-settings` config pointers, follow the AI-configuration
   section above: a workspace now has exactly one binding per purpose, edited through
   `PUT /workspaces/{slug}/agents/{purpose}`.

No database action is required. The config-audit trail keeps its historical entity-type values as
written — the table is append-only by database trigger, so past rows are never rewritten.

#### 🔴 The default GitLab server follows your GitLab login instead of the maintainers' instance

**Affected**: deployments that use the shipped Compose files, talk to a GitLab other than
`gitlab.com`, and have never set `GITLAB_DEFAULT_SERVER_URL` themselves. Check with
`grep GITLAB_ .env` before you upgrade.

**Before**: `docker/compose.app.yaml` passed `GITLAB_DEFAULT_SERVER_URL:-https://gitlab.lrz.de` — the
maintainers' own university instance. Because Compose always supplied a concrete value, the fallback
the shipped configuration documents never ran, and an operator who had configured only their GitLab
login silently got an instance they had never named.

**After**: the same line is `${GITLAB_DEFAULT_SERVER_URL:-${GITLAB_OAUTH_BASE_URL:-https://gitlab.com}}`.
Unset, it follows your GitLab login URL; with neither set it is `https://gitlab.com`.

This setting names the GitLab that workspace creation and repository, group and member sync talk to.
Sync resolves its provider by that URL, so changing it does not re-point existing data — it stops
matching the rows written under the old URL and begins writing new ones stamped with the new one.

**Migration**:

1. If `GITLAB_DEFAULT_SERVER_URL` is set in your `.env`, nothing changes for you.
2. If it is unset but `GITLAB_OAUTH_BASE_URL` already names your GitLab, that is now the value —
   which is the intended behaviour, and for most self-hosted installs is the correct one. Confirm it
   is the instance you sync from.
3. If both are unset and you are not on `gitlab.com`, set `GITLAB_DEFAULT_SERVER_URL` to your
   instance **before** starting the new version.

A deployment that runs the application without the shipped Compose files was already defaulting to
`https://gitlab.com` and is unaffected.

#### 🔴 An agent heartbeat slower than 30 seconds now refuses to start

**Affected**: deployments that override `hephaestus.agent.heartbeat-interval`. There is no environment
variable for it, so that means an `application-local.yaml` or another `spring.config.import` source,
or the relaxed-binding form `HEPHAESTUS_AGENT_HEARTBEATINTERVAL`. The shipped default is `25s` and is
valid; if you have not set this, there is nothing to do.

A worker renews a 60-second lease on the jobs it is running. A heartbeat slower than half that lease
let a worker be declared dead while it was still working: its in-flight reviews were requeued onto a
sibling and the same work ran twice, at double the model spend. The value is now rejected instead of
accepted.

**Migration**: if you set it above `30s`, lower it before upgrading. Otherwise the application does
not start — on every role, not only the worker, because the value is rejected when configuration is
bound — and reports:

```
hephaestus.agent.heartbeat-interval must be <= PT30S (half the PT1M worker lease), or every worker
is orphaned while its jobs are still running; got: ...
```

#### 🔴 The containers now have their own memory limits

**Affected**: hosts with less RAM than the limits add up to — in particular any host sized from
guidance that named 4 GB as the floor.

`application-server` (`APPLICATION_SERVER_MEM_LIMIT`, default `5g`), `application-worker`
(`APPLICATION_WORKER_MEM_LIMIT`, default `3g`) and `webhook-server` (`WEBHOOK_SERVER_MEM_LIMIT`,
default `2g`) each carry a container memory limit, and each JVM now sizes its heap from its own limit
rather than from the whole host. On the single-host install the worker runs inside the application
server, so the limits that apply there are `5g` and `2g`.

Nothing compares these against the host. Docker starts the stack either way and the kernel kills
whichever container exceeds its own limit; all three restart automatically, so an undersized host
presents as a restart loop rather than as a refusal to start. Review sandboxes are separate
containers and their memory sits outside these limits (`SANDBOX_MEMORY_BYTES`, default 4 GiB per
concurrent sandbox).

**Migration**: on a host below 8 GB RAM, set lower values in `.env` before the first start of the new
version. The [install guide](https://ls1intum.github.io/Hephaestus/admin/install) states the floor
and how the limits relate to it.

#### 🔴 A workspace's per-run AI timeout is capped at one hour

**Affected**: workspaces whose per-run timeout under Administration → AI models is above 3600 seconds.

The ceiling is enforced when the value is saved, and existing stored values are left as they are.
That combination is what needs your attention: such a workspace cannot save **any** change on its AI
models page until the timeout is brought to 3600 or below, because the whole form is rejected. Its
mentor turns are clamped to the ceiling, but its practice-review runs still run to the stored value,
so the setting and the behaviour disagree until you change it.

**Migration**: open each workspace's Administration → AI models page and lower any timeout above one
hour. There is no automatic clamp of stored values.

#### 🔴 AI proxy metrics are labelled by API contract, not by provider

**Affected**: deployments with dashboards or alerts on the LLM proxy metrics, and anything searching
logs by the `proxy.provider` field.

`llm.proxy.duration` and `llm.proxy.errors` keep their names. Their label changes from `provider`
(values `OPENAI`, `ANTHROPIC`, `AZURE_OPENAI`) to `apiProtocol` (values `openai-completions`,
`openai-responses`), because a provider name stopped identifying anything once any OpenAI-compatible
endpoint can be registered. The MDC log fields change from `proxy.jobId` and `proxy.provider` to
`proxy.principal` and `proxy.apiProtocol`.

A query filtering on the old label does not error — it matches no series and renders empty. An alert
built on one stops firing, which is indistinguishable from the condition being healthy.

**Migration**: update those queries before upgrading. New counters you may want to add while you are
there: `llm.proxy.budget.blocked` (calls refused by a spending cap), `llm.proxy.unbillable.refused`,
`llm.proxy.usage.unparseable` and `llm.proxy.stream.usage.unsupported` (responses whose token counts
could not be read, which is what makes a monthly total understated).

#### 🔴 Reviewed work is renamed in place, and the rename is one way

**Affected**: every deployment, and any API client that reads or writes the kind of work a practice
applies to or that a review or observation records.

A practice, a review run and a recorded observation all now identify what was reviewed as
`scm.pull_request`, `scm.issue` or `chat.conversation_thread`, replacing two internal vocabularies
that had drifted apart. The upgrade rewrites the stored values.

**Rolling the release back requires rolling this database change back with it.** Redeploying the
previous image on its own leaves a database the old version cannot read.

Two effects are worth expecting while the first reviews run after the upgrade, neither of which needs
action:

- A piece of feedback already posted on an open pull request or thread may be posted once more rather
  than updated in place. What ties a re-review to an earlier one is derived from the old name, so the
  first review after the upgrade does not recognise its own earlier comment.
- Practice review rules are re-fingerprinted on the first start after the upgrade, so a practice can
  briefly show as differing from its Hephaestus default until that finishes. If a workspace still
  shows as locally edited long afterwards, check the startup log: the pass records a failure per
  workspace and moves on rather than stopping, so a workspace it could not complete stays that way
  until the next start.

### v0.69.0

#### 🔴 Agent image pin moved from `docker/agent-image-pin.env` to a signed release asset

**Version**: v0.69.0
**Affected**: any deployment relying on `docker/agent-image-pin.env` or `docker/agent-image-pin.local.env`.

**Before**: `docker/agent-image-pin.env` was committed to `main` on every release by an auto-commit step in `release.yml`. `compose.app.yaml` loaded it via `env_file:` from the source tree.

**After**: each GitHub Release publishes a signed `release-vX.Y.Z.yaml` (cosign keyless OIDC bundle, multi-subject in-toto attestation). The `release-pin-fetcher` init service in `compose.app.yaml` fetches + verifies it at deploy time onto a shared volume; `application-server` imports it via `spring.config.import: optional:file:/pin/release-pin.yaml` (declared in `application-prod.yml`).

**Migration**:

1. Deploy host must reach `github.com`, `fulcio.sigstore.dev`, `rekor.sigstore.dev`, and `tuf-repo-cdn.sigstore.dev` over HTTPS.
2. Remove `docker/agent-image-pin.local.env`. Use `application-local.yaml` or a shell env var instead — see [Agent image digests](https://github.com/ls1intum/Hephaestus/blob/main/docs/admin/agent-image-digests.md).
3. Confirm `HEPHAESTUS_AGENT_IMAGE_REFERENCE` is not pre-set in your deploy substrate; an unintended value shadows the verified pin.
4. Rolling back to a pre-v0.69.0 release: set `HEPHAESTUS_RELEASE_PIN_SKIP=true` plus an explicit `HEPHAESTUS_AGENT_IMAGE_REFERENCE=...@sha256:<digest>` env override on the init service.

#### 🔴 Agent runtime: image config consolidated under `hephaestus.agent.image.*`

**Version**: v0.69.0
**Affected**: any deployment that pinned the agent-pi image via `HEPHAESTUS_AGENT_PI_IMAGE`, `HEPHAESTUS_MENTOR_AGENT_IMAGE`, or the matching pull-policy env vars.

**Before**:

```bash
HEPHAESTUS_AGENT_PI_IMAGE=ghcr.io/ls1intum/hephaestus/agent-pi:latest
HEPHAESTUS_MENTOR_AGENT_IMAGE=ghcr.io/ls1intum/hephaestus/agent-pi:latest
HEPHAESTUS_AGENT_PI_PULL_POLICY=IF_NOT_PRESENT
HEPHAESTUS_MENTOR_AGENT_PULL_POLICY=IF_NOT_PRESENT
```

**After**: production binds `HEPHAESTUS_AGENT_IMAGE_REFERENCE` from the signed release asset (previous entry). `pull-policy` and `require-digest` are now Spring properties set in `application-prod.yml`, not env vars.

**Migration**:

1. Drop the four old env vars from your prod configuration.
2. See [Agent image digests](https://github.com/ls1intum/Hephaestus/blob/main/docs/admin/agent-image-digests.md) for verification + rollback.

---

## Automatic vs Manual Migrations

### Automatic (No Action Needed)

| Component | Tool | Notes |
|-----------|------|-------|
| Database schema | Liquibase | Changesets apply automatically, in order, on server startup |

### Manual (Action Required)

| Component | How | Notes |
|-----------|-----|-------|
| Environment variables | Check release notes | New config may be required |
| Docker compose | Check `docker/` files | Image versions may change |

---

## Stability Roadmap

### v1.0.0 (Future)

At v1.0.0 the [Compatibility Policy](https://ls1intum.github.io/Hephaestus/admin/compatibility-policy)
takes effect — the public contract, the "any 1.x → any later 1.y" upgrade guarantee,
deprecation-ahead-of-removal, and latest-release-only support. Until then, expect rapid iteration and
occasional breaking changes in minor releases.

---

## Common Migration Scenarios

### You build against our REST API

The API surface is published as `server/openapi.yaml` in each release; regenerate your client from it
and review the release notes for endpoint changes.

### New Required Environment Variable

1. Check the release notes and the [Production Setup](https://ls1intum.github.io/Hephaestus/admin/production-setup) guide for new variables
2. Add them to your deployment's environment (see the `docker/compose.app.yaml` env block)
3. Restart services

### Database Schema Changed

Liquibase applies changelogs automatically, in order, on server startup. A failure is not silent: the
application context fails to start, so the container exits and your orchestrator restarts it — a
crash-loop whose first useful line is in the *first* startup attempt's log, not the latest. Capture
that before doing anything else:

```bash
docker compose logs application-server | grep -i -m5 liquibase
```

Liquibase runs each changeset in its own transaction, so a failure leaves earlier changesets applied
and the failing one rolled back. The database is therefore consistent but *partially migrated*, and
the old application version may no longer match it — do not assume rolling back the image is safe.

| Symptom in the log | What it means | What to do |
| --- | --- | --- |
| `Could not acquire change log lock` | A previous run was killed (OOM, `docker kill`, node eviction) mid-migration and left its row in `DATABASECHANGELOGLOCK`. | Confirm no server is actually running, then clear it: `UPDATE databasechangeloglock SET locked = FALSE, lockgranted = NULL, lockedby = NULL WHERE id = 1;` and restart. Never clear it while another replica may still be migrating. |
| `Validation Failed: … checksums do not match` | A changelog file that already ran was edited. Released changelogs are immutable for exactly this reason. | Restore the file to its released content and fix forward with a *new* changelog. Do not `clearCheckSums` on production to make the error go away — it tells Liquibase to trust a file whose applied effect you no longer know. |
| A constraint or `NOT NULL` addition fails | Existing rows violate the new rule. CI replays migrations against an empty database, so a data-incompatible migration passes CI and fails only on real data. | Do not hand-edit the schema. Report it with the failing changeset id; the fix ships as a new changelog that cleans the data first. |
| `permission denied` / `must be owner of` | The database user lacks DDL rights on an existing object. | Grant ownership or `ALTER` on the named object and restart. |

**Fix forward, do not roll back.** Changelogs carry `<rollback>` blocks, but they are never exercised
in CI and are not a supported recovery path. The supported recoveries, in order of preference:

1. **Wait for a patch release** that fixes the changeset forward. A partially migrated database keeps
   serving on the previous image only if no applied changeset broke it — check the log for which
   changesets succeeded before deciding.
2. **Restore from backup** if the instance must come back now and forward-fixing will take longer than
   the outage budget. Follow
   [Backup & restore](https://ls1intum.github.io/Hephaestus/admin/backup-restore); restore the
   database dump *and* the `.env` holding `HEPHAESTUS_SECURITY_ENCRYPTION_KEY`, or every encrypted
   credential in the restored database is unreadable. Then pin `IMAGE_TAG` to the version the dump
   was taken under so it is not immediately re-migrated by the release that failed.

Take a database dump before every upgrade that ships a migration. The release notes flag which ones
do.

---

## Getting Help

1. 📖 [GitHub Discussions](https://github.com/ls1intum/Hephaestus/discussions) - Ask the community
2. 🐛 [Issues](https://github.com/ls1intum/Hephaestus/issues) - Report problems
3. 📝 [CHANGELOG.md](./CHANGELOG.md) - Detailed change history
4. 🔄 [Release Notes](https://github.com/ls1intum/Hephaestus/releases) - Per-version details
