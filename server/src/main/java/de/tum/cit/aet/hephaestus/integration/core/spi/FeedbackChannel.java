package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * Root feedback SPI — every kind that declares {@link Capability#FEEDBACK_DELIVERY}
 * implements this. {@link InlineFindingChannel} and {@link ApprovalChannel} are
 * separate capability-gated SPIs.
 *
 * <p>Vendor-specific subject formatting (e.g. GitHub {@code owner/repo#42} vs
 * GitLab {@code group/project!42}) lives behind {@link #formatPullRequestSubjectId} so
 * the agent module never branches on {@link IntegrationKind}.
 */
public interface FeedbackChannel {
    IntegrationKind kind();

    SummaryHandle postSummary(FeedbackTarget target, FeedbackContent content);

    /**
     * Edit an already-posted summary <em>in place</em> (ADR 0021 re-review UX): the persistent overview
     * comment is updated rather than re-posted, so a re-reviewed PR/MR keeps ONE evolving summary thread
     * instead of accumulating one comment per run (the Qodo {@code persistent_comment} / CodeRabbit model).
     * {@code externalId} is the handle a prior {@link #postSummary} returned.
     *
     * <p>Vendors whose summary surface cannot be edited (append-only) keep the default and the caller falls
     * back to a fresh {@link #postSummary}. A vendor that <em>can</em> edit but finds the prior comment gone
     * (a human deleted it) signals that with {@link FeedbackDeliveryException} so the caller re-posts.
     *
     * @throws UnsupportedOperationException if this channel cannot edit a summary in place
     * @throws FeedbackDeliveryException if the edit was attempted but the vendor rejected it
     */
    default SummaryHandle updateSummary(FeedbackTarget target, String externalId, FeedbackContent content) {
        throw new UnsupportedOperationException("Channel " + kind() + " does not support editing a summary in place");
    }

    /**
     * Format the vendor's external identifier for a pull request / merge request. GitHub
     * uses {@code repoFullName#prNumber}; GitLab uses {@code repoFullName!prNumber};
     * future kinds add their own. The {@code subjectExternalId} stored on the
     * vendor post id is recorded as a {@code FeedbackPlacement.external_ref} (ADR 0021 C6).
     *
     * @throws IllegalArgumentException if {@code repoFullName} is not well-formed for the
     *     vendor (e.g. GitHub's two-segment {@code owner/repo} requirement).
     */
    String formatPullRequestSubjectId(String repoFullName, int prNumber);

    /**
     * Format the vendor's external identifier for an issue. Both GitHub and GitLab address issues as
     * {@code repoFullName#issueNumber}; the GitLab channel routes a {@code #}-suffixed subject to the
     * issue note path (vs {@code !} for a merge request). Default mirrors that convention; a vendor with
     * a different scheme overrides.
     */
    default String formatIssueSubjectId(String repoFullName, int issueNumber) {
        if (repoFullName == null || repoFullName.isBlank()) {
            throw new IllegalArgumentException("repoFullName is required");
        }
        return repoFullName + "#" + issueNumber;
    }

    /** Hephaestus's typed reference to the subject the feedback attaches to. */
    record FeedbackTarget(IntegrationRef ref, String subjectExternalId, String resourceUrl) {}

    record FeedbackContent(String body, String marker) {}

    /** Vendor-side post identifier recorded on {@code FeedbackPlacement.external_ref} for edit-in-place (ADR 0021 C6). */
    record SummaryHandle(String externalId) {}
}
