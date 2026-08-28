# ADR 0034: Production consumes one signed release image lock

**Status:** Accepted
**Date:** 2026-08-28
**Supersedes:** [ADR 0031](0031-agent-image-follows-the-deployments-own-tag.md)

## Context

Release evidence binds each supported platform image to an immutable digest, but tags and separate
agent pinning did not guarantee that staging and production consumed the complete verified set.
Upstream pins also need one reviewed dependency source without resolving tags during deployment.

## Decision

Each release publishes one JSON lock derived from the verified evidence manifest. It identifies the
release and source commit and records, for every first-party and reviewed upstream production image,
the repository, provenance class, index digest, and supported platform child digests.

The release workflow signs the lock with its keyless GitHub Actions identity and publishes it on an
immutable GitHub Release. Staging, production, install, upgrade, and rollback verify that identity and
validate the lock against the evidence manifest before rendering Compose. Compose accepts only index
digest references emitted by the validator. The same lock supplies the dynamically launched agent
image. `security/release-images.json` remains the reviewed source for upstream dependency updates;
the release lock is derived output.

Deployment executes the Compose files and validator from the source commit named by the signed lock.
A release fails if its inventory differs from the blessed Compose topology plus the dynamic agent.
Rollback selects an earlier published lock and never rebuilds metadata or resolves a tag.

## Consequences

- Production and staging no longer deploy branch, channel, or version tags.
- The former release-pin init container and production pin-file import are removed.
- Operators need GitHub CLI, Cosign, and the repository-pinned Bun version to prepare self-hosted
  deployment inputs.
- Preview and local development remain non-release paths and may use build-specific tags.

## Revisit trigger

Revisit when Compose natively verifies signed image collections with equivalent signer, release,
platform, and exact-topology policy.
