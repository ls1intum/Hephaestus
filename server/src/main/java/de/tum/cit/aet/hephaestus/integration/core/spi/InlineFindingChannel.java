package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;

/**
 * Capability-gated SPI for posting inline findings (SCM diff notes, knowledge-base
 * document-anchor comments). Kinds that don't declare {@link Capability#INLINE_FINDINGS}
 * never resolve via this registry — Slack and similar messaging vendors are
 * compile-time excluded.
 */
public interface InlineFindingChannel {
    IntegrationKind kind();

    InlineResult postInlineFindings(FeedbackChannel.FeedbackTarget target, List<InlineFinding> findings);

    /**
     * Removes this run's previously-posted inline findings (matched by {@code marker}) WITHOUT posting new
     * ones — the clear half of clear-then-post, callable on a zero-note re-run so stale notes from an earlier
     * run never survive a review that now finds nothing inline (the empty-diff pathology where a re-reviewed
     * PR keeps line-numbered notes on code no longer in the diff).
     *
     * <p>Default is a no-op: vendors whose review model is append-only/immutable (GitHub posts a single
     * {@code addPullRequestReview} with no per-thread delete path) cannot reconcile and inherit this safely.
     * Vendors with a deletable note model (GitLab) override to delete every marker-bearing note on the target.
     */
    default void clearStaleFindings(FeedbackChannel.FeedbackTarget target, String marker) {
        // no-op — see Javadoc: only deletable-note vendors override.
    }

    record InlineFinding(FindingAnchor anchor, String body, String marker) {}

    record InlineResult(int posted, int failed) {}
}
