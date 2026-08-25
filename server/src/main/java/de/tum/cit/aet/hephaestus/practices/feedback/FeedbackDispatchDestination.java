package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * Every provider write that CREATES a provider-identified object goes through {@code feedback_dispatch},
 * because a crashed caller cannot otherwise tell whether the object exists. A write that only converges
 * provider state toward state we can re-derive — an edit of a comment id we hold, or a reconcile keyed by
 * a marker already living in the provider — stays direct, provided re-running it is safe and its failure
 * reaches a durable retry. A create with neither property is a bug, not a third category.
 */
public enum FeedbackDispatchDestination {
    ARTIFACT_SUMMARY,
    APPROVED_ARTIFACT_COMMENT,
    RE_REVIEW_PING,
}
