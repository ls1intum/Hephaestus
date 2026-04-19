package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles GitLab MR discussion participation into {@link PullRequestReview} rows
 * with state {@link PullRequestReview.State#COMMENTED}.
 * <p>
 * Unlike GitHub, GitLab has no first-class "review" entity. We derive one COMMENTED
 * review per {@code (author, discussion)} cluster so that leaderboard and profile
 * scoring can attribute inline feedback submissions to a review and stay at parity
 * with GitHub. Approvals are handled separately (see {@code GitLabMergeRequestProcessor}).
 * <p>
 * Idempotency: a deterministic {@code nativeId} is derived by hashing
 * {@code (discussionGlobalId, authorNativeId)}; re-running the sync reuses the same
 * row and updates {@code submittedAt} if an earlier note is discovered.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabReviewReconciler {

    private static final Logger log = LoggerFactory.getLogger(GitLabReviewReconciler.class);

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final PullRequestReviewRepository reviewRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitLabReviewReconciler(
        PullRequestReviewRepository reviewRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.reviewRepository = reviewRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Returns a {@link PullRequestReview} with state COMMENTED for the given
     * {@code (pr, author, discussion)} tuple, creating it when absent and shifting
     * {@code submittedAt} backwards when an earlier note is observed.
     *
     * @param pr the parent pull request
     * @param author the note author (must have a non-null native ID)
     * @param discussionGlobalId the GitLab Discussion GID (hex-hash string)
     * @param earliestNoteCreatedAt earliest non-system note createdAt for this author in the discussion
     * @param provider the GitLab provider
     * @param ctx processing context for event emission (may be null during webhook paths)
     * @return the reconciled review, or {@code null} when inputs are invalid
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PullRequestReview findOrCreateCommentedReview(
        PullRequest pr,
        User author,
        String discussionGlobalId,
        Instant earliestNoteCreatedAt,
        GitProvider provider,
        ProcessingContext ctx
    ) {
        if (pr == null || author == null || author.getNativeId() == null || discussionGlobalId == null) {
            log.warn(
                "Skipped COMMENTED review synthesis: prId={}, authorPresent={}, authorNativeIdPresent={}, discussionPresent={}",
                pr != null ? pr.getId() : null,
                author != null,
                author != null && author.getNativeId() != null,
                discussionGlobalId != null
            );
            return null;
        }

        long reviewNativeId = generateCommentedReviewNativeId(discussionGlobalId, author.getNativeId());
        Long providerId = provider.getId();

        return reviewRepository
            .findByNativeIdAndProviderId(reviewNativeId, providerId)
            .map(existing -> updateReview(existing, earliestNoteCreatedAt, ctx))
            .orElseGet(() -> createReview(reviewNativeId, pr, author, provider, earliestNoteCreatedAt, ctx));
    }

    private PullRequestReview updateReview(
        PullRequestReview existing,
        Instant earliestNoteCreatedAt,
        ProcessingContext ctx
    ) {
        if (earliestNoteCreatedAt != null) {
            Instant currentSubmittedAt = existing.getSubmittedAt();
            if (currentSubmittedAt == null || earliestNoteCreatedAt.isBefore(currentSubmittedAt)) {
                existing.setSubmittedAt(earliestNoteCreatedAt);
                existing.setUpdatedAt(Instant.now());
                reviewRepository.save(existing);
            }
        }

        // Re-publish ReviewSubmitted during re-sync so that COMMENTED reviews synced
        // before the event emission was fixed still get an activity_event row. The
        // activity_event unique constraint on (workspace_id, event_key) dedupes, so
        // replaying is safe.
        if (ctx != null) {
            EventPayload.ReviewData.from(existing).ifPresent(reviewData ->
                eventPublisher.publishEvent(new DomainEvent.ReviewSubmitted(reviewData, EventContext.from(ctx)))
            );
        }
        return existing;
    }

    private PullRequestReview createReview(
        long reviewNativeId,
        PullRequest pr,
        User author,
        GitProvider provider,
        Instant earliestNoteCreatedAt,
        ProcessingContext ctx
    ) {
        Instant submittedAt =
            earliestNoteCreatedAt != null
                ? earliestNoteCreatedAt
                : (pr.getUpdatedAt() != null ? pr.getUpdatedAt() : Instant.now());

        PullRequestReview review = new PullRequestReview();
        review.setNativeId(reviewNativeId);
        review.setProvider(provider);
        review.setState(PullRequestReview.State.COMMENTED);
        review.setHtmlUrl(pr.getHtmlUrl() != null ? pr.getHtmlUrl() + "#discussion" : "");
        review.setSubmittedAt(submittedAt);
        review.setCreatedAt(submittedAt);
        review.setUpdatedAt(Instant.now());
        review.setAuthor(author);
        review.setPullRequest(pr);

        PullRequestReview saved = reviewRepository.save(review);
        pr.addReview(saved);

        if (ctx != null) {
            EventPayload.ReviewData.from(saved).ifPresent(reviewData ->
                eventPublisher.publishEvent(new DomainEvent.ReviewSubmitted(reviewData, EventContext.from(ctx)))
            );
        }

        log.debug(
            "Created COMMENTED review from discussion: prId={}, author={}, nativeId={}",
            pr.getId(),
            author.getLogin(),
            reviewNativeId
        );
        return saved;
    }

    /**
     * Produces a deterministic positive Long native ID for a COMMENTED review.
     * <p>
     * Uses FNV-1a over {@code discussionGlobalId + "|" + authorNativeId}. Collisions
     * with the bit-packed approval native IDs ({@code (mr<<32)|user}) are
     * astronomically unlikely because the two schemes occupy different hash spaces;
     * any collision would surface as a DB unique-constraint violation and is logged.
     */
    public static long generateCommentedReviewNativeId(String discussionGlobalId, long authorNativeId) {
        long hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < discussionGlobalId.length(); i++) {
            hash ^= discussionGlobalId.charAt(i);
            hash *= FNV_PRIME;
        }
        hash ^= '|';
        hash *= FNV_PRIME;
        long mixed = authorNativeId;
        for (int i = 0; i < 8; i++) {
            hash ^= (mixed & 0xFFL);
            hash *= FNV_PRIME;
            mixed >>>= 8;
        }
        return hash & Long.MAX_VALUE;
    }
}
