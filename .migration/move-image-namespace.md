#### 🔴 Container images moved to `ghcr.io/hephaestus-build/<image>`

**Affected**: deployments with registry mirrors, egress allowlists, or hand-written image
references. The standard install and upgrade flow — Compose plus the signed release lock — follows
the move on its own.

**Before**: all images lived under `ghcr.io/ls1intum/hephaestus/<image>`, and every release lock was
signed as `ls1intum/Hephaestus`.

**After**: this release and everything newer publish under `ghcr.io/hephaestus-build/<image>`
(without the redundant `hephaestus/` segment) and sign as `hephaestus-build/Hephaestus`. Releases
published before the move are unchanged: GHCR packages do not transfer between organizations, so
their images stay at `ghcr.io/ls1intum/hephaestus/<image>`, and their signatures name the old
repository forever. `security/release-identities.json` records which namespace and signing identity
each release uses, and the upgrade gate, deploy workflows, and lock verifier resolve it per release.

**Migration**: allow `ghcr.io/hephaestus-build/<image>` wherever registry access is restricted or
mirrored. If you overrode an image reference by hand — for example a pinned
`HEPHAESTUS_AGENT_IMAGE_REFERENCE` — repoint it at the new namespace when you next update the pin.
When verifying an old release yourself, use the identity recorded for its version in
`security/release-identities.json` (releases before the move:
`https://github.com/ls1intum/Hephaestus/.github/workflows/release.yml@refs/heads/main`).
