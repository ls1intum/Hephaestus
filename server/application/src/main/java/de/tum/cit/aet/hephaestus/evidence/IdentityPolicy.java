package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;

/**
 * Whether a source's capture can be anchored to an identity that cannot change under it, such as a
 * commit SHA. There is deliberately no staleness verdict to go with it — nothing in the capture
 * pipeline could produce one. The declaration earns its place by constraining which sources may claim
 * a pinnable identity (see {@link ArtifactSourceContract}).
 */
public record IdentityPolicy(IdentityMode mode) {
    public IdentityPolicy {
        Objects.requireNonNull(mode, "mode");
    }
}
