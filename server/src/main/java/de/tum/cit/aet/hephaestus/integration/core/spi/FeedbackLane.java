package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * Where a piece of feedback about an artifact can be put.
 *
 * <p>Delivery genuinely branches on this: a summary goes through {@link SummaryChannel}, a diff-anchored
 * note through {@link InlineFindingChannel} — separate SPIs with separate failure modes. That is why this
 * is finer-grained than the persisted {@code practices.feedback.FeedbackChannel}, where both in-context
 * lanes collapse to one value: the ledger only records that the developer was told in place, while the
 * delivery path has to know which bean to call.
 *
 * <p>A lane declared by an {@code ArtifactDescriptor} claims feedback <em>can</em> land there for that
 * kind; one declared by an {@code IntegrationManifest} claims this vendor <em>will</em> put it there, and
 * the bootstrap holds the vendor to owning the matching {@link Capability}.
 */
public enum FeedbackLane {
    /** Delivered through {@code SummaryChannel.postSummary}. */
    IN_CONTEXT_SUMMARY,

    /** A note anchored to a position in a diff; only an artifact that carries a diff has this lane. */
    IN_CONTEXT_INLINE,

    /** A turn in an ongoing mentor conversation with the recipient, on a messaging integration. */
    CONVERSATION,

    /**
     * The recipient's private reflection surface inside Hephaestus. Deliberately not deliverable by an
     * integration — it is ours, and a vendor claiming it would be claiming a surface it cannot reach.
     */
    REFLECTION,
}
