# Migration Guide

This document helps you upgrade between versions of Hephaestus.

> ⚠️ **Pre-1.0 Notice**: We are in active development. Minor versions (0.x.0) may contain breaking changes. Always test in staging before production.

## Quick Reference

| Symbol | Meaning |
|--------|---------|
| 🔴 | **Breaking**: Action required before upgrade |
| 🟡 | **Deprecated**: Works now, removed in next major |
| 🟢 | **New**: No action needed |

## Check Your Version

```bash
# Deployed version
curl -s https://api.hephaestus.cit.tum.de/actuator/info | jq '.build.version'

# Git tag
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
4. ✅ Regenerate API clients: `pnpm run generate:api`
5. ✅ Approve production deployment after staging verification

---

## Version History

### v0.10.0 (Upcoming)

#### 🔴 Agent runtime: image config consolidated under `hephaestus.agent.image.*`

**Version**: v0.10.0
**Affected**: any deployment that pinned the agent-pi image via `HEPHAESTUS_AGENT_PI_IMAGE`, `HEPHAESTUS_MENTOR_AGENT_IMAGE`, or the matching pull-policy env vars.

**Before**:

```bash
HEPHAESTUS_AGENT_PI_IMAGE=ghcr.io/ls1intum/hephaestus/agent-pi:latest
HEPHAESTUS_MENTOR_AGENT_IMAGE=ghcr.io/ls1intum/hephaestus/agent-pi:latest
HEPHAESTUS_AGENT_PI_PULL_POLICY=IF_NOT_PRESENT
HEPHAESTUS_MENTOR_AGENT_PULL_POLICY=IF_NOT_PRESENT
```

**After** (`docker/agent-image-pin.env`, rewritten by the release workflow):

```bash
HEPHAESTUS_AGENT_IMAGE_REFERENCE=ghcr.io/ls1intum/hephaestus/agent-pi@sha256:<digest>
```

`pull-policy` and `require-digest` are now Spring properties set in `application-prod.yml`, not env vars.

**Migration**:

1. Drop the four old env vars from your prod configuration.
2. Production reads the pinned digest from `docker/agent-image-pin.env` via Compose `env_file:`. If `HEPHAESTUS_AGENT_PI_IMAGE` is set in the Coolify UI, **unset it** — UI values shadow `env_file:`.
3. See [Agent image digests](https://github.com/ls1intum/Hephaestus/blob/main/docs/admin/agent-image-digests.md) for verification + rollback.

**Why**: GHCR retention previously pruned `:latest` between releases; sandboxes 500'd until the next push refilled the tag. Long-lived mentor containers on tags can pick up unexpected upstream changes mid-session. Digest pinning protects bytes-in-flight; the `require-digest` startup guard fails fast if a tag ever slips back into config.



### v0.9.0

No breaking changes documented.

### v0.8.0

No breaking changes documented.

---

## Breaking Change Template

When breaking changes occur, they're documented like this:

### 🔴 [Component]: Description

**Version**: v0.x.0
**Affected**: What's impacted

**Before**:

```typescript
// Old code
```

**After**:

```typescript
// New code
```

**Migration**:

1. Step one
2. Step two

**Why**: Rationale for the change

---

## Automatic vs Manual Migrations

### Automatic (No Action Needed)

| Component | Tool | Notes |
|-----------|------|-------|
| Database schema | Liquibase | Runs on server startup |
| Entity relationships | JPA | Schema changes auto-applied |

### Manual (Action Required)

| Component | How | Notes |
|-----------|-----|-------|
| API clients | `pnpm run generate:api` | After any OpenAPI spec change |
| Environment variables | Check release notes | New config may be required |
| Docker compose | Check `docker/` files | Image versions may change |

---

## Stability Roadmap

### v1.0.0 (Future)

When we release v1.0.0, we commit to:

- ✅ No breaking changes in patch releases (1.0.x)
- ✅ Deprecation warnings before removal
- ✅ Migration guides for every breaking change
- ✅ LTS support consideration

Until then, expect rapid iteration and occasional breaking changes.

---

## Common Migration Scenarios

### API Response Shape Changed

1. Regenerate client: `pnpm run generate:api`
2. Fix TypeScript errors
3. Update any manual type assertions

### New Required Environment Variable

1. Check `.env.example` for new variables
2. Add to your `.env` and deployment configs
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
