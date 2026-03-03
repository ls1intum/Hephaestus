package de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.BaseGitLabProcessor;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookUser;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RepositoryScopeFilter;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab.dto.GitLabMergeRequestEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitLab merge requests.
 * <p>
 * Handles conversion of GitLab MR data (from webhooks and GraphQL sync) to PullRequest entities.
 * Follows the same patterns as {@link de.tum.in.www1.hephaestus.gitprovider.issue.gitlab.GitLabIssueProcessor}.
 * <p>
 * GitLab approvals are mapped to PullRequestReview entities with state APPROVED.
 * Deterministic review IDs prevent collisions with GitHub review IDs.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabMergeRequestProcessor extends BaseGitLabProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitLabMergeRequestProcessor.class);

    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewRepository reviewRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitLabMergeRequestProcessor(
        PullRequestRepository pullRequestRepository,
        PullRequestReviewRepository reviewRepository,
        UserRepository userRepository,
        LabelRepository labelRepository,
        RepositoryRepository repositoryRepository,
        ScopeIdResolver scopeIdResolver,
        RepositoryScopeFilter repositoryScopeFilter,
        GitLabProperties gitLabProperties,
        ApplicationEventPublisher eventPublisher
    ) {
        super(
            userRepository,
            labelRepository,
            repositoryRepository,
            scopeIdResolver,
            repositoryScopeFilter,
            gitLabProperties
        );
        this.pullRequestRepository = pullRequestRepository;
        this.reviewRepository = reviewRepository;
        this.eventPublisher = eventPublisher;
    }

    // ========================================================================
    // Sync Data Records
    // ========================================================================

    public record SyncLabelData(String globalId, String title, @Nullable String color) {}

    /** Shared record for user references in sync data (assignees, reviewers, approvers). */
    public record SyncUserData(
        String globalId,
        String username,
        @Nullable String name,
        @Nullable String avatarUrl,
        @Nullable String webUrl
    ) {}

    public record SyncMergeRequestData(
        String globalId,
        String iid,
        String title,
        @Nullable String description,
        String state,
        boolean draft,
        @Nullable Boolean mergeable,
        @Nullable String detailedMergeStatus,
        boolean approved,
        String webUrl,
        @Nullable String createdAt,
        @Nullable String updatedAt,
        @Nullable String closedAt,
        @Nullable String mergedAt,
        int commitCount,
        int additions,
        int deletions,
        int fileCount,
        String sourceBranch,
        String targetBranch,
        @Nullable String diffHeadSha,
        @Nullable String baseSha,
        boolean discussionLocked,
        int commentsCount,
        @Nullable String authorGlobalId,
        @Nullable String authorUsername,
        @Nullable String authorName,
        @Nullable String authorAvatarUrl,
        @Nullable String authorWebUrl,
        @Nullable String mergeUserGlobalId,
        @Nullable String mergeUserUsername,
        @Nullable String mergeUserName,
        @Nullable String mergeUserAvatarUrl,
        @Nullable String mergeUserWebUrl,
        @Nullable List<SyncLabelData> syncLabels,
        @Nullable List<SyncUserData> syncAssignees,
        @Nullable List<SyncUserData> syncReviewers,
        @Nullable List<SyncUserData> syncApprovers
    ) {}

    // ========================================================================
    // Webhook Processing
    // ========================================================================

    /**
     * Process a GitLab merge request webhook event (open/update).
     * <p>
     * Returns the existing entity unchanged if the webhook is stale (event's
     * {@code updatedAt} is not newer than the stored value). This allows callers
     * to still publish lifecycle events while preventing stale data from
     * overwriting newer sync data or M:N relationships.
     */
    @Transactional
    @Nullable
    public PullRequest process(GitLabMergeRequestEventDTO event, ProcessingContext context) {
        if (event.isConfidential()) {
            log.debug("Skipped confidential merge request: iid={}", event.objectAttributes().iid());
            return null;
        }

        var attrs = event.objectAttributes();

        // Stale webhook detection BEFORE upsert to avoid data regression.
        // Returns existing entity so callers can still publish lifecycle events.
        // Also determines isNew to avoid a redundant query inside upsertMergeRequest.
        boolean isNew = true;
        if (attrs.iid() != null) {
            Optional<PullRequest> existingOpt = pullRequestRepository.findByRepositoryIdAndNumber(
                context.repository().getId(),
                attrs.iid()
            );
            if (existingOpt.isPresent()) {
                isNew = false;
                PullRequest existing = existingOpt.get();
                Instant eventUpdatedAt = parseGitLabTimestamp(attrs.updatedAt());
                if (
                    existing.getUpdatedAt() != null &&
                    eventUpdatedAt != null &&
                    !eventUpdatedAt.isAfter(existing.getUpdatedAt())
                ) {
                    log.debug(
                        "Skipped stale MR webhook: nativeId={}, existingUpdatedAt={}, eventUpdatedAt={}",
                        attrs.id(),
                        existing.getUpdatedAt(),
                        eventUpdatedAt
                    );
                    return existing;
                }
            }
        }

        User author = resolveWebhookAuthor(event, context.providerId());
        User mergedBy = resolveWebhookMergeUser(event, context.providerId());

        PullRequest pr = upsertMergeRequest(
            attrs.id(),
            attrs.iid(),
            attrs.title(),
            attrs.description(),
            attrs.state(),
            attrs.sourceBranch(),
            attrs.targetBranch(),
            attrs.draft(),
            attrs.url(),
            attrs.createdAt(),
            attrs.updatedAt(),
            attrs.closedAt(),
            attrs.mergedAt(),
            author,
            mergedBy,
            context.repository(),
            context,
            isNew
        );

        if (pr == null) return null;

        boolean changed = updateLabels(event.labels(), pr.getLabels(), context.repository());
        changed |= updateAssignees(event.assignees(), pr.getAssignees(), context.providerId());
        changed |= updateRequestedReviewers(event.reviewers(), pr.getRequestedReviewers(), context.providerId());
        if (changed) {
            pr = pullRequestRepository.save(pr);
        }

        return pr;
    }

    /**
     * Process a closed event (not merged).
     */
    @Transactional
    @Nullable
    public PullRequest processClosed(GitLabMergeRequestEventDTO event, ProcessingContext context) {
        Issue.State before = getExistingState(event, context);
        PullRequest pr = process(event, context);
        if (pr != null && before != Issue.State.CLOSED && before != Issue.State.MERGED) {
            eventPublisher.publishEvent(
                new DomainEvent.PullRequestClosed(
                    EventPayload.PullRequestData.from(pr),
                    false,
                    EventContext.from(context)
                )
            );
            log.debug("Closed merge request: prId={}", pr.getId());
        }
        return pr;
    }

    /**
     * Process a reopened event.
     */
    @Transactional
    @Nullable
    public PullRequest processReopened(GitLabMergeRequestEventDTO event, ProcessingContext context) {
        Issue.State before = getExistingState(event, context);
        PullRequest pr = process(event, context);
        if (pr != null && before != Issue.State.OPEN) {
            eventPublisher.publishEvent(
                new DomainEvent.PullRequestReopened(EventPayload.PullRequestData.from(pr), EventContext.from(context))
            );
            log.debug("Reopened merge request: prId={}", pr.getId());
        }
        return pr;
    }

    /**
     * Process a merged event.
     */
    @Transactional
    @Nullable
    public PullRequest processMerged(GitLabMergeRequestEventDTO event, ProcessingContext context) {
        Issue.State before = getExistingState(event, context);
        PullRequest pr = process(event, context);
        if (pr != null && before != Issue.State.MERGED) {
            var prData = EventPayload.PullRequestData.from(pr);
            if (before != Issue.State.CLOSED) {
                eventPublisher.publishEvent(
                    new DomainEvent.PullRequestClosed(prData, true, EventContext.from(context))
                );
            }
            eventPublisher.publishEvent(new DomainEvent.PullRequestMerged(prData, EventContext.from(context)));
            log.debug("Merged merge request: prId={}", pr.getId());
        }
        return pr;
    }

    /**
     * Process an approval event.
     */
    @Transactional
    @Nullable
    public PullRequest processApproved(GitLabMergeRequestEventDTO event, ProcessingContext context) {
        PullRequest pr = process(event, context);
        if (pr == null || event.user() == null) return pr;

        User approver = findOrCreateUser(event.user(), context.providerId());
        if (approver == null) return pr;

        long reviewId = generateApprovalReviewId(pr.getNativeId(), approver.getNativeId());
        if (reviewRepository.findById(reviewId).isEmpty()) {
            PullRequestReview review = createApprovalReview(reviewId, pr, approver);
            reviewRepository.save(review);
            pr.addReview(review);

            EventPayload.ReviewData.from(review).ifPresent(reviewData ->
                eventPublisher.publishEvent(new DomainEvent.ReviewSubmitted(reviewData, EventContext.from(context)))
            );
            log.debug("Created approval review: prId={}, reviewerId={}", pr.getId(), approver.getLogin());
        }

        return pr;
    }

    /**
     * Process an unapproval event.
     */
    @Transactional
    @Nullable
    public PullRequest processUnapproved(GitLabMergeRequestEventDTO event, ProcessingContext context) {
        PullRequest pr = process(event, context);
        if (pr == null || event.user() == null) return pr;

        User approver = findOrCreateUser(event.user(), context.providerId());
        if (approver == null) return pr;

        long reviewId = generateApprovalReviewId(pr.getNativeId(), approver.getNativeId());
        reviewRepository
            .findById(reviewId)
            .ifPresent(review -> {
                // Capture event payload BEFORE removeReview() nullifies the PR back-reference
                var reviewData = EventPayload.ReviewData.from(review);
                pr.removeReview(review);
                reviewRepository.delete(review);
                reviewData.ifPresent(data ->
                    eventPublisher.publishEvent(new DomainEvent.ReviewDismissed(data, EventContext.from(context)))
                );
                log.debug("Removed approval review: prId={}, reviewerId={}", pr.getId(), approver.getLogin());
            });

        return pr;
    }

    // ========================================================================
    // Sync Processing
    // ========================================================================

    /**
     * Looks up the current state of an existing PR before processing a webhook event.
     * Returns null if the PR does not exist yet.
     */
    @Nullable
    private Issue.State getExistingState(GitLabMergeRequestEventDTO event, ProcessingContext context) {
        if (event.objectAttributes() == null || event.objectAttributes().iid() == null) {
            return null;
        }
        return pullRequestRepository
            .findByRepositoryIdAndNumber(context.repository().getId(), event.objectAttributes().iid())
            .map(PullRequest::getState)
            .orElse(null);
    }

    /**
     * Process a GitLab merge request from GraphQL sync.
     */
    @Transactional
    @Nullable
    public PullRequest processFromSync(SyncMergeRequestData data, Repository repository, @Nullable Long scopeId) {
        long nativeId;
        try {
            nativeId = GitLabSyncConstants.extractNumericId(data.globalId());
        } catch (IllegalArgumentException e) {
            log.warn("Skipped MR processing: reason=invalidGlobalId, gid={}", data.globalId());
            return null;
        }

        int mrNumber;
        try {
            mrNumber = Integer.parseInt(data.iid());
        } catch (NumberFormatException e) {
            log.warn("Skipped MR processing: reason=invalidIid, iid={}", data.iid());
            return null;
        }

        Long providerId = repository.getProvider().getId();

        Optional<PullRequest> existingOpt = pullRequestRepository.findByRepositoryIdAndNumber(
            repository.getId(),
            mrNumber
        );
        boolean isNew = existingOpt.isEmpty();

        User author = findOrCreateUser(
            data.authorGlobalId(),
            data.authorUsername(),
            data.authorName(),
            data.authorAvatarUrl(),
            data.authorWebUrl(),
            providerId
        );

        User mergeUser = findOrCreateUser(
            data.mergeUserGlobalId(),
            data.mergeUserUsername(),
            data.mergeUserName(),
            data.mergeUserAvatarUrl(),
            data.mergeUserWebUrl(),
            providerId
        );

        Issue.State mrState = convertState(data.state());
        boolean isMerged = "merged".equalsIgnoreCase(data.state());
        String reviewDecision = data.approved() ? "APPROVED" : "REVIEW_REQUIRED";
        String mergeStateStatus = mapDetailedMergeStatus(data.detailedMergeStatus());

        Instant now = Instant.now();
        pullRequestRepository.upsertCore(
            nativeId,
            providerId,
            mrNumber,
            sanitize(data.title()),
            sanitize(data.description()),
            mrState.name(),
            null, // stateReason
            data.webUrl(),
            data.discussionLocked(),
            parseGitLabTimestamp(data.closedAt()),
            data.commentsCount(),
            now,
            parseGitLabTimestamp(data.createdAt()),
            parseGitLabTimestamp(data.updatedAt()),
            author != null ? author.getId() : null,
            repository.getId(),
            null, // milestoneId
            parseGitLabTimestamp(data.mergedAt()),
            data.draft(),
            isMerged,
            data.commitCount(),
            data.additions(),
            data.deletions(),
            data.fileCount(),
            reviewDecision,
            mergeStateStatus,
            data.mergeable(),
            data.sourceBranch(),
            data.targetBranch(),
            data.diffHeadSha(),
            data.baseSha(),
            mergeUser != null ? mergeUser.getId() : null
        );

        PullRequest pr = pullRequestRepository
            .findByRepositoryIdAndNumber(repository.getId(), mrNumber)
            .orElseThrow(() ->
                new IllegalStateException(
                    "PullRequest not found after upsert: nativeId=" + nativeId + ", iid=" + data.iid()
                )
            );

        pr.setProvider(repository.getProvider());

        boolean changed = updateSyncLabels(data.syncLabels(), pr.getLabels(), repository);
        changed |= updateSyncAssignees(data.syncAssignees(), pr.getAssignees(), providerId);
        changed |= updateSyncReviewers(data.syncReviewers(), pr.getRequestedReviewers(), providerId);
        if (changed) {
            pr = pullRequestRepository.save(pr);
        }

        // Reconcile approvals (needs ctx for activity event emission)
        ProcessingContext ctx = ProcessingContext.forSync(scopeId, repository);
        reconcileApprovals(data.syncApprovers(), pr, providerId, ctx);
        var prData = EventPayload.PullRequestData.from(pr);
        var eventCtx = EventContext.from(ctx);

        if (isNew) {
            eventPublisher.publishEvent(new DomainEvent.PullRequestCreated(prData, eventCtx));

            // Emit lifecycle events for MRs that are already in a terminal state
            // when first seen during sync (e.g. historical merged/closed MRs).
            if (isMerged) {
                eventPublisher.publishEvent(new DomainEvent.PullRequestClosed(prData, true, eventCtx));
                eventPublisher.publishEvent(new DomainEvent.PullRequestMerged(prData, eventCtx));
            } else if (mrState == Issue.State.CLOSED) {
                eventPublisher.publishEvent(new DomainEvent.PullRequestClosed(prData, false, eventCtx));
            }

            log.debug("Created merge request from sync: nativeId={}, iid={}", nativeId, data.iid());
        } else {
            eventPublisher.publishEvent(new DomainEvent.PullRequestUpdated(prData, Set.of(), eventCtx));
            log.debug("Updated merge request from sync: nativeId={}, iid={}", nativeId, data.iid());
        }

        return pr;
    }

    // ========================================================================
    // Private Helpers
    // ========================================================================

    @Nullable
    private User resolveWebhookAuthor(GitLabMergeRequestEventDTO event, Long providerId) {
        Long authorId = event.objectAttributes().authorId();
        GitLabWebhookUser eventUser = event.user();

        // If the event user IS the author, use the webhook user data to upsert
        if (eventUser != null && authorId != null && authorId.equals(eventUser.id())) {
            return findOrCreateUser(eventUser, providerId);
        }

        // Otherwise, look up by authorId if available
        if (authorId != null) {
            return userRepository.findByNativeIdAndProviderId(authorId, providerId).orElse(null);
        }

        // Fallback: try the event user
        return findOrCreateUser(eventUser, providerId);
    }

    @Nullable
    private User resolveWebhookMergeUser(GitLabMergeRequestEventDTO event, Long providerId) {
        Long mergeUserId = event.objectAttributes().mergeUserId();
        if (mergeUserId == null) return null;

        if (event.user() != null && mergeUserId.equals(event.user().id())) {
            return findOrCreateUser(event.user(), providerId);
        }

        return userRepository.findByNativeIdAndProviderId(mergeUserId, providerId).orElse(null);
    }

    @Nullable
    private PullRequest upsertMergeRequest(
        Long rawId,
        Integer iid,
        String title,
        @Nullable String description,
        String state,
        @Nullable String sourceBranch,
        @Nullable String targetBranch,
        boolean draft,
        @Nullable String htmlUrl,
        @Nullable String createdAt,
        @Nullable String updatedAt,
        @Nullable String closedAt,
        @Nullable String mergedAt,
        @Nullable User author,
        @Nullable User mergedBy,
        Repository repository,
        ProcessingContext context,
        boolean isNew
    ) {
        if (rawId == null || iid == null) {
            log.warn("Skipped MR processing: reason=missingIdOrIid");
            return null;
        }

        long nativeId = rawId;
        int mrNumber = iid;
        Long providerId = repository.getProvider().getId();

        Issue.State mrState = convertState(state);
        boolean isMerged = mrState == Issue.State.MERGED;

        Instant now = Instant.now();
        pullRequestRepository.upsertCore(
            nativeId,
            providerId,
            mrNumber,
            sanitize(title),
            sanitize(description),
            mrState.name(),
            null,
            htmlUrl,
            null, // isLocked: not in webhook — null lets COALESCE preserve existing or default
            parseGitLabTimestamp(closedAt),
            null, // commentsCount: not in webhook — null lets COALESCE preserve existing or default
            now, // lastSyncAt
            parseGitLabTimestamp(createdAt),
            parseGitLabTimestamp(updatedAt),
            author != null ? author.getId() : null,
            repository.getId(),
            null,
            parseGitLabTimestamp(mergedAt),
            draft,
            isMerged,
            null,
            null,
            null,
            null, // commits, additions, deletions, changedFiles — not in webhook, null preserves existing
            null,
            null,
            null, // reviewDecision, mergeStateStatus, mergeable — not in webhook
            sourceBranch,
            targetBranch,
            null,
            null, // headRefOid, baseRefOid — not in webhook
            mergedBy != null ? mergedBy.getId() : null
        );

        PullRequest pr = pullRequestRepository
            .findByRepositoryIdAndNumber(repository.getId(), mrNumber)
            .orElseThrow(() ->
                new IllegalStateException(
                    "PullRequest not found after upsert: nativeId=" + nativeId + ", number=" + mrNumber
                )
            );

        pr.setProvider(repository.getProvider());

        if (isNew) {
            eventPublisher.publishEvent(
                new DomainEvent.PullRequestCreated(EventPayload.PullRequestData.from(pr), EventContext.from(context))
            );
            log.debug("Created merge request: nativeId={}, iid={}", nativeId, mrNumber);
        } else {
            eventPublisher.publishEvent(
                new DomainEvent.PullRequestUpdated(
                    EventPayload.PullRequestData.from(pr),
                    Set.of(),
                    EventContext.from(context)
                )
            );
            log.debug("Updated merge request: nativeId={}, iid={}", nativeId, mrNumber);
        }

        return pr;
    }

    private static Issue.State convertState(@Nullable String state) {
        if (state == null) return Issue.State.OPEN;
        return switch (state.toLowerCase()) {
            case "opened" -> Issue.State.OPEN;
            case "closed" -> Issue.State.CLOSED;
            case "merged" -> Issue.State.MERGED;
            case "locked" -> Issue.State.CLOSED;
            default -> {
                log.warn("Unknown GitLab MR state '{}', defaulting to OPEN", state);
                yield Issue.State.OPEN;
            }
        };
    }

    @Nullable
    private static String mapDetailedMergeStatus(@Nullable String detailedStatus) {
        if (detailedStatus == null) return null;
        return switch (detailedStatus.toLowerCase()) {
            case "mergeable" -> "CLEAN";
            case "broken_status", "ci_must_pass", "ci_still_running" -> "UNSTABLE";
            case "checking" -> "UNKNOWN";
            case "conflict", "need_rebase" -> "DIRTY";
            case "not_approved", "blocked_status", "policies_denied" -> "BLOCKED";
            case "not_open" -> "BEHIND";
            default -> "UNKNOWN";
        };
    }

    /**
     * Generates a deterministic negative review ID for a GitLab approval.
     * <p>
     * Layout: {@code [sign=1][mrNativeId (31 bits)][userNativeId (32 bits)]}.
     * Forces the sign bit via {@code | Long.MIN_VALUE}, guaranteeing a negative ID
     * that cannot collide with GitHub's positive review IDs.
     * <p>
     * Collision-free when MR native IDs fit in 31 bits and user native IDs fit in 32 bits.
     * When either exceeds its range, collisions become possible due to bit truncation,
     * and a warning is logged.
     */
    static long generateApprovalReviewId(long mrNativeId, long userNativeId) {
        if ((mrNativeId >>> 31) != 0 || (userNativeId >>> 32) != 0) {
            log.warn(
                "Native IDs exceed safe range, review ID may collide: mrNativeId={}, userNativeId={}",
                mrNativeId,
                userNativeId
            );
        }
        return ((mrNativeId << 32) | (userNativeId & 0xFFFFFFFFL)) | Long.MIN_VALUE;
    }

    private PullRequestReview createApprovalReview(long reviewId, PullRequest pr, User approver) {
        PullRequestReview review = new PullRequestReview();
        review.setId(reviewId);
        review.setState(PullRequestReview.State.APPROVED);
        review.setHtmlUrl(pr.getHtmlUrl() + "#approvals");
        review.setSubmittedAt(pr.getUpdatedAt() != null ? pr.getUpdatedAt() : Instant.now());
        review.setCreatedAt(pr.getUpdatedAt() != null ? pr.getUpdatedAt() : Instant.now());
        review.setUpdatedAt(Instant.now());
        review.setAuthor(approver);
        review.setPullRequest(pr);
        return review;
    }

    private void reconcileApprovals(
        @Nullable List<SyncUserData> syncApprovers,
        PullRequest pr,
        Long providerId,
        @Nullable ProcessingContext ctx
    ) {
        if (syncApprovers == null) return;

        // Pre-load existing review IDs in memory to avoid N+1 findById calls
        Set<Long> existingReviewIds = pr
            .getReviews()
            .stream()
            .map(PullRequestReview::getId)
            .collect(Collectors.toSet());

        Set<Long> expectedReviewIds = new HashSet<>();

        for (SyncUserData approver : syncApprovers) {
            User user = findOrCreateUser(
                approver.globalId(),
                approver.username(),
                approver.name(),
                approver.avatarUrl(),
                approver.webUrl(),
                providerId
            );
            if (user == null) continue;

            long reviewId = generateApprovalReviewId(pr.getNativeId(), user.getNativeId());
            expectedReviewIds.add(reviewId);

            if (!existingReviewIds.contains(reviewId)) {
                PullRequestReview review = createApprovalReview(reviewId, pr, user);
                reviewRepository.save(review);
                pr.addReview(review);
                log.debug("Created approval review from sync: prId={}, reviewerId={}", pr.getId(), user.getLogin());

                // Emit activity event for the new approval
                if (ctx != null) {
                    EventPayload.ReviewData.from(review).ifPresent(reviewData ->
                        eventPublisher.publishEvent(new DomainEvent.ReviewSubmitted(reviewData, EventContext.from(ctx)))
                    );
                }
            }
        }

        // Remove stale approval reviews (user no longer in approvedBy)
        Set<PullRequestReview> staleReviews = pr
            .getReviews()
            .stream()
            .filter(r -> r.getState() == PullRequestReview.State.APPROVED)
            .filter(r -> r.getId() < 0) // Only GitLab-generated reviews (negative IDs)
            .filter(r -> !expectedReviewIds.contains(r.getId()))
            .collect(Collectors.toSet());

        for (PullRequestReview stale : staleReviews) {
            pr.removeReview(stale);
            reviewRepository.delete(stale);
            log.debug("Removed stale approval review from sync: prId={}, reviewId={}", pr.getId(), stale.getId());
        }
    }

    private boolean updateRequestedReviewers(
        @Nullable List<GitLabWebhookUser> reviewerDtos,
        Set<User> currentReviewers,
        Long providerId
    ) {
        if (reviewerDtos == null) return false;

        Set<User> newReviewers = new HashSet<>();
        for (var dto : reviewerDtos) {
            User user = findOrCreateUser(dto, providerId);
            if (user != null) newReviewers.add(user);
        }

        if (!currentReviewers.equals(newReviewers)) {
            currentReviewers.clear();
            currentReviewers.addAll(newReviewers);
            return true;
        }
        return false;
    }

    private boolean updateSyncLabels(
        @Nullable List<SyncLabelData> syncLabels,
        Collection<Label> currentLabels,
        Repository repository
    ) {
        if (syncLabels == null) return false;

        Set<Label> newLabels = new HashSet<>();
        for (SyncLabelData data : syncLabels) {
            Label label = findOrCreateLabel(data.title(), data.color(), repository);
            if (label != null) newLabels.add(label);
        }

        if (!new HashSet<>(currentLabels).equals(newLabels)) {
            currentLabels.clear();
            currentLabels.addAll(newLabels);
            return true;
        }
        return false;
    }

    private boolean updateSyncAssignees(
        @Nullable List<SyncUserData> syncAssignees,
        Set<User> currentAssignees,
        Long providerId
    ) {
        if (syncAssignees == null) return false;

        Set<User> newAssignees = new HashSet<>();
        for (SyncUserData data : syncAssignees) {
            User user = findOrCreateUser(
                data.globalId(),
                data.username(),
                data.name(),
                data.avatarUrl(),
                data.webUrl(),
                providerId
            );
            if (user != null) newAssignees.add(user);
        }

        if (!currentAssignees.equals(newAssignees)) {
            currentAssignees.clear();
            currentAssignees.addAll(newAssignees);
            return true;
        }
        return false;
    }

    private boolean updateSyncReviewers(
        @Nullable List<SyncUserData> syncReviewers,
        Set<User> currentReviewers,
        Long providerId
    ) {
        if (syncReviewers == null) return false;

        Set<User> newReviewers = new HashSet<>();
        for (SyncUserData data : syncReviewers) {
            User user = findOrCreateUser(
                data.globalId(),
                data.username(),
                data.name(),
                data.avatarUrl(),
                data.webUrl(),
                providerId
            );
            if (user != null) newReviewers.add(user);
        }

        if (!currentReviewers.equals(newReviewers)) {
            currentReviewers.clear();
            currentReviewers.addAll(newReviewers);
            return true;
        }
        return false;
    }
}
