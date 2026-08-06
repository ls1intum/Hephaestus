package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;

/**
 * Whether a source's capture can be anchored to an identity that cannot change under it.
 *
 * <p>This was called a freshness policy while a practice could demand a CURRENT capture. That
 * requirement was never enforceable — nothing ever produced a stale verdict — so it is gone, and
 * what remains is the honest half: a declaration that a capture carries a pinnable identity, such
 * as a commit SHA. The declaration still earns its place because it constrains which sources may
 * claim one (see {@link ArtifactSourceContract}).
 */
public record IdentityPolicy(IdentityMode mode) {
    public IdentityPolicy {
        Objects.requireNonNull(mode, "mode");
    }
}
