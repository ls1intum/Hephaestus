package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.PostgresStringUtils;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
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
     * Finds or creates a review comment from a GitLab diff note.
     *
     * @param data the diff note data
     * @param thread the parent thread (discussion)
     * @param pr the parent pull request
     * @param author the comment author (may be null)
     * @param provider the git provider
     * @param inReplyTo the parent comment (for threaded replies, may be null)
     * @param scopeId the scope ID for event context
     * @return the persisted comment entity, or null if noteGlobalId is invalid
     */
    @Transactional
    @Nullable
    public PullRequestReviewComment findOrCreateComment(
        DiffNoteData data,
        PullRequestReviewThread thread,
        PullRequest pr,
        @Nullable User author,
        GitProvider provider,
        @Nullable PullRequestReviewComment inReplyTo,
        Long scopeId
    ) {
        long nativeId;
        try {
            nativeId = GitLabSyncConstants.extractNumericId(data.noteGlobalId());
        } catch (IllegalArgumentException e) {
            log.warn("Skipped diff note: reason=invalidGlobalId, gid={}", data.noteGlobalId());
            return null;
        }

        Long providerId = provider.getId();

        return commentRepository
            .findByNativeIdAndProviderId(nativeId, providerId)
            .map(existing -> updateComment(existing, data, thread, pr, scopeId))
            .orElseGet(() -> createComment(nativeId, data, thread, pr, author, provider, inReplyTo, scopeId));
    }

    private PullRequestReviewComment updateComment(
        PullRequestReviewComment existing,
        DiffNoteData data,
        PullRequestReviewThread thread,
        PullRequest pr,
        Long scopeId
    ) {
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

    private PullRequestReviewComment createComment(
        long nativeId,
        DiffNoteData data,
        PullRequestReviewThread thread,
        PullRequest pr,
        @Nullable User author,
        GitProvider provider,
        @Nullable PullRequestReviewComment inReplyTo,
        Long scopeId
    ) {
        PullRequestReviewComment comment = new PullRequestReviewComment();
        comment.setNativeId(nativeId);
        comment.setProvider(provider);
        comment.setPullRequest(pr);
        comment.setThread(thread);

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
        if (author != null) {
            comment.setAuthor(author);
        }
        if (inReplyTo != null) {
            comment.setInReplyTo(inReplyTo);
        }

        PullRequestReviewComment saved = commentRepository.save(comment);
        log.debug("Created diff note: nativeId={}, path={}, line={}", nativeId, path, data.newLine());

        eventPublisher.publishEvent(
            new DomainEvent.ReviewCommentCreated(
                EventPayload.ReviewCommentData.from(saved),
                pr.getId(),
                createSyncContext(pr, scopeId)
            )
        );

        return saved;
    }

    private static EventContext createSyncContext(PullRequest pr, Long scopeId) {
        RepositoryRef repoRef = pr.getRepository() != null ? RepositoryRef.from(pr.getRepository()) : null;
        return EventContext.forSync(scopeId, repoRef);
    }
}
