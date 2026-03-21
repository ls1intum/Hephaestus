package de.tum.in.www1.hephaestus.practices.review;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import java.util.Objects;

/**
 * Result of the review delivery gate evaluation.
 * <p>
 * Uses a sealed interface so callers must handle all outcomes, and each
 * variant carries only the data relevant to that outcome:
 * <ul>
 *   <li>{@link StoreOnly}: findings stored but no comment posted (silence = positive signal,
 *       user opted out, PR closed/draft, etc.)</li>
 *   <li>{@link PostNew}: first analysis with negative findings — post a new comment + diff notes</li>
 *   <li>{@link EditExisting}: re-analysis with negative findings — edit existing comment
 *       (never re-post; diff notes skipped on re-analysis)</li>
 *   <li>{@link EditAllResolved}: re-analysis where all previously negative findings are now
 *       positive — edit existing comment to "all resolved" message</li>
 * </ul>
 *
 * @see PracticeReviewDeliveryGate
 * @see GateDecision
 */
public sealed interface DeliveryDecision
    permits
        DeliveryDecision.StoreOnly,
        DeliveryDecision.PostNew,
        DeliveryDecision.EditExisting,
        DeliveryDecision.EditAllResolved
{
    /**
     * No comment should be posted — findings are stored but delivery is suppressed.
     *
     * @param reason a short, human-readable reason for the skip (for logging/diagnostics)
     */
    record StoreOnly(String reason) implements DeliveryDecision {
        public StoreOnly {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    /**
     * Post a new comment on the PR (first analysis with negative findings).
     *
     * @param pullRequest the resolved pull request to post on
     */
    record PostNew(PullRequest pullRequest) implements DeliveryDecision {
        public PostNew {
            Objects.requireNonNull(pullRequest, "pullRequest must not be null");
        }
    }

    /**
     * Edit an existing comment (re-analysis with negative findings).
     * Diff notes are NOT re-posted on re-analysis — only the summary is updated.
     *
     * @param pullRequest the resolved pull request
     * @param commentId   the existing comment ID to edit
     */
    record EditExisting(PullRequest pullRequest, String commentId) implements DeliveryDecision {
        public EditExisting {
            Objects.requireNonNull(pullRequest, "pullRequest must not be null");
            Objects.requireNonNull(commentId, "commentId must not be null");
        }
    }

    /**
     * Edit an existing comment to an "all resolved" message (re-analysis where
     * all previously negative findings are now positive).
     *
     * @param pullRequest the resolved pull request
     * @param commentId   the existing comment ID to edit
     */
    record EditAllResolved(PullRequest pullRequest, String commentId) implements DeliveryDecision {
        public EditAllResolved {
            Objects.requireNonNull(pullRequest, "pullRequest must not be null");
            Objects.requireNonNull(commentId, "commentId must not be null");
        }
    }
}
