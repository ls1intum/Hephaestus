---
id: agent-image-digests
title: Agent image digest pinning
sidebar_position: 3
description: How the agent-pi image is pinned by sha256 digest in production, where the pin comes from, and how to roll back.
---

The `agent-pi` Docker image runs every practice-review sandbox and every Pi mentor container. Production references it by **sha256 digest** so the bytes can't change under a running deploy and GHCR retention can't break sandboxes between releases.

The digest is **not stored in this repository**. Each GitHub Release publishes a signed `release-vX.Y.Z.yaml` asset. The `release-pin-fetcher` init service in `docker/compose.app.yaml` downloads and cosign-verifies it at deploy time onto a shared volume. `application-server` imports it via `spring.config.import`, and `AgentImagePinGuard` refuses to start unless `hephaestus.agent.image.reference` is digest-pinned.

## Prerequisites

1. **Docker Compose v2.20+** on the deploy host.
2. **No `HEPHAESTUS_AGENT_IMAGE_REFERENCE` override** in the deploy substrate's `environment:` block — Spring's env source overrides `spring.config.import`. Leave it unset unless you are intentionally pinning to a different digest (see selective hotfix below).
3. **Outbound HTTPS to `github.com`** (release asset) and to `fulcio.sigstore.dev` + `rekor.sigstore.dev` + `tuf-repo-cdn.sigstore.dev` (cosign keyless verification).
4. **`IMAGE_TAG` env var** set by the deploy substrate.

## Verification

```bash
# Verify by OCI subject (application-server image):
gh attestation verify oci://ghcr.io/ls1intum/hephaestus/application-server:vX.Y.Z \
  --owner ls1intum --predicate-type https://in-toto.io/attestation/release/v0.1

# Verify by file subject (the pin asset itself):
gh release download vX.Y.Z --repo ls1intum/Hephaestus --pattern 'release-*.yaml'
gh attestation verify release-vX.Y.Z.yaml \
  --owner ls1intum --predicate-type https://in-toto.io/attestation/release/v0.1
```

The release predicate lists `application-server`, `agent-pi`, and the pin asset as subjects, cryptographically bound to this repo's release workflow.

## Local development

Override the property directly — no init service involved:

```bash
echo 'hephaestus.agent.image.reference: ghcr.io/ls1intum/hephaestus/agent-pi:dev' \
  > server/src/main/resources/application-local.yaml
```

Or via env var: `export HEPHAESTUS_AGENT_IMAGE_REFERENCE=ghcr.io/ls1intum/hephaestus/agent-pi:dev`.

## Rollback

**Full rollback to a prior release**: set `IMAGE_TAG=<previous-version>` in the deploy substrate and redeploy. The init service downloads that version's signed pin.

**Selective hotfix (pin agent-pi alone)**: set `HEPHAESTUS_AGENT_IMAGE_REFERENCE` in the deploy substrate. Spring's env source overrides the file:

```bash
HEPHAESTUS_AGENT_IMAGE_REFERENCE=ghcr.io/ls1intum/hephaestus/agent-pi@sha256:<older-digest>
```

Find prior digests via `gh release download <version> --repo ls1intum/Hephaestus --pattern 'release-*.yaml'`.

**Rollback to a release that predates this pattern (no signed asset)**: set `HEPHAESTUS_RELEASE_PIN_SKIP=true` on the init service plus the `HEPHAESTUS_AGENT_IMAGE_REFERENCE` override above. The init service then explicitly exits without writing the pin file, and `AgentImagePinGuard` validates the env-supplied digest.
