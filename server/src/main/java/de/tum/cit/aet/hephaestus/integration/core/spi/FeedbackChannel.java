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
     * Format the vendor's external identifier for a pull request / merge request. GitHub
     * uses {@code repoFullName#prNumber}; GitLab uses {@code repoFullName!prNumber};
     * future kinds add their own. The {@code subjectExternalId} stored on the
     * {@code FeedbackPost} aggregate is what this returns.
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

    /** Vendor-side post identifier used by {@code FeedbackPostService} for edit-in-place. */
    record SummaryHandle(String externalId) {}
}
