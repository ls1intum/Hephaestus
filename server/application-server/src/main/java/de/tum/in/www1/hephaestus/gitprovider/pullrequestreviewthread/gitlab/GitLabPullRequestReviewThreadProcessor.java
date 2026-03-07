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
     * Groups the discussion-level data needed to find or create a review thread.
     */
    public record ThreadData(
        String discussionGlobalId,
        boolean resolved,
        @Nullable User resolvedBy,
        @Nullable String filePath,
        @Nullable Integer newLine,
        @Nullable Instant createdAt
    ) {}

    /**
     * Groups the webhook-level data needed to find or create a webhook thread.
     */
    public record WebhookThreadData(
        long noteNativeId,
        @Nullable String filePath,
        @Nullable Integer newLine,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt
    ) {}

    /**
     * Finds or creates a review thread from a GitLab discussion.
     * <p>
     * Since GitLab Discussion IDs are hex hashes (not numeric), we look up by
     * {@code nodeId + providerId} and use a deterministic hash as {@code nativeId}.
     *
     * @param data the discussion-level data (global ID, resolution state, file position, timestamp)
     * @param pr the parent pull request
     * @param provider the git provider
     * @param scopeId the scope ID for event context
     * @return the thread entity (never null)
     */
    @Transactional
    public PullRequestReviewThread findOrCreateThread(
        ThreadData data,
        PullRequest pr,
        GitProvider provider,
        Long scopeId
    ) {
        Long providerId = provider.getId();

        return threadRepository
            .findByNodeIdAndProviderId(data.discussionGlobalId(), providerId)
            .map(existing -> updateThread(existing, data.resolved(), data.resolvedBy(), pr, scopeId))
            .orElseGet(() -> createThread(data, pr, provider, scopeId));
    }

    /**
     * Finds or creates a thread from a webhook diff note, where only the note ID is available.
     * <p>
     * Webhooks don't carry the discussion ID, so we use the note's numeric ID as nativeId.
     * The GraphQL discussion sync will reconcile this into the proper discussion later
     * by matching on the note within the thread.
     *
     * @param data the webhook-level data (note native ID, file position, timestamps)
     * @param pr the parent pull request
     * @param provider the git provider
     * @return the thread entity (never null)
     */
    @Transactional
    public PullRequestReviewThread findOrCreateWebhookThread(
        WebhookThreadData data,
        PullRequest pr,
        GitProvider provider
    ) {
        Long providerId = provider.getId();

        return threadRepository
            .findByNativeIdAndProviderId(data.noteNativeId(), providerId)
            .orElseGet(() -> {
                PullRequestReviewThread thread = new PullRequestReviewThread();
                thread.setNativeId(data.noteNativeId());
                thread.setProvider(provider);
                thread.setPullRequest(pr);
                thread.setPath(data.filePath());
                thread.setLine(data.newLine());
                thread.setState(PullRequestReviewThread.State.UNRESOLVED);
                thread.setCreatedAt(data.createdAt());
                thread.setUpdatedAt(data.updatedAt());

                PullRequestReviewThread saved = threadRepository.save(thread);
                log.debug("Created webhook thread: nativeId={}, path={}", data.noteNativeId(), data.filePath());
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
        ThreadData data,
        PullRequest pr,
        GitProvider provider,
        Long scopeId
    ) {
        long nativeId = deterministicNativeId(data.discussionGlobalId());

        PullRequestReviewThread thread = new PullRequestReviewThread();
        thread.setNativeId(nativeId);
        thread.setNodeId(data.discussionGlobalId());
        thread.setProvider(provider);
        thread.setPullRequest(pr);
        thread.setPath(data.filePath());
        thread.setLine(data.newLine());
        thread.setState(data.resolved() ? PullRequestReviewThread.State.RESOLVED : PullRequestReviewThread.State.UNRESOLVED);
        if (data.resolved() && data.resolvedBy() != null) {
            thread.setResolvedBy(data.resolvedBy());
        }
        thread.setCreatedAt(data.createdAt());
        thread.setUpdatedAt(data.createdAt());

        PullRequestReviewThread saved = threadRepository.save(thread);
        log.debug("Created thread from GitLab discussion: nodeId={}, path={}", data.discussionGlobalId(), data.filePath());

        // Publish resolved event if the thread was already resolved when first synced
        if (data.resolved()) {
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
