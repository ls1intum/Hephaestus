package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.github.BaseGitHubProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub pull request reviews.
 * <p>
 * This service handles the conversion of GitHubReviewDTO to PullRequestReview entities,
 * persists them, and publishes appropriate domain events.
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations via upsert pattern</li>
 * <li>Domain events published for reactive feature development</li>
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * </ul>
 */
@Service
public class GitHubPullRequestReviewProcessor extends BaseGitHubProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestReviewProcessor.class);

    private final PullRequestReviewRepository reviewRepository;
    private final PullRequestRepository prRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubPullRequestReviewProcessor(
        PullRequestReviewRepository reviewRepository,
        PullRequestRepository prRepository,
        UserRepository userRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        super(userRepository, null, null);
        this.reviewRepository = reviewRepository;
        this.prRepository = prRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub review DTO and persist it as a PullRequestReview entity.
     * <p>
     * This overload requires the parent PR to already exist in the database.
     * For webhook processing where the parent might not exist, use
     * {@link #processWithParentCreation(GitHubPullRequestReviewEventDTO.GitHubReviewDTO, GitHubPullRequestDTO, ProcessingContext)} instead.
     *
     * @param dto the GitHub review DTO
     * @param prId the database ID of the pull request this review belongs to
     * @param context processing context with scope information
     * @return the persisted PullRequestReview entity, or null if processing failed
     */
    @Transactional
    public PullRequestReview process(
        GitHubPullRequestReviewEventDTO.GitHubReviewDTO dto,
        Long prId,
        @NonNull ProcessingContext context
    ) {
        if (dto == null || dto.id() == null) {
            log.warn("Skipped review processing: reason=nullOrMissingId");
            return null;
        }

        PullRequest pr = prRepository.findById(prId).orElse(null);
        if (pr == null) {
            log.warn("Skipped review processing: reason=prNotFound, prId={}, reviewId={}", prId, dto.id());
            return null;
        }

        return processReviewInternal(dto, pr, context);
    }

    /**
     * Process a GitHub review DTO and persist it as a PullRequestReview entity,
     * creating a minimal parent PR entity if it doesn't exist.
     * <p>
     * This method solves the message ordering problem where review webhooks arrive
     * before the parent PR webhook. Instead of failing and retrying, we create
     * a minimal parent entity from the webhook's embedded PR data. The full entity
     * will be updated later when the proper PR webhook arrives or during GraphQL sync.
     * <p>
     * <b>Why this approach?</b>
     * <ul>
     *   <li>GitHub sends pull_request_review events with embedded PR data</li>
     *   <li>The webhook includes PR id, number, title, body, state, etc.</li>
     *   <li>Creating a stub entity is better than losing data or retrying indefinitely</li>
     *   <li>The stub will be hydrated by the PR webhook or scheduled sync</li>
     * </ul>
     *
     * @param dto the GitHub review DTO
     * @param prDto the GitHub PR DTO from the webhook (contains parent entity data)
     * @param context processing context with scope information
     * @return the persisted PullRequestReview entity, or null if processing failed
     */
    @Transactional
    public PullRequestReview processWithParentCreation(
        GitHubPullRequestReviewEventDTO.GitHubReviewDTO dto,
        GitHubPullRequestDTO prDto,
        @NonNull ProcessingContext context
    ) {
        if (dto == null || dto.id() == null) {
            log.warn("Skipped review processing: reason=nullOrMissingId");
            return null;
        }

        if (context.repository() == null) {
            log.warn("Skipped review processing: reason=missingRepository, reviewId={}", dto.id());
            return null;
        }

        // Use repository ID + PR number for lookup - these are stable across GraphQL and webhooks.
        // Do NOT use prDto.getDatabaseId() as the ID format may differ between API sources.
        PullRequest pr = prRepository
            .findByRepositoryIdAndNumber(context.repository().getId(), prDto.number())
            .orElse(null);

        // If parent doesn't exist, create a minimal entity from webhook data
        if (pr == null) {
            pr = createMinimalPullRequest(prDto, context);
            if (pr == null) {
                log.warn(
                    "Skipped review processing: reason=failedToCreateParent, prNumber={}, reviewId={}",
                    prDto.number(),
                    dto.id()
                );
                return null;
            }
        }

        return processReviewInternal(dto, pr, context);
    }

    /**
     * Internal method that processes a review given a resolved parent PR.
     */
    private PullRequestReview processReviewInternal(
        GitHubPullRequestReviewEventDTO.GitHubReviewDTO dto,
        PullRequest pr,
        @NonNull ProcessingContext context
    ) {
        return reviewRepository
            .findById(dto.id())
            .map(review -> updateReview(review, dto, context))
            .orElseGet(() -> createReview(dto, pr, context));
    }

    /**
     * Process a review dismissal.
     *
     * @param reviewId the ID of the review to dismiss
     * @param context processing context with scope information
     */
    @Transactional
    public void processDismissed(Long reviewId, @NonNull ProcessingContext context) {
        reviewRepository
            .findById(reviewId)
            .ifPresent(review -> {
                review.setDismissed(true);
                review = reviewRepository.save(review);
                EventPayload.ReviewData.from(review).ifPresent(reviewData ->
                    eventPublisher.publishEvent(new DomainEvent.ReviewDismissed(reviewData, EventContext.from(context)))
                );
                log.debug("Dismissed review: reviewId={}", reviewId);
            });
    }

    private PullRequestReview updateReview(
        PullRequestReview review,
        GitHubPullRequestReviewEventDTO.GitHubReviewDTO dto,
        @NonNull ProcessingContext context
    ) {
        review.setBody(dto.body());
        if (dto.state() != null) {
            PullRequestReview.State newState = mapState(dto.state());
            if (newState == PullRequestReview.State.DISMISSED) {
                // Don't overwrite original state, just mark as dismissed.
                // The original state (e.g., APPROVED) should be preserved.
                review.setDismissed(true);
                // Only set state to DISMISSED if we don't have an original state
                if (review.getState() == null) {
                    review.setState(newState);
                }
            } else {
                review.setState(newState);
                review.setDismissed(false);
            }
        }
        if (dto.commitId() != null) {
            review.setCommitId(dto.commitId());
        }
        PullRequestReview saved = reviewRepository.save(review);
        EventPayload.ReviewData.from(saved).ifPresent(reviewData ->
            eventPublisher.publishEvent(
                new DomainEvent.ReviewEdited(reviewData, Set.of("body", "state"), EventContext.from(context))
            )
        );
        log.debug("Updated review: reviewId={}", dto.id());
        return saved;
    }

    private PullRequestReview createReview(
        GitHubPullRequestReviewEventDTO.GitHubReviewDTO dto,
        PullRequest pr,
        @NonNull ProcessingContext context
    ) {
        PullRequestReview review = new PullRequestReview();
        review.setId(dto.id());
        review.setBody(dto.body());
        PullRequestReview.State newState = mapState(dto.state());
        if (newState == PullRequestReview.State.DISMISSED) {
            // When creating a review that's already dismissed, we don't know the original state.
            // Set state to DISMISSED and mark as dismissed. This is a best-effort for historical data.
            review.setState(newState);
            review.setDismissed(true);
        } else {
            review.setState(newState);
            review.setDismissed(false);
        }
        // Use submittedAt from DTO, fallback to PR createdAt (review can't predate PR)
        review.setSubmittedAt(dto.submittedAt() != null ? dto.submittedAt() : pr.getCreatedAt());
        review.setHtmlUrl(dto.htmlUrl() != null ? dto.htmlUrl() : "");
        review.setPullRequest(pr);
        review.setCommitId(dto.commitId());

        if (dto.author() != null) {
            User author = findOrCreateUser(dto.author());
            if (author != null) {
                review.setAuthor(author);
            }
        }

        PullRequestReview saved = reviewRepository.save(review);
        var reviewDataOpt = EventPayload.ReviewData.from(saved);
        if (reviewDataOpt.isPresent()) {
            var reviewData = reviewDataOpt.get();
            log.info(
                "Publishing ReviewSubmitted event: reviewId={}, state={}, scopeId={}, authorId={}, repositoryId={}",
                reviewData.id(),
                reviewData.state(),
                context.scopeId(),
                reviewData.authorId(),
                reviewData.repositoryId()
            );
            eventPublisher.publishEvent(new DomainEvent.ReviewSubmitted(reviewData, EventContext.from(context)));
        } else {
            log.warn(
                "ReviewData.from() returned empty - no event published: reviewId={}, prId={}, prIsNull={}",
                saved.getId(),
                saved.getPullRequest() != null ? saved.getPullRequest().getId() : "null",
                saved.getPullRequest() == null
            );
        }
        log.debug("Created review: reviewId={}", dto.id());
        return saved;
    }

    private PullRequestReview.State mapState(String state) {
        if (state == null) {
            log.warn(
                "Review state is null, using UNKNOWN. This may indicate missing data in webhook or GraphQL response."
            );
            return PullRequestReview.State.UNKNOWN;
        }
        return switch (state.toUpperCase()) {
            case "APPROVED" -> PullRequestReview.State.APPROVED;
            case "CHANGES_REQUESTED" -> PullRequestReview.State.CHANGES_REQUESTED;
            case "COMMENTED" -> PullRequestReview.State.COMMENTED;
            case "PENDING" -> PullRequestReview.State.PENDING;
            case "DISMISSED" -> PullRequestReview.State.DISMISSED;
            default -> {
                log.warn("Unknown review state '{}', using UNKNOWN", state);
                yield PullRequestReview.State.UNKNOWN;
            }
        };
    }

    // ==================== Private Helper Methods ====================

    /**
     * Creates a minimal PullRequest entity from webhook data.
     * <p>
     * This method is called when a review webhook arrives before the parent PR exists.
     * We create a "stub" entity with the data available in the webhook payload. The full
     * entity will be populated later by the proper PR webhook or GraphQL sync.
     * <p>
     * <b>Hydration Strategy:</b> Stubs rely on natural hydration through:
     * <ol>
     *   <li>The pull_request webhook (typically arrives within seconds)</li>
     *   <li>The scheduled GraphQL sync (safety net for missed webhooks)</li>
     * </ol>
     *
     * @param dto the PR DTO from the webhook payload
     * @param context the processing context with repository information
     * @return the created PullRequest entity, or null if creation failed
     */
    @Nullable
    private PullRequest createMinimalPullRequest(GitHubPullRequestDTO dto, ProcessingContext context) {
        Repository repository = context.repository();
        if (repository == null) {
            log.warn("Cannot create parent PR: reason=noRepository, prId={}", dto.getDatabaseId());
            return null;
        }

        Long prId = dto.getDatabaseId();
        if (prId == null) {
            return null;
        }

        PullRequest pr = new PullRequest();
        pr.setId(prId);
        pr.setNumber(dto.number());
        pr.setTitle(sanitize(dto.title()));
        pr.setBody(sanitize(dto.body()));
        pr.setState(convertState(dto.state()));
        pr.setHtmlUrl(dto.htmlUrl());
        pr.setCreatedAt(dto.createdAt());
        pr.setUpdatedAt(dto.updatedAt());
        pr.setClosedAt(dto.closedAt());
        pr.setMergedAt(dto.mergedAt());
        pr.setDraft(dto.isDraft());
        pr.setMerged(dto.isMerged());
        pr.setLocked(dto.locked());
        pr.setAdditions(dto.additions());
        pr.setDeletions(dto.deletions());
        pr.setChangedFiles(dto.changedFiles());
        pr.setCommits(dto.commits());
        pr.setCommentsCount(dto.commentsCount());
        pr.setRepository(repository);
        pr.setLastSyncAt(Instant.now());

        // Head/base branch references
        if (dto.head() != null) {
            pr.setHeadRefName(dto.head().ref());
            pr.setHeadRefOid(dto.head().sha());
        }
        if (dto.base() != null) {
            pr.setBaseRefName(dto.base().ref());
            pr.setBaseRefOid(dto.base().sha());
        }

        // Link author
        if (dto.author() != null) {
            User author = findOrCreateUser(dto.author());
            pr.setAuthor(author);
        }

        PullRequest saved = prRepository.save(pr);
        log.info(
            "Created stub PullRequest from review webhook (will be hydrated by PR webhook or sync): " +
                "prId={}, prNumber={}, repoName={}",
            saved.getId(),
            saved.getNumber(),
            repository.getNameWithOwner()
        );
        return saved;
    }

    /**
     * Converts a GitHub API state string to Issue.State enum.
     */
    private de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State convertState(String state) {
        if (state == null) {
            log.warn(
                "PR state is null when creating stub from review webhook, defaulting to OPEN. " +
                    "This may indicate missing data in webhook payload."
            );
            return de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.OPEN;
        }
        return switch (state.toUpperCase()) {
            case "OPEN" -> de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.OPEN;
            case "CLOSED" -> de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.CLOSED;
            case "MERGED" -> de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.MERGED;
            default -> {
                log.warn("Unknown PR state '{}' when creating stub from review webhook, defaulting to OPEN", state);
                yield de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.OPEN;
            }
        };
    }
}
