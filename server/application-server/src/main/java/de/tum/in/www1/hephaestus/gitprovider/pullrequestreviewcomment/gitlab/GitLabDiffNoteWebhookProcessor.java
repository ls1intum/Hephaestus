package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.BaseGitLabProcessor;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabFieldUtils;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RepositoryScopeFilter;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab.dto.GitLabNoteEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab.dto.GitLabNoteEventDTO.NoteAttributes;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.gitlab.GitLabPullRequestReviewThreadProcessor;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes GitLab diff note webhooks into {@link PullRequestReviewComment} + {@link PullRequestReviewThread}.
 * <p>
 * When a diff note webhook arrives, we:
 * <ol>
 *   <li>Find the parent MR (PullRequest) by repository + IID</li>
 *   <li>Create/find a thread using the note ID as nativeId (webhooks don't carry discussion IDs)</li>
 *   <li>Create the review comment within that thread</li>
 * </ol>
 * <p>
 * Note: Webhook diff notes don't carry discussion/thread IDs, so we use the note ID
 * itself as the thread native ID for the root note. The full discussion structure is
 * reconciled during GraphQL sync via {@link GitLabDiscussionSyncService}.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabDiffNoteWebhookProcessor extends BaseGitLabProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitLabDiffNoteWebhookProcessor.class);

    private final PullRequestRepository pullRequestRepository;
    private final GitLabPullRequestReviewThreadProcessor threadProcessor;
    private final GitLabPullRequestReviewCommentProcessor reviewCommentProcessor;

    public GitLabDiffNoteWebhookProcessor(
        UserRepository userRepository,
        LabelRepository labelRepository,
        RepositoryRepository repositoryRepository,
        ScopeIdResolver scopeIdResolver,
        RepositoryScopeFilter repositoryScopeFilter,
        GitLabProperties gitLabProperties,
        PullRequestRepository pullRequestRepository,
        GitLabPullRequestReviewThreadProcessor threadProcessor,
        GitLabPullRequestReviewCommentProcessor reviewCommentProcessor
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
        this.threadProcessor = threadProcessor;
        this.reviewCommentProcessor = reviewCommentProcessor;
    }

    /**
     * Processes a diff note webhook event for a merge request.
     */
    @Transactional
    @Nullable
    public PullRequestReviewComment processDiffNote(GitLabNoteEventDTO event, ProcessingContext context) {
        NoteAttributes attrs = event.objectAttributes();
        GitLabNoteEventDTO.EmbeddedMergeRequest embeddedMr = event.mergeRequest();

        if (attrs == null || attrs.id() == null || embeddedMr == null || embeddedMr.iid() == null) {
            log.warn("Skipped diff note: reason=missingData");
            return null;
        }

        // Find parent MR
        PullRequest pr = pullRequestRepository
            .findByRepositoryIdAndNumber(context.repository().getId(), embeddedMr.iid())
            .orElse(null);

        if (pr == null) {
            log.warn("Skipped diff note: reason=mrNotFound, mrIid={}, noteId={}", embeddedMr.iid(), attrs.id());
            return null;
        }

        GitProvider provider = context.provider();

        // Extract position data from the webhook payload
        @SuppressWarnings("unchecked")
        Map<String, Object> position = attrs.position() instanceof Map ? (Map<String, Object>) attrs.position() : null;

        String filePath = null;
        Integer newLine = null;
        Integer oldLine = null;
        String newPath = null;
        String oldPath = null;
        String headSha = null;
        String baseSha = null;

        if (position != null) {
            String np = GitLabFieldUtils.asString(position.get("new_path"));
            filePath = np != null ? np : GitLabFieldUtils.asString(position.get("old_path"));
            newLine = GitLabFieldUtils.toInteger(position.get("new_line"));
            oldLine = GitLabFieldUtils.toInteger(position.get("old_line"));
            newPath = GitLabFieldUtils.asString(position.get("new_path"));
            oldPath = GitLabFieldUtils.asString(position.get("old_path"));
            headSha = GitLabFieldUtils.asString(position.get("head_sha"));
            baseSha = GitLabFieldUtils.asString(position.get("base_sha"));
        }

        // Capture effectively final for lambda
        final String threadPath = filePath;
        final Integer threadLine = newLine;

        Instant createdAt = parseWebhookTimestamp(attrs.createdAt());
        Instant updatedAt = parseWebhookTimestamp(attrs.updatedAt());

        // Create a synthetic thread for this diff note via the thread processor.
        // Webhooks don't provide the discussion ID, so we use the note ID as the thread nativeId.
        // The GraphQL discussion sync will reconcile this into the proper discussion later.
        PullRequestReviewThread thread = threadProcessor.findOrCreateWebhookThread(
            attrs.id(),
            threadPath,
            threadLine,
            pr,
            provider,
            createdAt,
            updatedAt
        );

        // Resolve author
        User author = findOrCreateUser(event.user(), context.providerId());

        // Create the diff note data
        String noteGlobalId = "gid://gitlab/DiffNote/" + attrs.id();

        var noteData = new GitLabPullRequestReviewCommentProcessor.DiffNoteData(
            noteGlobalId,
            attrs.note(),
            attrs.url(),
            filePath,
            newLine,
            oldLine,
            newPath,
            oldPath,
            headSha,
            baseSha,
            createdAt,
            updatedAt
        );

        PullRequestReviewComment comment = reviewCommentProcessor.findOrCreateComment(
            noteData,
            thread,
            pr,
            author,
            provider,
            null, // no parent for webhook diff notes
            context.scopeId()
        );

        if (comment != null) {
            log.debug("Processed diff note webhook: noteId={}, mrIid={}", attrs.id(), embeddedMr.iid());
        }

        return comment;
    }

    @Nullable
    private static Instant parseWebhookTimestamp(@Nullable String value) {
        // Delegates to BaseGitLabProcessor which handles both webhook format
        // ("2026-01-31 19:03:35 +0100") and ISO-8601 ("2026-01-31T19:03:35Z")
        return parseGitLabTimestamp(value);
    }
}
