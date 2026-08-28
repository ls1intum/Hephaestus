---
id: release-image-lock
title: Release image lock
description: Verify the image digests selected by a Hephaestus release.
---

# Release image lock

Each release publishes `release-vX.Y.Z.json` with its Sigstore bundle. The lock records the release,
source commit, provenance class, repository, multi-platform index digest, and supported platform child
digests for every deployed image. Compose uses the index digests; tags identify releases for humans.

Follow the [install guide](./install) to install or upgrade. Its verifier checks the signature, signer
identity, release identity, schema, and equality with the release evidence manifest before writing
`release-lock.env`.

## Independent verification

```bash
VERSION=vX.Y.Z
gh release download "$VERSION" --repo ls1intum/Hephaestus \
  --pattern "release-$VERSION.json" \
  --pattern "release-$VERSION.json.sigstore.json"
cosign verify-blob \
  --bundle "release-$VERSION.json.sigstore.json" \
  --certificate-identity \
    'https://github.com/ls1intum/Hephaestus/.github/workflows/release.yml@refs/heads/main' \
  --certificate-oidc-issuer 'https://token.actions.githubusercontent.com' \
  "release-$VERSION.json"
gh attestation verify "release-$VERSION.json" --owner ls1intum
```

## Rollback

Follow the [rollback procedure](./install#rollback). It selects an earlier published lock without
reconstructing metadata or resolving image tags. Production startup independently rejects a
non-digest `agent-pi` reference.
