package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;

/**
 * Declares that something can assemble the subject of a review for one {@link ArtifactKind}.
 *
 * <p>A port rather than a direct dependency, because the check that needs it lives in the integration
 * framework while the implementations live in the agent's context layer, and the framework must not
 * import the agent. Its only job is to make "this kind is reviewable" falsifiable at boot: a descriptor
 * can declare {@code reviewable()} all it likes, but if nothing can materialise the artifact's context
 * then every review of it would be submitted and then fail, one job at a time, in production.
 */
public interface ReviewContextBuilder {
    /** The artifact family this builder can assemble review context for. */
    ArtifactKind artifactKind();
}
