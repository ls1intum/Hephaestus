package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncException;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab.GitLabIssueCommentProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.gitlab.GitLabPullRequestReviewThreadProcessor;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Syncs GitLab merge request discussions via GraphQL API.
 * <p>
 * Unlike the flat note-based sync in {@link de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab.GitLabNoteSyncService},
 * this service fetches <b>discussions</b> which preserve:
 * <ul>
 *   <li>Thread structure (discussion = thread of related notes)</li>
 *   <li>Resolution state ({@code resolved}, {@code resolvedBy})</li>
 *   <li>Diff position data ({@code filePath}, {@code newLine}, {@code oldLine}, {@code diffRefs})</li>
 * </ul>
 * <p>
 * Routing logic:
 * <ul>
 *   <li>Discussions where any note has {@code position != null} &rarr; {@link PullRequestReviewThread} + {@link PullRequestReviewComment}</li>
 *   <li>General discussions (no position) &rarr; {@link de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment} (via existing processor)</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabDiscussionSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabDiscussionSyncService.class);

    private static final String GET_MR_DISCUSSIONS_DOCUMENT = "GetMergeRequestDiscussions";
    private static final int DISCUSSION_SYNC_PAGE_SIZE = 50;

    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabPullRequestReviewThreadProcessor threadProcessor;
    private final GitLabPullRequestReviewCommentProcessor reviewCommentProcessor;
    private final GitLabIssueCommentProcessor issueCommentProcessor;
    private final GitLabProperties gitLabProperties;

    public GitLabDiscussionSyncService(
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabPullRequestReviewThreadProcessor threadProcessor,
        GitLabPullRequestReviewCommentProcessor reviewCommentProcessor,
        GitLabIssueCommentProcessor issueCommentProcessor,
        GitLabProperties gitLabProperties
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.threadProcessor = threadProcessor;
        this.reviewCommentProcessor = reviewCommentProcessor;
        this.issueCommentProcessor = issueCommentProcessor;
        this.gitLabProperties = gitLabProperties;
    }

    /**
     * Syncs all discussions for a merge request, routing diff discussions to review
     * threads/comments and general discussions to issue comments.
     *
     * @param scopeId the scope ID for rate limiting
     * @param repository the repository entity
     * @param mrIid the merge request IID (internal ID)
     * @param pr the parent PullRequest entity
     * @return total number of notes synced (diff + general)
     */
    public int syncDiscussionsForMergeRequest(Long scopeId, Repository repository, int mrIid, PullRequest pr) {
        String projectPath = repository.getNameWithOwner();
        String safeContext = sanitizeForLog(projectPath) + "!" + mrIid;
        GitProvider provider = repository.getProvider();
        Long providerId = provider.getId();

        if (providerId == null) {
            log.warn("Skipping discussion sync: reason=nullProviderId, context={}", safeContext);
            return 0;
        }

        int totalDiffNotes = 0;
        int totalGeneralNotes = 0;
        int totalSkipped = 0;
        String cursor = null;
        int page = 0;

        try {
            do {
                if (page >= GitLabSyncConstants.MAX_PAGINATION_PAGES) {
                    log.warn("Discussion sync reached max pages: context={}", safeContext);
                    break;
                }

                graphQlClientProvider.acquirePermission();

                try {
                    graphQlClientProvider.waitIfRateLimitLow(scopeId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Discussion sync interrupted: context={}", safeContext);
                    break;
                }

                int remaining = graphQlClientProvider.getRateLimitRemaining(scopeId);
                int pageSize = GitLabSyncConstants.adaptPageSize(DISCUSSION_SYNC_PAGE_SIZE, remaining);

                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(GET_MR_DISCUSSIONS_DOCUMENT)
                    .variable("fullPath", projectPath)
                    .variable("iid", String.valueOf(mrIid))
                    .variable("first", pageSize)
                    .variable("after", cursor)
                    .execute()
                    .block(gitLabProperties.graphqlTimeout());

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Failed to fetch discussions: context={}, errors={}",
                        safeContext,
                        response != null ? response.getErrors() : "null response"
                    );
                    graphQlClientProvider.recordFailure(
                        new GitLabSyncException("Invalid GraphQL response: context=" + safeContext)
                    );
                    break;
                }

                if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                    log.warn(
                        "Partial errors in discussion response: context={}, errors={}",
                        safeContext,
                        response.getErrors()
                    );
                }

                graphQlClientProvider.recordSuccess();

                String discussionsPath = "project.mergeRequest.discussions";

                @SuppressWarnings("rawtypes")
                List nodesRaw = response.field(discussionsPath + ".nodes").toEntityList(Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesRaw;

                if (nodes == null || nodes.isEmpty()) {
                    break;
                }

                for (Map<String, Object> discussionNode : nodes) {
                    try {
                        int[] result = processDiscussion(discussionNode, pr, provider, providerId, scopeId);
                        totalDiffNotes += result[0];
                        totalGeneralNotes += result[1];
                        totalSkipped += result[2];
                    } catch (Exception e) {
                        log.warn(
                            "Error processing discussion: context={}, id={}",
                            safeContext,
                            discussionNode.get("id"),
                            e
                        );
                        totalSkipped++;
                    }
                }

                // Pagination
                GitLabPageInfo pageInfo = response.field(discussionsPath + ".pageInfo").toEntity(GitLabPageInfo.class);
                if (pageInfo == null || !pageInfo.hasNextPage()) {
                    break;
                }
                cursor = pageInfo.endCursor();
                if (cursor == null) {
                    log.warn("Discussion pagination cursor null despite hasNextPage=true: context={}", safeContext);
                    break;
                }
                page++;

                try {
                    Thread.sleep(gitLabProperties.paginationThrottle().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } while (true);
        } catch (Exception e) {
            graphQlClientProvider.recordFailure(e);
            log.error("Discussion sync failed: context={}", safeContext, e);
        }

        int totalSynced = totalDiffNotes + totalGeneralNotes;
        if (totalSynced > 0 || totalSkipped > 0) {
            log.info(
                "Synced discussions: context={}, diffNotes={}, generalNotes={}, skipped={}",
                safeContext,
                totalDiffNotes,
                totalGeneralNotes,
                totalSkipped
            );
        }

        return totalSynced;
    }

    /**
     * Processes a single discussion node, routing to diff thread/comment or general comment.
     *
     * @return int[3]: [diffNotes, generalNotes, skipped]
     */
    @SuppressWarnings("unchecked")
    private int[] processDiscussion(
        Map<String, Object> discussionNode,
        PullRequest pr,
        GitProvider provider,
        Long providerId,
        Long scopeId
    ) {
        String discussionGlobalId = (String) discussionNode.get("id");
        if (discussionGlobalId == null) {
            return new int[] { 0, 0, 1 };
        }

        Boolean resolved = (Boolean) discussionNode.get("resolved");

        // Extract notes
        Map<String, Object> notesMap = (Map<String, Object>) discussionNode.get("notes");
        if (notesMap == null) {
            return new int[] { 0, 0, 1 };
        }

        List<Map<String, Object>> noteNodes = (List<Map<String, Object>>) notesMap.get("nodes");
        if (noteNodes == null || noteNodes.isEmpty()) {
            return new int[] { 0, 0, 1 };
        }

        // Detect notes truncation (100-note limit per discussion)
        Map<String, Object> notesPageInfo = (Map<String, Object>) notesMap.get("pageInfo");
        if (notesPageInfo != null && Boolean.TRUE.equals(notesPageInfo.get("hasNextPage"))) {
            log.warn(
                "Discussion has more than 100 notes (truncated): discussionId={}, fetchedNotes={}",
                discussionGlobalId,
                noteNodes.size()
            );
        }

        // Check if this is a diff discussion (any note has position)
        boolean isDiffDiscussion = noteNodes.stream().anyMatch(note -> note.get("position") != null);

        if (isDiffDiscussion) {
            return processDiffDiscussion(
                discussionNode,
                discussionGlobalId,
                resolved != null && resolved,
                noteNodes,
                pr,
                provider,
                providerId,
                scopeId
            );
        } else {
            return processGeneralDiscussion(noteNodes, pr, providerId, scopeId);
        }
    }

    /**
     * Processes a diff discussion into PullRequestReviewThread + PullRequestReviewComment(s).
     */
    @SuppressWarnings("unchecked")
    private int[] processDiffDiscussion(
        Map<String, Object> discussionNode,
        String discussionGlobalId,
        boolean resolved,
        List<Map<String, Object>> noteNodes,
        PullRequest pr,
        GitProvider provider,
        Long providerId,
        Long scopeId
    ) {
        int diffNotes = 0;

        // Resolve the user who resolved the thread
        User resolvedBy = null;
        if (resolved) {
            Map<String, Object> resolvedByMap = (Map<String, Object>) discussionNode.get("resolvedBy");
            if (resolvedByMap != null) {
                resolvedBy = issueCommentProcessor.findOrCreateUser(
                    (String) resolvedByMap.get("id"),
                    (String) resolvedByMap.get("username"),
                    (String) resolvedByMap.get("name"),
                    (String) resolvedByMap.get("avatarUrl"),
                    (String) resolvedByMap.get("webUrl"),
                    providerId
                );
            }
        }

        // Extract position from first note for thread metadata
        Map<String, Object> firstNote = noteNodes.get(0);
        Map<String, Object> firstPosition = (Map<String, Object>) firstNote.get("position");
        String filePath = firstPosition != null ? (String) firstPosition.get("filePath") : null;
        Integer newLine = firstPosition != null ? GitLabFieldUtils.toInteger(firstPosition.get("newLine")) : null;

        Instant firstCreatedAt = parseTimestamp((String) firstNote.get("createdAt"));

        // Create/update the thread
        PullRequestReviewThread thread = threadProcessor.findOrCreateThread(
            discussionGlobalId,
            resolved,
            resolvedBy,
            filePath,
            newLine,
            pr,
            provider,
            firstCreatedAt,
            scopeId
        );

        // Process each note in the discussion as a review comment
        PullRequestReviewComment previousComment = null;
        for (Map<String, Object> noteNode : noteNodes) {
            // Skip system and internal notes within discussions
            if (Boolean.TRUE.equals(noteNode.get("system")) || Boolean.TRUE.equals(noteNode.get("internal"))) {
                continue;
            }

            String noteGlobalId = (String) noteNode.get("id");
            if (noteGlobalId == null) {
                continue;
            }

            // Extract position data for this note
            Map<String, Object> position = (Map<String, Object>) noteNode.get("position");
            String noteFilePath = null;
            Integer noteNewLine = null;
            Integer noteOldLine = null;
            String newPath = null;
            String oldPath = null;
            String headSha = null;
            String baseSha = null;

            if (position != null) {
                noteFilePath = (String) position.get("filePath");
                noteNewLine = GitLabFieldUtils.toInteger(position.get("newLine"));
                noteOldLine = GitLabFieldUtils.toInteger(position.get("oldLine"));
                newPath = (String) position.get("newPath");
                oldPath = (String) position.get("oldPath");

                Map<String, Object> diffRefs = (Map<String, Object>) position.get("diffRefs");
                if (diffRefs != null) {
                    headSha = (String) diffRefs.get("headSha");
                    baseSha = (String) diffRefs.get("baseSha");
                }
            }

            // Resolve author
            User author = resolveAuthor(noteNode, providerId);

            var noteData = new GitLabPullRequestReviewCommentProcessor.DiffNoteData(
                noteGlobalId,
                (String) noteNode.get("body"),
                (String) noteNode.get("url"),
                noteFilePath,
                noteNewLine,
                noteOldLine,
                newPath,
                oldPath,
                headSha,
                baseSha,
                parseTimestamp((String) noteNode.get("createdAt")),
                parseTimestamp((String) noteNode.get("updatedAt"))
            );

            PullRequestReviewComment comment = reviewCommentProcessor.findOrCreateComment(
                noteData,
                thread,
                pr,
                author,
                provider,
                previousComment, // first note has no parent, subsequent notes are replies
                scopeId
            );

            if (comment != null) {
                diffNotes++;
                previousComment = comment;
            }
        }

        return new int[] { diffNotes, 0, 0 };
    }

    /**
     * Processes a general discussion into IssueComment(s) via the existing processor.
     */
    private int[] processGeneralDiscussion(
        List<Map<String, Object>> noteNodes,
        PullRequest pr,
        Long providerId,
        Long scopeId
    ) {
        int generalNotes = 0;

        for (Map<String, Object> noteNode : noteNodes) {
            // Skip system and internal notes
            if (Boolean.TRUE.equals(noteNode.get("system")) || Boolean.TRUE.equals(noteNode.get("internal"))) {
                continue;
            }

            String globalId = (String) noteNode.get("id");
            if (globalId == null) {
                continue;
            }

            try {
                long noteId = GitLabSyncConstants.extractNumericId(globalId);
                @SuppressWarnings("unchecked")
                Map<String, Object> authorMap = (Map<String, Object>) noteNode.get("author");

                var syncData = new GitLabIssueCommentProcessor.SyncNoteData(
                    noteId,
                    (String) noteNode.get("body"),
                    (String) noteNode.get("url"),
                    authorMap != null ? (String) authorMap.get("id") : null,
                    authorMap != null ? (String) authorMap.get("username") : null,
                    authorMap != null ? (String) authorMap.get("name") : null,
                    authorMap != null ? (String) authorMap.get("avatarUrl") : null,
                    authorMap != null ? (String) authorMap.get("webUrl") : null,
                    noteNode.get("createdAt") != null ? noteNode.get("createdAt").toString() : null,
                    noteNode.get("updatedAt") != null ? noteNode.get("updatedAt").toString() : null
                );

                if (issueCommentProcessor.processFromSync(syncData, pr, providerId, scopeId) != null) {
                    generalNotes++;
                }
            } catch (Exception e) {
                log.warn("Error processing general note: gid={}", globalId, e);
            }
        }

        return new int[] { 0, generalNotes, 0 };
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private User resolveAuthor(Map<String, Object> noteNode, Long providerId) {
        Map<String, Object> authorMap = (Map<String, Object>) noteNode.get("author");
        if (authorMap == null) {
            return null;
        }
        return issueCommentProcessor.findOrCreateUser(
            (String) authorMap.get("id"),
            (String) authorMap.get("username"),
            (String) authorMap.get("name"),
            (String) authorMap.get("avatarUrl"),
            (String) authorMap.get("webUrl"),
            providerId
        );
    }

    @Nullable
    static Instant parseTimestamp(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            log.warn("Could not parse timestamp: value={}", value);
            return null;
        }
    }
}
