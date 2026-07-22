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

```bash
# Deployed version (the image tag your containers run; APP_VERSION is derived from it)
docker compose -f docker/compose.app.yaml images application-server

# Latest release
git describe --tags --abbrev=0
```

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

#### 🔴 LLM provider configuration moved from env vars to the admin console

**Affected**: any deployment setting `HEPHAESTUS_WORKER_LLM_BASE_URL`, `HEPHAESTUS_WORKER_LLM_API_KEY`, `HEPHAESTUS_SANDBOX_LLM_PROXY_ENABLED`, or an `AGENT_DEFAULT_CONFIG_*` variable.

**Before**: the worker pod's LLM upstream/key were passed through env vars (`HEPHAESTUS_WORKER_LLM_BASE_URL` / `HEPHAESTUS_WORKER_LLM_API_KEY`), and the LLM proxy could be toggled per pod with `HEPHAESTUS_SANDBOX_LLM_PROXY_ENABLED` (`hephaestus.sandbox.llm-proxy.enabled`).

**After**: OpenAI and other OpenAI-compatible endpoints are registered at runtime under **Instance admin → AI models**, with an explicit Chat Completions or Responses API contract, per-model pricing, and optional sharing with workspaces. Workspaces can also connect their own compatible endpoint. The LLM proxy — the only path a sandbox has to a provider key — now runs automatically wherever agent jobs execute; it has no standalone enable flag. The three env vars above are no longer read.

**Migration**:

1. Remove `HEPHAESTUS_WORKER_LLM_BASE_URL`, `HEPHAESTUS_WORKER_LLM_API_KEY`,
   `HEPHAESTUS_SANDBOX_LLM_PROXY_ENABLED`, and every `AGENT_DEFAULT_CONFIG_*` variable from your
   deployment. They are silently ignored, not an error, but keeping them is misleading.
2. Register your OpenAI-compatible endpoint(s) under Instance admin → AI models (or have a workspace admin connect their own under the workspace's AI settings).
3. Rebind each legacy agent configuration to a catalog model through the admin console, then
   re-enable it. The migration deliberately disables every enabled configuration without exactly one
   catalog binding; it never guesses which connection, credential owner, model, or price an old row
   should use.

#### 🔴 Agent job queue moved from NATS to PostgreSQL

**Affected**: any deployment setting `AGENT_NATS_ENABLED`, `HEPHAESTUS_AGENT_NATS_SERVER`, `AGENT_NATS_MAX_ACK_PENDING`, or `AGENT_NATS_FETCH_BATCH_SIZE`.

**Before**: the practice-review agent job queue was delivered over a NATS JetStream stream (`AGENT`); a worker pulled a job id off the stream, then loaded the job from PostgreSQL to execute it. Interactive mentor turns were and remain request-affine; they do not use `agent_job`.

**After**: workers poll `agent_job` directly and claim a batch with `FOR UPDATE SKIP LOCKED` — PostgreSQL, already the source of truth for job state, is now also the delivery mechanism. `AGENT_NATS_ENABLED` is replaced by `AGENT_ENABLED` (default `false`). New optional tuning: `AGENT_POLL_INTERVAL` (default `1s`), `AGENT_CLAIM_BATCH_SIZE` (default `5`), `AGENT_MAX_RETRIES` (default `5`), `AGENT_PAYLOAD_RETENTION` (default `14d`), and `AGENT_ROW_RETENTION` (default `90d`). NATS itself is unaffected everywhere else — it remains required for webhook ingest and SCM/Slack sync. See [ADR 0025](https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0025-agent-job-queue-on-postgresql.md).

**Migration**:

1. Set `AGENT_ENABLED=true` (replacing `AGENT_NATS_ENABLED=true`) on **every** role that needs to submit, execute, or recover jobs — not just the role that claims and runs them. In a split-pod deployment that means **both** `application-server` (submits jobs from PR/issue events and runs the orphan-recovery sweep — both gate on this same flag, independent of the worker role) **and** `application-worker` (claims and executes them, additionally gated on the worker role); `docker/compose.app.yaml` already sets the same `AGENT_ENABLED` value on both services. In the monolith, set it once. The `application-worker` profile now defaults `AGENT_ENABLED` to `true` on its own (see `docs/admin/runtime-roles.mdx`) — an explicit env var is still the reliable, portable way to set it consistently across every pod.
2. Remove `HEPHAESTUS_AGENT_NATS_SERVER`, `AGENT_NATS_MAX_ACK_PENDING`, and `AGENT_NATS_FETCH_BATCH_SIZE` from your deployment — they are silently ignored, not an error, but keeping them is misleading.
3. Optional cleanup: the `AGENT` JetStream stream is no longer read from or written to. Delete it with `nats stream rm AGENT` if you want to reclaim its storage; leaving it in place is harmless.
4. Do not remove NATS itself or `NATS_ENABLED` — webhook ingest and SCM/Slack sync still require it.

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

Liquibase handles this automatically. If you see errors:

1. Check Liquibase changelog for the migration
2. Ensure database user has ALTER permissions
3. Check for data that violates new constraints

---

## Getting Help

1. 📖 [GitHub Discussions](https://github.com/ls1intum/Hephaestus/discussions) - Ask the community
2. 🐛 [Issues](https://github.com/ls1intum/Hephaestus/issues) - Report problems
3. 📝 [CHANGELOG.md](./CHANGELOG.md) - Detailed change history
4. 🔄 [Release Notes](https://github.com/ls1intum/Hephaestus/releases) - Per-version details
