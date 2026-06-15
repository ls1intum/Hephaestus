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
     * <p>Returns a typed {@link UpdateOutcome} rather than throwing for recoverable cases, so the caller can
     * tell apart: {@code EDITED} (success), {@code GONE} (the prior comment is confirmed deleted — re-post),
     * {@code TRANSIENT} (a rate-limit / network / unknown vendor error — keep the prior summary, do NOT
     * re-post this run, else a flaky update double-posts a second summary), and {@code UNSUPPORTED}
     * (append-only channel — re-post). A genuine data error (e.g. a blank external id) still throws
     * {@link FeedbackDeliveryException}.
     */
    default UpdateOutcome updateSummary(FeedbackTarget target, String externalId, FeedbackContent content) {
        return UpdateOutcome.unsupported();
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

    /**
     * The outcome of an {@link #updateSummary} attempt. {@code TRANSIENT} is the load-bearing case: the caller
     * must NOT create-fallback on it (that double-posts), only on {@code GONE}/{@code UNSUPPORTED}.
     */
    record UpdateOutcome(Kind kind, SummaryHandle handle, String reason) {
        public enum Kind {
            EDITED,
            GONE,
            TRANSIENT,
            UNSUPPORTED,
        }

        public static UpdateOutcome edited(SummaryHandle handle) {
            return new UpdateOutcome(Kind.EDITED, handle, null);
        }

        /** The prior comment is confirmed gone (a human deleted it) — the caller should re-post. */
        public static UpdateOutcome gone(String reason) {
            return new UpdateOutcome(Kind.GONE, null, reason);
        }

        /** A recoverable failure (rate limit, network, unknown vendor error) — keep the prior summary, do not re-post. */
        public static UpdateOutcome transientFailure(String reason) {
            return new UpdateOutcome(Kind.TRANSIENT, null, reason);
        }

        /** This channel cannot edit in place (append-only) — the caller should re-post. */
        public static UpdateOutcome unsupported() {
            return new UpdateOutcome(Kind.UNSUPPORTED, null, null);
        }
    }
}
