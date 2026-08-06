package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;

/**
 * Where a piece of feedback about an artifact can be put.
 *
 * <p>Core owns this enum because delivery genuinely branches on it — a summary goes through
 * {@code FeedbackChannel}, a diff-anchored note through {@code InlineFindingChannel}, and the two are
 * separate SPIs with separate failure modes. That split is why the enum is finer-grained than the
 * persisted {@code practices.feedback.FeedbackChannel}, where both in-context lanes collapse to
 * {@code IN_CONTEXT}: the ledger only records that the developer was told in place, while the
 * delivery path has to know which bean to call.
 *
 * <p>A lane declared by an {@code ArtifactDescriptor} is a claim that feedback <em>can</em> land there
 * for that kind; a lane declared by an {@code IntegrationManifest} is a claim that this vendor
 * <em>will</em> put it there, and the bootstrap holds the vendor to owning the matching
 * {@link Capability}.
 */
public enum FeedbackLane {
    /** One comment on the artifact itself. Delivered through {@code FeedbackChannel.postSummary}. */
    IN_CONTEXT_SUMMARY,

    /**
     * A note anchored to a position in a diff. Only an artifact that carries a diff has this lane, which
     * is the fact {@code ArtifactKind.hasInlineLane()} states from the practices side.
     */
    IN_CONTEXT_INLINE,

    /** A turn in an ongoing mentor conversation with the recipient, on a messaging integration. */
    CONVERSATION,

    /**
     * The recipient's private reflection surface inside Hephaestus. Deliberately not deliverable by an
     * integration — it is ours, and a vendor claiming it would be claiming a surface it cannot reach.
     */
    PROFILE,
}
