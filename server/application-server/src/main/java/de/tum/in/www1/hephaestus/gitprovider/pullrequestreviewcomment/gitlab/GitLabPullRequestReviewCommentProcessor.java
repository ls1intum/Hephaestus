package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.PostgresStringUtils;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitLab merge request diff notes mapped to {@link PullRequestReviewComment}.
 * <p>
 * GitLab diff notes (notes with a {@code position}) are the equivalent of GitHub's
 * pull request review comments. Each diff note belongs to a discussion (thread).
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabPullRequestReviewCommentProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitLabPullRequestReviewCommentProcessor.class);

    private final PullRequestReviewCommentRepository commentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitLabPullRequestReviewCommentProcessor(
        PullRequestReviewCommentRepository commentRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.commentRepository = commentRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Data record for a GitLab diff note extracted from GraphQL or webhook data.
     */
    public record DiffNoteData(
        String noteGlobalId,
        @Nullable String body,
        @Nullable String url,
        @Nullable String filePath,
        @Nullable Integer newLine,
        @Nullable Integer oldLine,
        @Nullable String newPath,
        @Nullable String oldPath,
        @Nullable String headSha,
        @Nullable String baseSha,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt
    ) {}

    /**
     * Groups the contextual parameters needed to find or create a review comment.
     */
    public record CommentContext(
        PullRequestReviewThread thread,
        PullRequest pr,
        @Nullable User author,
        GitProvider provider,
        @Nullable PullRequestReviewComment inReplyTo,
        @Nullable PullRequestReview review,
        Long scopeId
    ) {}

    /**
     * Finds or creates a review comment from a GitLab diff note.
     *
     * @param data the diff note data
     * @param context the contextual parameters (thread, PR, author, provider, inReplyTo, scopeId)
     * @return the persisted comment entity, or null if noteGlobalId is invalid
     */
    @Transactional
    @Nullable
    public PullRequestReviewComment findOrCreateComment(DiffNoteData data, CommentContext context) {
        long nativeId;
        try {
            nativeId = GitLabSyncConstants.extractNumericId(data.noteGlobalId());
        } catch (IllegalArgumentException e) {
            log.warn("Skipped diff note: reason=invalidGlobalId, gid={}", data.noteGlobalId());
            return null;
        }

        Long providerId = context.provider().getId();

        return commentRepository
            .findByNativeIdAndProviderId(nativeId, providerId)
            .map(existing -> updateComment(existing, data, context))
            .orElseGet(() -> createComment(nativeId, data, context));
    }

    private PullRequestReviewComment updateComment(
        PullRequestReviewComment existing,
        DiffNoteData data,
        CommentContext context
    ) {
        PullRequestReviewThread thread = context.thread();
        PullRequest pr = context.pr();
        Long scopeId = context.scopeId();
        Set<String> changedFields = new HashSet<>();
        String sanitizedBody = PostgresStringUtils.sanitize(data.body());
        if (sanitizedBody != null && !sanitizedBody.equals(existing.getBody())) {
            existing.setBody(sanitizedBody);
            changedFields.add("body");
        }
        // Reconcile thread: move comment to the authoritative sync-created thread
        // if it was originally created by a webhook with a different thread
        if (existing.getThread() == null || !existing.getThread().getId().equals(thread.getId())) {
            existing.setThread(thread);
            changedFields.add("thread");
        }
        // Backfill the review link when a synthetic COMMENTED review is now available
        if (existing.getReview() == null && context.review() != null) {
            context.review().addComment(existing);
            changedFields.add("review");
        }
        if (data.updatedAt() != null) {
            existing.setUpdatedAt(data.updatedAt());
        }
        if (!changedFields.isEmpty()) {
            existing = commentRepository.save(existing);
            log.debug("Updated diff note: nativeId={}", existing.getNativeId());

            eventPublisher.publishEvent(
                new DomainEvent.ReviewCommentEdited(
                    EventPayload.ReviewCommentData.from(existing),
                    pr.getId(),
                    changedFields,
                    createSyncContext(pr, scopeId)
                )
            );
        }
        return existing;
    }

    private PullRequestReviewComment createComment(long nativeId, DiffNoteData data, CommentContext context) {
        PullRequestReviewComment comment = new PullRequestReviewComment();
        comment.setNativeId(nativeId);
        comment.setProvider(context.provider());
        comment.setPullRequest(context.pr());
        comment.setThread(context.thread());

        String sanitizedBody = PostgresStringUtils.sanitize(data.body());
        comment.setBody(sanitizedBody != null ? sanitizedBody : "");

        // Path from position data
        String path = data.filePath() != null ? data.filePath() : (data.newPath() != null ? data.newPath() : "");
        comment.setPath(path);

        // Line numbers
        comment.setLine(data.newLine() != null ? data.newLine() : 0);
        comment.setOriginalLine(data.oldLine() != null ? data.oldLine() : 0);

        // Commit SHAs from diffRefs
        comment.setCommitId(data.headSha() != null ? data.headSha() : "");
        comment.setOriginalCommitId(data.baseSha() != null ? data.baseSha() : "");

        // GitLab doesn't expose diff hunks or author association
        comment.setDiffHunk(null);
        comment.setAuthorAssociation(null);

        // HTML URL
        comment.setHtmlUrl(data.url() != null ? data.url() : "");

        // Timestamps
        comment.setCreatedAt(data.createdAt());
        comment.setUpdatedAt(data.updatedAt());

        // Relationships
        if (context.author() != null) {
            comment.setAuthor(context.author());
        }
        if (context.inReplyTo() != null) {
            comment.setInReplyTo(context.inReplyTo());
        }
        if (context.review() != null) {
            context.review().addComment(comment);
        }

        PullRequestReviewComment saved = commentRepository.save(comment);
        log.debug("Created diff note: nativeId={}, path={}, line={}", nativeId, path, data.newLine());

        eventPublisher.publishEvent(
            new DomainEvent.ReviewCommentCreated(
                EventPayload.ReviewCommentData.from(saved),
                context.pr().getId(),
                createSyncContext(context.pr(), context.scopeId())
            )
        );

        return saved;
    }

    private static EventContext createSyncContext(PullRequest pr, Long scopeId) {
        RepositoryRef repoRef = pr.getRepository() != null ? RepositoryRef.from(pr.getRepository()) : null;
        return EventContext.forSync(scopeId, repoRef, GitProviderType.GITLAB);
    }
}
