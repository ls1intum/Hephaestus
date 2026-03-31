package de.tum.in.www1.hephaestus.practices.review;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Delivery gate that decides whether to post/edit a comment after the practice review agent
 * completes.
 * <p>
 * This is the post-execution counterpart to {@link PracticeReviewDetectionGate} (pre-execution).
 * Findings are <strong>always stored</strong> regardless of delivery outcome — research data is
 * never lost. Only comment posting is gated.
 * <p>
 * The gate takes pre-resolved parameters to avoid cross-module dependencies. Data loading
 * (PR state, user preferences, dedup lookup) is done by the caller in the agent handler layer.
 *
 * <h2>Gate checks (in order)</h2>
 * <ol>
 *   <li>PR not found → STORE ONLY</li>
 *   <li>PR closed/merged → STORE ONLY</li>
 *   <li>PR draft + {@code skipDrafts} config → STORE ONLY</li>
 *   <li>User opted out ({@code aiReviewEnabled=false}) → STORE ONLY</li>
 *   <li>All positive + previous comment exists → EDIT ALL RESOLVED (system-generated message)</li>
 *   <li>All positive, no previous comment → STORE ONLY (silence = positive signal)</li>
 *   <li>No delivery content for negative findings → STORE ONLY (malformed agent output)</li>
 *   <li>Has negatives + previous comment → EDIT EXISTING</li>
 *   <li>Has negatives, no previous comment → POST NEW</li>
 * </ol>
 */
@Service
public class PracticeReviewDeliveryGate {

    private static final Logger log = LoggerFactory.getLogger(PracticeReviewDeliveryGate.class);

    private final PracticeReviewProperties properties;

    public PracticeReviewDeliveryGate(PracticeReviewProperties properties) {
        this.properties = properties;
    }

    /**
     * Evaluates whether a comment should be posted, edited, or suppressed.
     *
     * @param pullRequest         the resolved pull request (null if not found in DB)
     * @param hasNegativeFindings whether any NEGATIVE findings were persisted
     * @param hasDeliveryContent  whether the agent output contained delivery content (mrNote/diffNotes)
     * @param userAiReviewEnabled whether the PR author has AI review enabled (true if no preference record)
     * @param previousCommentId   the comment ID from a previous delivery for the same PR (null if first analysis)
     * @return a {@link DeliveryDecision} indicating the action to take
     */
    public DeliveryDecision evaluate(
        @Nullable PullRequest pullRequest,
        boolean hasNegativeFindings,
        boolean hasDeliveryContent,
        boolean userAiReviewEnabled,
        @Nullable String previousCommentId
    ) {
        // Normalize blank to null — PullRequestCommentPoster treats blank as "no existing comment"
        String effectiveCommentId = (previousCommentId != null && !previousCommentId.isBlank())
            ? previousCommentId
            : null;

        // 1. PR not found
        if (pullRequest == null) {
            log.debug("Review delivery gate: STORE_ONLY, reason=prNotFound");
            return new DeliveryDecision.StoreOnly("PR not found");
        }

        Long prId = pullRequest.getId();

        // 2. PR state: closed always blocked; merged blocked unless deliverToMerged is enabled
        if (pullRequest.getState() == Issue.State.CLOSED) {
            log.info("Review delivery gate: STORE_ONLY, reason=PR is CLOSED, prId={}", prId);
            return new DeliveryDecision.StoreOnly("PR is CLOSED");
        }
        if (pullRequest.getState() == Issue.State.MERGED && !properties.deliverToMerged()) {
            log.info("Review delivery gate: STORE_ONLY, reason=PR is MERGED (deliverToMerged=false), prId={}", prId);
            return new DeliveryDecision.StoreOnly("PR is MERGED");
        }

        // 3. Draft gate (defense in depth — detection gate also checks this)
        if (properties.skipDrafts() && pullRequest.isDraft()) {
            log.info("Review delivery gate: STORE_ONLY, reason=draftPR, prId={}", prId);
            return new DeliveryDecision.StoreOnly("PR is draft");
        }

        // 4. User preference (opt-out model)
        if (!userAiReviewEnabled) {
            log.info("Review delivery gate: STORE_ONLY, reason=userOptedOut, prId={}", prId);
            return new DeliveryDecision.StoreOnly("user opted out");
        }

        // 5–6. All findings positive
        // NOTE: this check is BEFORE the delivery content check so that EDIT_ALL_RESOLVED
        // is reachable even when the agent correctly omits delivery for all-positive findings.
        // The "all resolved" message is system-generated, not agent-generated.
        if (!hasNegativeFindings) {
            if (effectiveCommentId != null) {
                // Re-analysis: all issues resolved → edit to positive message
                log.info("Review delivery gate: EDIT_ALL_RESOLVED, prId={}, commentId={}", prId, effectiveCommentId);
                return new DeliveryDecision.EditAllResolved(pullRequest, effectiveCommentId);
            }
            // First analysis: silence = positive signal
            log.debug("Review delivery gate: STORE_ONLY, reason=allFindingsPositive, prId={}", prId);
            return new DeliveryDecision.StoreOnly("all findings positive");
        }

        // 7. No delivery content for negative findings → agent output malformed, store only
        if (!hasDeliveryContent) {
            log.warn("Review delivery gate: STORE_ONLY, reason=noDeliveryContentForNegativeFindings, prId={}", prId);
            return new DeliveryDecision.StoreOnly("no delivery content for negative findings");
        }

        // 8–9. Has negative findings with delivery content
        if (effectiveCommentId != null) {
            // Re-analysis: edit existing comment (no diff notes re-posted)
            log.info("Review delivery gate: EDIT_EXISTING, prId={}, commentId={}", prId, effectiveCommentId);
            return new DeliveryDecision.EditExisting(pullRequest, effectiveCommentId);
        }

        // First analysis with negatives: post new comment + diff notes
        log.info("Review delivery gate: POST_NEW, prId={}", prId);
        return new DeliveryDecision.PostNew(pullRequest);
    }
}
