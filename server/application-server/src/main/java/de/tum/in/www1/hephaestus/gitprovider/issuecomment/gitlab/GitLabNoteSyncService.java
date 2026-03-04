package de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncException;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;

/** Syncs GitLab notes (comments) via GraphQL API. */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabNoteSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabNoteSyncService.class);

    private static final String GET_ISSUE_NOTES_DOCUMENT = "GetIssueNotes";
    private static final String GET_MR_NOTES_DOCUMENT = "GetMergeRequestNotes";

    private static final int NOTE_SYNC_PAGE_SIZE = 100;

    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabIssueCommentProcessor issueCommentProcessor;
    private final GitLabProperties gitLabProperties;

    public GitLabNoteSyncService(
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabIssueCommentProcessor issueCommentProcessor,
        GitLabProperties gitLabProperties
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.issueCommentProcessor = issueCommentProcessor;
        this.gitLabProperties = gitLabProperties;
    }

    public int syncNotesForIssue(Long scopeId, Repository repository, int issueIid, Issue parent) {
        return syncNotes(
            scopeId,
            repository,
            GET_ISSUE_NOTES_DOCUMENT,
            "iid",
            String.valueOf(issueIid),
            "project.issue.notes",
            parent
        );
    }

    public int syncNotesForMergeRequest(Long scopeId, Repository repository, int mrIid, Issue parent) {
        return syncNotes(
            scopeId,
            repository,
            GET_MR_NOTES_DOCUMENT,
            "iid",
            String.valueOf(mrIid),
            "project.mergeRequest.notes",
            parent
        );
    }

    @SuppressWarnings("unchecked")
    private int syncNotes(
        Long scopeId,
        Repository repository,
        String documentName,
        String iidVariableName,
        String iidValue,
        String notesFieldPath,
        Issue parent
    ) {
        String projectPath = repository.getNameWithOwner();
        String safeContext = sanitizeForLog(projectPath) + "#" + iidValue;
        Long providerId = repository.getProvider() != null ? repository.getProvider().getId() : null;

        if (providerId == null) {
            log.warn("Skipping note sync: reason=nullProviderId, context={}", safeContext);
            return 0;
        }

        int totalSynced = 0;
        int totalSkipped = 0;
        String cursor = null;
        int page = 0;
        int reportedTotalCount = -1;

        try {
            do {
                if (page >= GitLabSyncConstants.MAX_PAGINATION_PAGES) {
                    log.warn("Note sync reached max pages: context={}", safeContext);
                    break;
                }

                graphQlClientProvider.acquirePermission();

                try {
                    graphQlClientProvider.waitIfRateLimitLow(scopeId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Note sync interrupted: context={}", safeContext);
                    break;
                }

                int remaining = graphQlClientProvider.getRateLimitRemaining(scopeId);
                int pageSize = GitLabSyncConstants.adaptPageSize(NOTE_SYNC_PAGE_SIZE, remaining);

                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(documentName)
                    .variable("fullPath", projectPath)
                    .variable(iidVariableName, iidValue)
                    .variable("first", pageSize)
                    .variable("after", cursor)
                    .execute()
                    .block(gitLabProperties.graphqlTimeout());

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Failed to fetch notes: context={}, errors={}",
                        safeContext,
                        response != null ? response.getErrors() : "null response"
                    );
                    String errorDetail = response != null ? String.valueOf(response.getErrors()) : "null response";
                    graphQlClientProvider.recordFailure(
                        new GitLabSyncException("Invalid GraphQL response: context=" + safeContext + ", errors=" + errorDetail)
                    );
                    break;
                }

                if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                    log.warn(
                        "Partial errors in note response: context={}, errors={}",
                        safeContext,
                        response.getErrors()
                    );
                }

                graphQlClientProvider.recordSuccess();

                if (page == 0) {
                    try {
                        Object countField = response.field(notesFieldPath + ".count").getValue();
                        if (countField instanceof Number number) {
                            reportedTotalCount = number.intValue();
                        }
                    } catch (Exception e) {
                        log.debug("Could not extract note count: context={}", safeContext);
                    }
                }

                @SuppressWarnings("rawtypes")
                List nodesRaw = response.field(notesFieldPath + ".nodes").toEntityList(Map.class);
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesRaw;

                if (nodes == null || nodes.isEmpty()) {
                    break;
                }

                for (Map<String, Object> noteNode : nodes) {
                    try {
                        if (processNoteNode(noteNode, parent, providerId, scopeId)) {
                            totalSynced++;
                        } else {
                            totalSkipped++;
                        }
                    } catch (Exception e) {
                        log.warn("Error processing note: context={}, noteId={}", safeContext, noteNode.get("id"), e);
                        totalSkipped++;
                    }
                }

                // Pagination
                GitLabPageInfo pageInfo = response.field(notesFieldPath + ".pageInfo").toEntity(GitLabPageInfo.class);

                if (pageInfo == null || !pageInfo.hasNextPage()) {
                    break;
                }
                cursor = pageInfo.endCursor();
                if (cursor == null) {
                    log.warn("Note pagination cursor null despite hasNextPage=true: context={}", safeContext);
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
            log.error("Note sync failed: context={}", safeContext, e);
        }

        if (reportedTotalCount >= 0 && totalSynced + totalSkipped < reportedTotalCount) {
            log.warn(
                "Note connection overflow detected: context={}, synced={}, skipped={}, reportedCount={}",
                safeContext,
                totalSynced,
                totalSkipped,
                reportedTotalCount
            );
        }

        if (totalSynced > 0) {
            log.debug("Synced {} notes (skipped {}): context={}", totalSynced, totalSkipped, safeContext);
        }

        return totalSynced;
    }

    @SuppressWarnings("unchecked")
    private boolean processNoteNode(Map<String, Object> node, Issue parent, Long providerId, Long scopeId) {
        // Skip system-generated notes
        if (Boolean.TRUE.equals(node.get("system"))) {
            return false;
        }

        // Skip internal/confidential notes
        if (Boolean.TRUE.equals(node.get("internal"))) {
            return false;
        }

        // Skip diff notes (position != null) — deferred to follow-up PR
        if (node.get("position") != null) {
            log.debug("Skipping diff note during sync: id={}", node.get("id"));
            return false;
        }

        String globalId = (String) node.get("id");
        if (globalId == null) {
            return false;
        }

        long noteId = GitLabSyncConstants.extractNumericId(globalId);
        String body = (String) node.get("body");
        String url = (String) node.get("url");
        String createdAt = node.get("createdAt") != null ? node.get("createdAt").toString() : null;
        String updatedAt = node.get("updatedAt") != null ? node.get("updatedAt").toString() : null;

        // Author
        String authorGlobalId = null,
            authorUsername = null,
            authorName = null,
            authorAvatarUrl = null,
            authorWebUrl = null;
        Map<String, Object> authorMap = (Map<String, Object>) node.get("author");
        if (authorMap != null) {
            authorGlobalId = (String) authorMap.get("id");
            authorUsername = (String) authorMap.get("username");
            authorName = (String) authorMap.get("name");
            authorAvatarUrl = (String) authorMap.get("avatarUrl");
            authorWebUrl = (String) authorMap.get("webUrl");
        }

        var syncData = new GitLabIssueCommentProcessor.SyncNoteData(
            noteId,
            body,
            url,
            authorGlobalId,
            authorUsername,
            authorName,
            authorAvatarUrl,
            authorWebUrl,
            createdAt,
            updatedAt
        );

        return issueCommentProcessor.processFromSync(syncData, parent, providerId, scopeId) != null;
    }
}
