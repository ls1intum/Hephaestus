package de.tum.in.www1.hephaestus.gitprovider.issue.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncException;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
 * Service for syncing GitLab issues via GraphQL API.
 * <p>
 * Implements cursor-based pagination with {@code updatedAfter} for incremental sync.
 * Confidential issues are skipped. Per-issue error handling ensures one bad issue
 * doesn't abort the entire sync.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabIssueSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabIssueSyncService.class);

    private static final String GET_PROJECT_ISSUES_DOCUMENT = "GetProjectIssues";

    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabIssueProcessor issueProcessor;
    private final GitLabProperties gitLabProperties;

    public GitLabIssueSyncService(
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabIssueProcessor issueProcessor,
        GitLabProperties gitLabProperties
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.issueProcessor = issueProcessor;
        this.gitLabProperties = gitLabProperties;
    }

    /**
     * Syncs all issues for a repository.
     *
     * @param scopeId      the workspace/scope ID for authentication
     * @param repository   the repository to sync issues for
     * @param updatedAfter optional timestamp for incremental sync (null = full sync)
     * @return sync result indicating completion status and count
     */
    public SyncResult syncIssues(Long scopeId, Repository repository, @Nullable OffsetDateTime updatedAfter) {
        String projectPath = repository.getNameWithOwner();
        String safeProjectPath = sanitizeForLog(projectPath);

        log.info(
            "Starting issue sync: scopeId={}, projectPath={}, updatedAfter={}",
            scopeId,
            safeProjectPath,
            updatedAfter
        );

        int totalSynced = 0;
        String cursor = null;
        int page = 0;
        boolean rateLimitAborted = false;
        boolean errorAborted = false;

        try {
            do {
                if (page >= GitLabSyncConstants.MAX_PAGINATION_PAGES) {
                    log.warn("Reached max pagination pages: scopeId={}, projectPath={}", scopeId, safeProjectPath);
                    break;
                }

                graphQlClientProvider.acquirePermission();

                // Wait if rate limited
                try {
                    graphQlClientProvider.waitIfRateLimitLow(scopeId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Issue sync interrupted: scopeId={}, projectPath={}", scopeId, safeProjectPath);
                    rateLimitAborted = true;
                    break;
                }

                int remaining = graphQlClientProvider.getRateLimitRemaining(scopeId);
                int pageSize = GitLabSyncConstants.adaptPageSize(GitLabSyncConstants.ISSUE_SYNC_PAGE_SIZE, remaining);

                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(GET_PROJECT_ISSUES_DOCUMENT)
                    .variable("fullPath", projectPath)
                    .variable("first", pageSize)
                    .variable("after", cursor)
                    .variable("updatedAfter", updatedAfter != null ? updatedAfter.toString() : null)
                    .execute()
                    .block(gitLabProperties.extendedGraphqlTimeout());

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Failed to fetch issues: scopeId={}, projectPath={}, errors={}",
                        scopeId,
                        safeProjectPath,
                        response != null ? response.getErrors() : "null response"
                    );
                    graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                    errorAborted = true;
                    break;
                }

                graphQlClientProvider.recordSuccess();

                // Extract issues from response using dot-notation paths
                @SuppressWarnings({ "unchecked", "rawtypes" })
                List<Map<String, Object>> nodes = (List) response.field("project.issues.nodes").toEntityList(Map.class);

                if (nodes == null || nodes.isEmpty()) {
                    break;
                }

                for (Map<String, Object> issueNode : nodes) {
                    try {
                        if (processIssueNode(issueNode, repository) != null) {
                            totalSynced++;
                        }
                    } catch (Exception e) {
                        log.warn(
                            "Error processing issue: projectPath={}, issueId={}",
                            safeProjectPath,
                            issueNode.get("iid"),
                            e
                        );
                    }
                }

                // Pagination
                GitLabPageInfo pageInfo = response.field("project.issues.pageInfo").toEntity(GitLabPageInfo.class);

                if (pageInfo == null || !pageInfo.hasNextPage()) {
                    break;
                }
                cursor = pageInfo.endCursor();
                page++;

                // Throttle between pages
                try {
                    Thread.sleep(gitLabProperties.paginationThrottle().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    rateLimitAborted = true;
                    break;
                }
            } while (true);
        } catch (Exception e) {
            graphQlClientProvider.recordFailure(e);
            log.error("Issue sync failed: scopeId={}, projectPath={}", scopeId, safeProjectPath, e);
            errorAborted = true;
        }

        SyncResult result;
        if (errorAborted) {
            result = SyncResult.abortedError(totalSynced);
        } else if (rateLimitAborted) {
            result = SyncResult.abortedRateLimit(totalSynced);
        } else {
            result = SyncResult.completed(totalSynced);
        }

        log.info(
            "Completed issue sync: scopeId={}, projectPath={}, status={}, totalSynced={}",
            scopeId,
            safeProjectPath,
            result.status(),
            totalSynced
        );

        return result;
    }

    /**
     * Extracts data from GraphQL response node and delegates to the processor.
     * <p>
     * Label and assignee data is extracted here and passed to the processor, which
     * handles persistence within its {@code @Transactional} boundary.
     *
     * @return the persisted Issue, or {@code null} if the issue was skipped (e.g. confidential)
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private Issue processIssueNode(Map<String, Object> node, Repository repository) {
        String globalId = (String) node.get("id");
        String iid = String.valueOf(node.get("iid"));
        String title = (String) node.get("title");
        String description = (String) node.get("description");
        String state = (String) node.get("state");
        Boolean confidential = (Boolean) node.get("confidential");
        String webUrl = (String) node.get("webUrl");
        String createdAt = node.get("createdAt") != null ? node.get("createdAt").toString() : null;
        String updatedAt = node.get("updatedAt") != null ? node.get("updatedAt").toString() : null;
        String closedAt = node.get("closedAt") != null ? node.get("closedAt").toString() : null;
        int userNotesCount = node.get("userNotesCount") != null ? ((Number) node.get("userNotesCount")).intValue() : 0;

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

        // Extract labels
        List<GitLabIssueProcessor.SyncLabelData> syncLabels = null;
        Map<String, Object> labelsMap = (Map<String, Object>) node.get("labels");
        if (labelsMap != null) {
            List<Map<String, Object>> labelNodes = (List<Map<String, Object>>) labelsMap.get("nodes");
            if (labelNodes != null) {
                syncLabels = new ArrayList<>(labelNodes.size());
                for (Map<String, Object> lbl : labelNodes) {
                    syncLabels.add(
                        new GitLabIssueProcessor.SyncLabelData(
                            (String) lbl.get("id"),
                            (String) lbl.get("title"),
                            (String) lbl.get("color")
                        )
                    );
                }
            }
        }

        // Extract assignees
        List<GitLabIssueProcessor.SyncAssigneeData> syncAssignees = null;
        Map<String, Object> assigneesMap = (Map<String, Object>) node.get("assignees");
        if (assigneesMap != null) {
            List<Map<String, Object>> assigneeNodes = (List<Map<String, Object>>) assigneesMap.get("nodes");
            if (assigneeNodes != null) {
                syncAssignees = new ArrayList<>(assigneeNodes.size());
                for (Map<String, Object> a : assigneeNodes) {
                    syncAssignees.add(
                        new GitLabIssueProcessor.SyncAssigneeData(
                            (String) a.get("id"),
                            (String) a.get("username"),
                            (String) a.get("name"),
                            (String) a.get("avatarUrl"),
                            (String) a.get("webUrl")
                        )
                    );
                }
            }
        }

        var syncData = new GitLabIssueProcessor.SyncIssueData(
            globalId,
            iid,
            title,
            description,
            state,
            Boolean.TRUE.equals(confidential),
            webUrl,
            createdAt,
            updatedAt,
            closedAt,
            authorGlobalId,
            authorUsername,
            authorName,
            authorAvatarUrl,
            authorWebUrl,
            userNotesCount,
            syncLabels,
            syncAssignees
        );
        return issueProcessor.processFromSync(syncData, repository);
    }
}
