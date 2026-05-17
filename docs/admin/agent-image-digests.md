---
id: agent-image-digests
title: Agent image digest pinning
description: How the agent-pi image is pinned by sha256 digest in production, how the release workflow keeps the pin current, and how to roll back.
---

The `agent-pi` Docker image runs every practice-review sandbox and every Pi mentor container. Production references it by **sha256 digest** so the bytes can't change under a running deploy and GHCR retention can't break sandboxes between releases.

## Prerequisites

1. **Docker Compose v2.24+** on the deploy host (long-form `env_file: [{path, required}]` syntax).
2. **GHCR retains `:vX.Y.Z` tags.** Each release tags the agent-pi manifest; as long as those tags exist, the digest is retained. Verify the `ls1intum/hephaestus/agent-pi` package isn't configured to prune versioned tags.
3. **Coolify preserves the `env_file:` directive.** Validate by setting the file to a sentinel value and `docker compose exec application-server env` after a deploy.
4. **`HEPHAESTUS_AGENT_IMAGE_REFERENCE` is not set in the Coolify UI or `compose.app.yaml`'s `environment:` block.** Compose precedence is `environment:` > `env_file:`, and shell-interpolated `${VAR}` shadows both.

## Source of truth: `docker/agent-image-pin.env`

```bash
HEPHAESTUS_AGENT_IMAGE_REFERENCE=ghcr.io/ls1intum/hephaestus/agent-pi@sha256:<digest>
```

`compose.app.yaml` loads it via `env_file:` (with `required: false`), followed by an optional `docker/agent-image-pin.local.env` (gitignored) for local overrides. The application server reads the final value into `hephaestus.agent.image.reference`; `application-prod.yml` sets `hephaestus.agent.image.require-digest: true`, so `AgentImagePinGuard` refuses to start on a tag reference.

For local agent-pi iteration: build your image and drop a one-line override into `docker/agent-image-pin.local.env`:

```bash
docker build -t ghcr.io/ls1intum/hephaestus/agent-pi:dev docker/agents -f docker/agents/pi/Dockerfile
echo 'HEPHAESTUS_AGENT_IMAGE_REFERENCE=ghcr.io/ls1intum/hephaestus/agent-pi:dev' > docker/agent-image-pin.local.env
```

## How the pin gets updated

`.github/workflows/release.yml` runs on every semver release:

1. **Retag** `docker buildx imagetools create -t agent-pi:vX.Y.Z agent-pi:<sha>`.
2. **Verify** the OCI manifest digest (`sha256` of `imagetools inspect --raw`) for source and target tags match — catches any registry-side rewrite.
3. **Smoke** the published digest by pulling and running `bun --version && node --version`.
4. **Commit** the new line to `docker/agent-image-pin.env` and push to `main` with `[skip ci]` (race-tolerant: rebase-and-retry up to 3 times).

Images are cosign-signed (keyless OIDC) and provenance-attested by `reusable-docker-build.yml`, then verified in the same job.

## Verification

```bash
DIGEST=$(grep -oE '@sha256:[a-f0-9]{64}' docker/agent-image-pin.env)
IMAGE=ghcr.io/ls1intum/hephaestus/agent-pi${DIGEST}

cosign verify "$IMAGE" \
  --certificate-identity-regexp '^https://github.com/ls1intum/hephaestus/' \
  --certificate-oidc-issuer 'https://token.actions.githubusercontent.com'

gh attestation verify "oci://$IMAGE" --owner ls1intum
```

```bash
git log --oneline -- docker/agent-image-pin.env
gh release view vX.Y.Z
```

## Rollback procedure

```bash
git log --oneline -- docker/agent-image-pin.env
git revert <bad-release-commit>
git push origin main
```

Coolify redeploys from `main`; new sandboxes pull the rolled-back digest. In-flight mentor containers keep their already-pulled image until they restart.

**Note**: the broken `:vX.Y.Z` tag still points to the bad digest — `git revert` only moves what `main` references. If anything else pins by version tag, cut a `vX.Y.Z+1` patch release once the fix is committed.
