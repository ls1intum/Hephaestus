package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitLab merge request discussion threads.
 * <p>
 * Maps GitLab discussions (which are threaded containers of notes) to
 * {@link PullRequestReviewThread} entities. A GitLab discussion with
 * {@code position != null} on any note is a diff discussion that maps
 * to a review thread.
 * <p>
 * GitLab Discussion IDs are SHA hex hashes (e.g. {@code gid://gitlab/Discussion/6a9c1750b37d...}),
 * NOT numeric. We store the full GID as {@code nodeId} and use a deterministic
 * hash as {@code nativeId} for the composite unique constraint.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabPullRequestReviewThreadProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitLabPullRequestReviewThreadProcessor.class);

    private final PullRequestReviewThreadRepository threadRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitLabPullRequestReviewThreadProcessor(
        PullRequestReviewThreadRepository threadRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.threadRepository = threadRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Finds or creates a review thread from a GitLab discussion.
     * <p>
     * Since GitLab Discussion IDs are hex hashes (not numeric), we look up by
     * {@code nodeId + providerId} and use a deterministic hash as {@code nativeId}.
     *
     * @param discussionGlobalId GitLab global ID (e.g. "gid://gitlab/Discussion/abc123def...")
     * @param resolved whether the discussion is resolved
     * @param resolvedBy the user who resolved the discussion (null if unresolved)
     * @param filePath the file path from the first note's position
     * @param newLine the new line number from the first note's position
     * @param pr the parent pull request
     * @param provider the git provider
     * @param createdAt the creation timestamp of the first note
     * @param scopeId the scope ID for event context
     * @return the thread entity (never null)
     */
    @Transactional
    public PullRequestReviewThread findOrCreateThread(
        String discussionGlobalId,
        boolean resolved,
        @Nullable User resolvedBy,
        @Nullable String filePath,
        @Nullable Integer newLine,
        PullRequest pr,
        GitProvider provider,
        @Nullable Instant createdAt,
        Long scopeId
    ) {
        Long providerId = provider.getId();

        return threadRepository
            .findByNodeIdAndProviderId(discussionGlobalId, providerId)
            .map(existing -> updateThread(existing, resolved, resolvedBy, pr, scopeId))
            .orElseGet(() ->
                createThread(
                    discussionGlobalId,
                    resolved,
                    resolvedBy,
                    filePath,
                    newLine,
                    pr,
                    provider,
                    createdAt,
                    scopeId
                )
            );
    }

    /**
     * Finds or creates a thread from a webhook diff note, where only the note ID is available.
     * <p>
     * Webhooks don't carry the discussion ID, so we use the note's numeric ID as nativeId.
     * The GraphQL discussion sync will reconcile this into the proper discussion later
     * by matching on the note within the thread.
     */
    @Transactional
    public PullRequestReviewThread findOrCreateWebhookThread(
        long noteNativeId,
        @Nullable String filePath,
        @Nullable Integer newLine,
        PullRequest pr,
        GitProvider provider,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt
    ) {
        Long providerId = provider.getId();

        return threadRepository
            .findByNativeIdAndProviderId(noteNativeId, providerId)
            .orElseGet(() -> {
                PullRequestReviewThread thread = new PullRequestReviewThread();
                thread.setNativeId(noteNativeId);
                thread.setProvider(provider);
                thread.setPullRequest(pr);
                thread.setPath(filePath);
                thread.setLine(newLine);
                thread.setState(PullRequestReviewThread.State.UNRESOLVED);
                thread.setCreatedAt(createdAt);
                thread.setUpdatedAt(updatedAt);

                PullRequestReviewThread saved = threadRepository.save(thread);
                log.debug("Created webhook thread: nativeId={}, path={}", noteNativeId, filePath);
                return saved;
            });
    }

    private PullRequestReviewThread updateThread(
        PullRequestReviewThread existing,
        boolean resolved,
        @Nullable User resolvedBy,
        PullRequest pr,
        Long scopeId
    ) {
        PullRequestReviewThread.State previousState = existing.getState();
        boolean changed = false;

        PullRequestReviewThread.State newState = resolved
            ? PullRequestReviewThread.State.RESOLVED
            : PullRequestReviewThread.State.UNRESOLVED;

        if (existing.getState() != newState) {
            existing.setState(newState);
            changed = true;
        }
        if (resolved && resolvedBy != null && existing.getResolvedBy() == null) {
            existing.setResolvedBy(resolvedBy);
            changed = true;
        }
        if (!resolved && existing.getResolvedBy() != null) {
            existing.setResolvedBy(null);
            changed = true;
        }

        if (changed) {
            existing.setUpdatedAt(Instant.now());
            existing = threadRepository.save(existing);
            log.debug("Updated thread: id={}, state={}", existing.getId(), newState);

            // Publish domain events on state transitions
            if (previousState != newState) {
                publishThreadStateEvent(existing, pr, scopeId);
            }
        }
        return existing;
    }

    private PullRequestReviewThread createThread(
        String discussionGlobalId,
        boolean resolved,
        @Nullable User resolvedBy,
        @Nullable String filePath,
        @Nullable Integer newLine,
        PullRequest pr,
        GitProvider provider,
        @Nullable Instant createdAt,
        Long scopeId
    ) {
        long nativeId = deterministicNativeId(discussionGlobalId);

        PullRequestReviewThread thread = new PullRequestReviewThread();
        thread.setNativeId(nativeId);
        thread.setNodeId(discussionGlobalId);
        thread.setProvider(provider);
        thread.setPullRequest(pr);
        thread.setPath(filePath);
        thread.setLine(newLine);
        thread.setState(resolved ? PullRequestReviewThread.State.RESOLVED : PullRequestReviewThread.State.UNRESOLVED);
        if (resolved && resolvedBy != null) {
            thread.setResolvedBy(resolvedBy);
        }
        thread.setCreatedAt(createdAt);
        thread.setUpdatedAt(createdAt);

        PullRequestReviewThread saved = threadRepository.save(thread);
        log.debug("Created thread from GitLab discussion: nodeId={}, path={}", discussionGlobalId, filePath);

        // Publish resolved event if the thread was already resolved when first synced
        if (resolved) {
            publishThreadStateEvent(saved, pr, scopeId);
        }

        return saved;
    }

    private void publishThreadStateEvent(PullRequestReviewThread thread, PullRequest pr, Long scopeId) {
        EventPayload.ReviewThreadData.from(thread).ifPresent(threadData -> {
            RepositoryRef repoRef = pr.getRepository() != null ? RepositoryRef.from(pr.getRepository()) : null;
            EventContext ctx = EventContext.forSync(scopeId, repoRef);

            if (thread.getState() == PullRequestReviewThread.State.RESOLVED) {
                eventPublisher.publishEvent(new DomainEvent.ReviewThreadResolved(threadData, ctx));
            } else {
                eventPublisher.publishEvent(new DomainEvent.ReviewThreadUnresolved(threadData, ctx));
            }
        });
    }

    /**
     * Generates a deterministic positive nativeId from a GitLab Discussion GID.
     * <p>
     * GitLab Discussion IDs are hex hashes (not numeric), so we use a hash function
     * to produce a stable Long. This is collision-resistant enough for our use case
     * since it's scoped per provider.
     */
    static long deterministicNativeId(String discussionGlobalId) {
        // Use the same approach as java.lang.String.hashCode() but with long accumulator
        // for better distribution. FNV-1a inspired.
        long hash = 0xcbf29ce484222325L; // FNV offset basis
        for (int i = 0; i < discussionGlobalId.length(); i++) {
            hash ^= discussionGlobalId.charAt(i);
            hash *= 0x100000001b3L; // FNV prime
        }
        // Ensure positive — nativeId must be positive
        return hash & Long.MAX_VALUE;
    }
}
