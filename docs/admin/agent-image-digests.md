---
id: agent-image-digests
title: Agent image resolution and pinning
sidebar_position: 3
description: Where the agent-pi image reference comes from in each environment, how production pins it by sha256 digest, how to check the image matches the server, and how to roll back.
---

The `agent-pi` Docker image runs every practice-review sandbox and every Pi mentor container. It is not an independent component: the application server stages TypeScript runners into a workspace and executes them with the `bun` inside this image, so the two are one runtime contract shipped as two artifacts and must come from the same build.

Production references the image by **sha256 digest** so the bytes can't change under a running deploy and GHCR retention can't break sandboxes between releases.

The digest is **not stored in this repository**. Each GitHub Release publishes a signed `release-vX.Y.Z.yaml` asset. The `release-pin-fetcher` init service in `docker/compose.app.yaml` downloads and cosign-verifies it at deploy time onto a shared volume. `application-server` imports it via `spring.config.import`, and `AgentImagePinGuard` refuses to start unless `hephaestus.agent.image.reference` is digest-pinned.

That path needs, on the deploy host:

1. **Docker Engine ≥ 24** and **Docker Compose ≥ 2.24.4** (`docker compose version`) — the floor the [install guide](./install) sets for the whole stack.
2. **Outbound HTTPS to `github.com`** (release asset) and to `fulcio.sigstore.dev` + `rekor.sigstore.dev` + `tuf-repo-cdn.sigstore.dev` (cosign keyless verification).
3. **`IMAGE_TAG` set by the deploy substrate**, since the asset is fetched for that version — and, as the next section explains, everything else is resolved from it too.
4. **No `HEPHAESTUS_AGENT_IMAGE_REFERENCE` override**, which outranks the signed pin. Leave it unset unless you are deliberately pinning something else (see [selective hotfix](#rollback)).

## Where the reference comes from

Highest precedence first. There is no fourth source, and no built-in fallback: a server that cannot resolve a reference refuses to start rather than guessing.

| Source | Applies to | Strength |
| --- | --- | --- |
| `HEPHAESTUS_AGENT_IMAGE_REFERENCE` | an operator's deliberate override | whatever you name |
| The signed pin at `/pin/release-pin.yaml` | release deploys | cosign-verified digest |
| `agent-pi:$IMAGE_TAG`, derived from `APP_VERSION` | everything else | matched commit, by tag |

The derived fallback is why a deployment tracking `main` gets the agent image CI built from **its own commit**.

Never name a **channel tag** — any tag a workflow moves from build to build rather than one that names a single build. We publish three spellings and none of them is safe here:

| Spelling | Example | Moves when |
| --- | --- | --- |
| Named channel | `latest` | any release |
| Branch tag | `main` | any push to that branch |
| Version series | `0.73` | any patch release in the `0.73` line |

Each pairs your server with an agent image nobody built it against, and each can change under a running deploy. The server therefore refuses to start on any of them — and on `stable` and `edge`, which we do not publish but which conventionally mean the same thing — on a reference with no tag at all (a daemon resolves that to `latest`), and on one whose tag is empty — which is what an empty `IMAGE_TAG` derives. A full version (`0.73.2`), a pre-release (`0.74.0-rc.1`), a commit SHA and a locally built tag all name one build and are accepted.

**This holds whether or not the agent is enabled**: the check reads configuration, not the sandbox. And because the reference is derived, `IMAGE_TAG=latest` or `IMAGE_TAG=0.73` trips it without your ever naming an agent image. See [ADR 0031](https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0031-agent-image-follows-the-deployments-own-tag.md).

## Checking the pair matches

The image records the runtime contract it was built for as the OCI label `hephaestus.agent.runtime-contract`, alongside the Bun and Pi SDK versions it carries. The application server reads that label when it pulls the image, and logs an ERROR if the image cannot run its runners: naming both contract versions when the image declares a different one, and naming the image alone when it carries no such label. To check by hand:

```bash
docker inspect --format '{{json .Config.Labels}}' \
  ghcr.io/ls1intum/hephaestus/agent-pi:<tag-or-digest> | jq
```

An image with no such label predates the contract and is not a build any current server can drive.

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

A checkout has no `APP_VERSION`, so the reference derives to `agent-pi:0.0.0-development`, which is not published — the server warns at startup and names the variable to set. Point it at an image you built or pulled:

```bash
echo 'hephaestus.agent.image.reference: ghcr.io/ls1intum/hephaestus/agent-pi:dev' \
  > server/application/src/main/resources/application-local.yml
```

Or via env var: `export HEPHAESTUS_AGENT_IMAGE_REFERENCE=ghcr.io/ls1intum/hephaestus/agent-pi:dev`.

The same applies to a preview deployment: previews derive from their own commit, and CI publishes an agent image at that commit only when the pull request touched `docker/agents/**` or a workflow. To exercise the sandbox from any other pull request, name the image in the preview's `.env` (see `docker/preview/.env.example`).

## Rollback

**Full rollback to a prior release**: set `IMAGE_TAG=<previous-version>` — the full `X.Y.Z`, not the `X.Y` series — in the deploy substrate and redeploy. The init service downloads that version's signed pin.

**Selective hotfix (pin agent-pi alone)**: set `HEPHAESTUS_AGENT_IMAGE_REFERENCE` in the deploy substrate. Spring's env source overrides the file:

```bash
HEPHAESTUS_AGENT_IMAGE_REFERENCE=ghcr.io/ls1intum/hephaestus/agent-pi@sha256:<older-digest>
```

Find prior digests via `gh release download <version> --repo ls1intum/Hephaestus --pattern 'release-*.yaml'`.

**Rollback to a release that predates this pattern (no signed asset)**: set `HEPHAESTUS_RELEASE_PIN_SKIP=true` on the init service plus the `HEPHAESTUS_AGENT_IMAGE_REFERENCE` override above. The init service then explicitly exits without writing the pin file, and `AgentImagePinGuard` validates the env-supplied digest.

**Deployments that track `main` rather than a release** set `HEPHAESTUS_RELEASE_PIN_SKIP=true` and `HEPHAESTUS_AGENT_IMAGE_REQUIRE_DIGEST=false`: there is no signed asset for a `main` commit, so the reference derives from `IMAGE_TAG` and is a tag rather than a digest. That is still a matched pair — the agent image built from the same commit — but it is weaker than a release, which is why production keeps the digest requirement.
