package de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncException;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
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
 * Service for syncing GitLab merge requests via GraphQL API.
 * <p>
 * Implements cursor-based pagination with {@code updatedAfter} for incremental sync.
 * Per-MR error handling ensures one bad MR doesn't abort the entire sync.
 * <p>
 * Nested collections (labels, assignees, reviewers, approvedBy) are fetched with
 * overflow detection via {@code count} fields and follow-up pagination.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabMergeRequestSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabMergeRequestSyncService.class);

    private static final String GET_PROJECT_MRS_DOCUMENT = "GetProjectMergeRequests";
    private static final String GET_MR_APPROVALS_DOCUMENT = "GetMergeRequestApprovals";
    private static final String GET_MR_REVIEWERS_DOCUMENT = "GetMergeRequestReviewers";
    private static final String GET_MR_LABELS_DOCUMENT = "GetMergeRequestLabels";
    private static final String GET_MR_ASSIGNEES_DOCUMENT = "GetMergeRequestAssignees";

    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabMergeRequestProcessor mergeRequestProcessor;
    private final GitLabProperties gitLabProperties;

    public GitLabMergeRequestSyncService(
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabMergeRequestProcessor mergeRequestProcessor,
        GitLabProperties gitLabProperties
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.mergeRequestProcessor = mergeRequestProcessor;
        this.gitLabProperties = gitLabProperties;
    }

    public SyncResult syncMergeRequests(Long scopeId, Repository repository, @Nullable OffsetDateTime updatedAfter) {
        String projectPath = repository.getNameWithOwner();
        String safeProjectPath = sanitizeForLog(projectPath);

        log.info(
            "Starting merge request sync: scopeId={}, projectPath={}, updatedAfter={}",
            scopeId,
            safeProjectPath,
            updatedAfter
        );

        int totalSynced = 0;
        int totalSkipped = 0;
        String cursor = null;
        int page = 0;
        boolean rateLimitAborted = false;
        boolean errorAborted = false;
        int reportedTotalCount = -1;

        try {
            do {
                if (page >= GitLabSyncConstants.MAX_PAGINATION_PAGES) {
                    log.warn("Reached max pagination pages: scopeId={}, projectPath={}", scopeId, safeProjectPath);
                    break;
                }

                graphQlClientProvider.acquirePermission();

                try {
                    graphQlClientProvider.waitIfRateLimitLow(scopeId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("MR sync interrupted: scopeId={}, projectPath={}", scopeId, safeProjectPath);
                    rateLimitAborted = true;
                    break;
                }

                int remaining = graphQlClientProvider.getRateLimitRemaining(scopeId);
                int pageSize = GitLabSyncConstants.adaptPageSize(
                    GitLabSyncConstants.MERGE_REQUEST_SYNC_PAGE_SIZE,
                    remaining
                );

                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(GET_PROJECT_MRS_DOCUMENT)
                    .variable("fullPath", projectPath)
                    .variable("first", pageSize)
                    .variable("after", cursor)
                    .variable("updatedAfter", updatedAfter != null ? updatedAfter.toString() : null)
                    .execute()
                    .block(gitLabProperties.extendedGraphqlTimeout());

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Failed to fetch merge requests: scopeId={}, projectPath={}, errors={}",
                        scopeId,
                        safeProjectPath,
                        response != null ? response.getErrors() : "null response"
                    );
                    graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                    errorAborted = true;
                    break;
                }

                if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                    log.warn(
                        "Partial GraphQL errors in MR response: scopeId={}, projectPath={}, errors={}",
                        scopeId,
                        safeProjectPath,
                        response.getErrors()
                    );
                }

                graphQlClientProvider.recordSuccess();

                if (page == 0) {
                    try {
                        Object countField = response.field("project.mergeRequests.count").getValue();
                        if (countField instanceof Number number) {
                            reportedTotalCount = number.intValue();
                            log.info(
                                "MR connection reports count={}, projectPath={}",
                                reportedTotalCount,
                                safeProjectPath
                            );
                        }
                    } catch (Exception e) {
                        log.debug("Could not extract MR count: projectPath={}", safeProjectPath);
                    }
                }

                @SuppressWarnings({ "unchecked", "rawtypes" })
                List<Map<String, Object>> nodes = (List) response
                    .field("project.mergeRequests.nodes")
                    .toEntityList(Map.class);

                if (nodes == null || nodes.isEmpty()) break;

                for (Map<String, Object> mrNode : nodes) {
                    try {
                        if (processMrNode(mrNode, repository, scopeId) != null) {
                            totalSynced++;
                        } else {
                            totalSkipped++;
                        }
                    } catch (Exception e) {
                        log.warn(
                            "Error processing merge request: projectPath={}, mrIid={}",
                            safeProjectPath,
                            mrNode.get("iid"),
                            e
                        );
                        totalSkipped++;
                    }
                }

                GitLabPageInfo pageInfo = response
                    .field("project.mergeRequests.pageInfo")
                    .toEntity(GitLabPageInfo.class);

                if (pageInfo == null || !pageInfo.hasNextPage()) break;
                cursor = pageInfo.endCursor();
                if (cursor == null) {
                    log.warn(
                        "Pagination cursor is null despite hasNextPage=true: projectPath={}, page={}",
                        safeProjectPath,
                        page
                    );
                    break;
                }
                page++;

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
            log.error("MR sync failed: scopeId={}, projectPath={}", scopeId, safeProjectPath, e);
            errorAborted = true;
        }

        if (reportedTotalCount >= 0 && totalSynced + totalSkipped < reportedTotalCount) {
            log.warn(
                "MR connection overflow detected: projectPath={}, synced={}, reportedCount={}",
                safeProjectPath,
                totalSynced,
                reportedTotalCount
            );
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
            "Completed MR sync: scopeId={}, projectPath={}, status={}, totalSynced={}, reportedCount={}",
            scopeId,
            safeProjectPath,
            result.status(),
            totalSynced,
            reportedTotalCount
        );

        return result;
    }

    // ========================================================================
    // Intermediate extraction records
    // ========================================================================

    private record ScalarFields(
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
        int userNotesCount,
        boolean discussionLocked,
        String sourceBranch,
        String targetBranch,
        @Nullable String diffHeadSha,
        @Nullable String baseSha
    ) {}

    private record DiffStats(int additions, int deletions, int fileCount) {
        static final DiffStats EMPTY = new DiffStats(0, 0, 0);
    }

    private record UserFields(
        @Nullable String globalId,
        @Nullable String username,
        @Nullable String name,
        @Nullable String avatarUrl,
        @Nullable String webUrl
    ) {
        static final UserFields EMPTY = new UserFields(null, null, null, null, null);
    }

    // ========================================================================
    // processMrNode — delegates to focused extraction helpers
    // ========================================================================

    @Nullable
    private PullRequest processMrNode(Map<String, Object> node, Repository repository, Long scopeId) {
        String projectPath = repository.getNameWithOwner();
        ScalarFields fields = extractScalarFields(node);
        String mrContext = sanitizeForLog(projectPath) + "!" + fields.iid();
        DiffStats diff = extractDiffStats(node);
        UserFields author = extractUserFields(node, "author");
        UserFields mergeUser = extractUserFields(node, "mergeUser");

        List<GitLabMergeRequestProcessor.SyncLabelData> syncLabels = extractLabels(
            node,
            scopeId,
            projectPath,
            fields.iid(),
            mrContext
        );
        List<GitLabMergeRequestProcessor.SyncUserData> syncAssignees = extractAssignees(
            node,
            scopeId,
            projectPath,
            fields.iid(),
            mrContext
        );
        List<GitLabMergeRequestProcessor.SyncUserData> syncReviewers = extractReviewers(
            node,
            scopeId,
            projectPath,
            fields.iid(),
            mrContext
        );
        List<GitLabMergeRequestProcessor.SyncUserData> syncApprovers = extractApprovers(
            node,
            scopeId,
            projectPath,
            fields.iid(),
            mrContext
        );

        var syncData = new GitLabMergeRequestProcessor.SyncMergeRequestData(
            fields.globalId(),
            fields.iid(),
            fields.title(),
            fields.description(),
            fields.state(),
            fields.draft(),
            fields.mergeable(),
            fields.detailedMergeStatus(),
            fields.approved(),
            fields.webUrl(),
            fields.createdAt(),
            fields.updatedAt(),
            fields.closedAt(),
            fields.mergedAt(),
            fields.commitCount(),
            diff.additions(),
            diff.deletions(),
            diff.fileCount(),
            fields.sourceBranch(),
            fields.targetBranch(),
            fields.diffHeadSha(),
            fields.baseSha(),
            fields.discussionLocked(),
            fields.userNotesCount(),
            author.globalId(),
            author.username(),
            author.name(),
            author.avatarUrl(),
            author.webUrl(),
            mergeUser.globalId(),
            mergeUser.username(),
            mergeUser.name(),
            mergeUser.avatarUrl(),
            mergeUser.webUrl(),
            syncLabels,
            syncAssignees,
            syncReviewers,
            syncApprovers
        );
        return mergeRequestProcessor.processFromSync(syncData, repository, scopeId);
    }

    // ========================================================================
    // Scalar field extraction
    // ========================================================================

    @SuppressWarnings("unchecked")
    private static ScalarFields extractScalarFields(Map<String, Object> node) {
        String globalId = (String) node.get("id");
        String iid = String.valueOf(node.get("iid"));
        String title = (String) node.get("title");
        String description = (String) node.get("description");
        String state = (String) node.get("state");
        String webUrl = (String) node.get("webUrl");
        String createdAt = node.get("createdAt") != null ? node.get("createdAt").toString() : null;
        String updatedAt = node.get("updatedAt") != null ? node.get("updatedAt").toString() : null;
        String closedAt = node.get("closedAt") != null ? node.get("closedAt").toString() : null;
        String mergedAt = node.get("mergedAt") != null ? node.get("mergedAt").toString() : null;
        boolean draft = Boolean.TRUE.equals(node.get("draft"));
        Boolean mergeable = (Boolean) node.get("mergeable");
        String detailedMergeStatus = (String) node.get("detailedMergeStatus");
        boolean approved = Boolean.TRUE.equals(node.get("approved"));
        int commitCount = node.get("commitCount") != null ? ((Number) node.get("commitCount")).intValue() : 0;
        int userNotesCount = node.get("userNotesCount") != null ? ((Number) node.get("userNotesCount")).intValue() : 0;
        boolean discussionLocked = Boolean.TRUE.equals(node.get("discussionLocked"));
        String sourceBranch = (String) node.get("sourceBranch");
        String targetBranch = (String) node.get("targetBranch");
        String diffHeadSha = (String) node.get("diffHeadSha");

        // Extract baseSha from diffRefs
        String baseSha = null;
        Map<String, Object> diffRefs = (Map<String, Object>) node.get("diffRefs");
        if (diffRefs != null) {
            baseSha = (String) diffRefs.get("baseSha");
        }

        return new ScalarFields(
            globalId,
            iid,
            title,
            description,
            state,
            draft,
            mergeable,
            detailedMergeStatus,
            approved,
            webUrl,
            createdAt,
            updatedAt,
            closedAt,
            mergedAt,
            commitCount,
            userNotesCount,
            discussionLocked,
            sourceBranch,
            targetBranch,
            diffHeadSha,
            baseSha
        );
    }

    // ========================================================================
    // Diff stats extraction
    // ========================================================================

    @SuppressWarnings("unchecked")
    private static DiffStats extractDiffStats(Map<String, Object> node) {
        Map<String, Object> diffStats = (Map<String, Object>) node.get("diffStatsSummary");
        if (diffStats == null) {
            return DiffStats.EMPTY;
        }
        int additions = diffStats.get("additions") != null ? ((Number) diffStats.get("additions")).intValue() : 0;
        int deletions = diffStats.get("deletions") != null ? ((Number) diffStats.get("deletions")).intValue() : 0;
        int fileCount = diffStats.get("fileCount") != null ? ((Number) diffStats.get("fileCount")).intValue() : 0;
        return new DiffStats(additions, deletions, fileCount);
    }

    // ========================================================================
    // User field extraction (reusable for author, mergeUser)
    // ========================================================================

    @SuppressWarnings("unchecked")
    private static UserFields extractUserFields(Map<String, Object> node, String fieldName) {
        Map<String, Object> userMap = (Map<String, Object>) node.get(fieldName);
        if (userMap == null) {
            return UserFields.EMPTY;
        }
        return new UserFields(
            (String) userMap.get("id"),
            (String) userMap.get("username"),
            (String) userMap.get("name"),
            (String) userMap.get("avatarUrl"),
            (String) userMap.get("webUrl")
        );
    }

    // ========================================================================
    // Labels extraction with overflow detection
    // ========================================================================

    @SuppressWarnings("unchecked")
    @Nullable
    private List<GitLabMergeRequestProcessor.SyncLabelData> extractLabels(
        Map<String, Object> node,
        Long scopeId,
        String projectPath,
        String iid,
        String context
    ) {
        Map<String, Object> labelsMap = (Map<String, Object>) node.get("labels");
        if (labelsMap == null) return null;

        List<Map<String, Object>> labelNodes = (List<Map<String, Object>>) labelsMap.get("nodes");
        if (labelNodes == null) return null;

        List<GitLabMergeRequestProcessor.SyncLabelData> syncLabels = new ArrayList<>(labelNodes.size());
        for (Map<String, Object> lbl : labelNodes) {
            syncLabels.add(
                new GitLabMergeRequestProcessor.SyncLabelData(
                    (String) lbl.get("id"),
                    (String) lbl.get("title"),
                    (String) lbl.get("color")
                )
            );
        }

        NestedOverflow overflow = detectNestedOverflow(labelsMap, "labels", labelNodes.size(), context);
        if (overflow.hasOverflow()) {
            List<GitLabMergeRequestProcessor.SyncLabelData> remaining = fetchRemainingLabels(
                scopeId,
                projectPath,
                iid,
                overflow.endCursor(),
                context
            );
            if (remaining == null) {
                // Do not reconcile with incomplete source-of-truth data.
                return null;
            }
            syncLabels.addAll(remaining);
        }

        return syncLabels;
    }

    // ========================================================================
    // Assignees extraction with overflow detection
    // ========================================================================

    @SuppressWarnings("unchecked")
    @Nullable
    private List<GitLabMergeRequestProcessor.SyncUserData> extractAssignees(
        Map<String, Object> node,
        Long scopeId,
        String projectPath,
        String iid,
        String context
    ) {
        Map<String, Object> assigneesMap = (Map<String, Object>) node.get("assignees");
        if (assigneesMap == null) return null;

        List<Map<String, Object>> assigneeNodes = (List<Map<String, Object>>) assigneesMap.get("nodes");
        if (assigneeNodes == null) return null;

        List<GitLabMergeRequestProcessor.SyncUserData> syncAssignees = new ArrayList<>(assigneeNodes.size());
        for (Map<String, Object> a : assigneeNodes) {
            syncAssignees.add(
                new GitLabMergeRequestProcessor.SyncUserData(
                    (String) a.get("id"),
                    (String) a.get("username"),
                    (String) a.get("name"),
                    (String) a.get("avatarUrl"),
                    (String) a.get("webUrl")
                )
            );
        }

        NestedOverflow overflow = detectNestedOverflow(assigneesMap, "assignees", assigneeNodes.size(), context);
        if (overflow.hasOverflow()) {
            List<GitLabMergeRequestProcessor.SyncUserData> remaining = fetchRemainingAssignees(
                scopeId,
                projectPath,
                iid,
                overflow.endCursor(),
                context
            );
            if (remaining == null) {
                // Do not reconcile with incomplete source-of-truth data.
                return null;
            }
            syncAssignees.addAll(remaining);
        }

        return syncAssignees;
    }

    // ========================================================================
    // Reviewers extraction with overflow detection
    // ========================================================================

    @SuppressWarnings("unchecked")
    @Nullable
    private List<GitLabMergeRequestProcessor.SyncUserData> extractReviewers(
        Map<String, Object> node,
        Long scopeId,
        String projectPath,
        String iid,
        String context
    ) {
        Map<String, Object> reviewersMap = (Map<String, Object>) node.get("reviewers");
        if (reviewersMap == null) return null;

        List<Map<String, Object>> reviewerNodes = (List<Map<String, Object>>) reviewersMap.get("nodes");
        if (reviewerNodes == null) return null;

        List<GitLabMergeRequestProcessor.SyncUserData> syncReviewers = new ArrayList<>(reviewerNodes.size());
        for (Map<String, Object> r : reviewerNodes) {
            syncReviewers.add(
                new GitLabMergeRequestProcessor.SyncUserData(
                    (String) r.get("id"),
                    (String) r.get("username"),
                    (String) r.get("name"),
                    (String) r.get("avatarUrl"),
                    (String) r.get("webUrl")
                )
            );
        }

        NestedOverflow overflow = detectNestedOverflow(reviewersMap, "reviewers", reviewerNodes.size(), context);
        if (overflow.hasOverflow()) {
            List<GitLabMergeRequestProcessor.SyncUserData> remaining = fetchRemainingReviewers(
                scopeId,
                projectPath,
                iid,
                overflow.endCursor(),
                context
            );
            if (remaining == null) {
                return null; // Do not reconcile with incomplete data
            }
            syncReviewers.addAll(remaining);
        }

        return syncReviewers;
    }

    // ========================================================================
    // Approvers extraction with overflow detection
    // ========================================================================

    @SuppressWarnings("unchecked")
    @Nullable
    private List<GitLabMergeRequestProcessor.SyncUserData> extractApprovers(
        Map<String, Object> node,
        Long scopeId,
        String projectPath,
        String iid,
        String context
    ) {
        Map<String, Object> approvedByMap = (Map<String, Object>) node.get("approvedBy");
        if (approvedByMap == null) return null;

        List<Map<String, Object>> approverNodes = (List<Map<String, Object>>) approvedByMap.get("nodes");
        if (approverNodes == null) return List.of(); // field present but empty → remove all stale approvals

        List<GitLabMergeRequestProcessor.SyncUserData> syncApprovers = new ArrayList<>(approverNodes.size());
        for (Map<String, Object> a : approverNodes) {
            syncApprovers.add(
                new GitLabMergeRequestProcessor.SyncUserData(
                    (String) a.get("id"),
                    (String) a.get("username"),
                    (String) a.get("name"),
                    (String) a.get("avatarUrl"),
                    (String) a.get("webUrl")
                )
            );
        }

        NestedOverflow overflow = detectNestedOverflow(approvedByMap, "approvedBy", approverNodes.size(), context);
        if (overflow.hasOverflow()) {
            List<GitLabMergeRequestProcessor.SyncUserData> remaining = fetchRemainingApprovers(
                scopeId,
                projectPath,
                iid,
                overflow.endCursor(),
                context
            );
            if (remaining == null) {
                return null; // Do not reconcile with incomplete data
            }
            syncApprovers.addAll(remaining);
        }

        return syncApprovers;
    }

    // ========================================================================
    // Nested overflow detection and follow-up pagination
    // ========================================================================

    private record NestedOverflow(boolean hasOverflow, @Nullable String endCursor, int count) {}

    @SuppressWarnings("unchecked")
    private static NestedOverflow detectNestedOverflow(
        Map<String, Object> connectionMap,
        String connectionName,
        int fetchedCount,
        String context
    ) {
        int count = -1;
        Object countField = connectionMap.get("count");
        if (countField instanceof Number number) {
            count = number.intValue();
        }

        Map<String, Object> pageInfo = (Map<String, Object>) connectionMap.get("pageInfo");
        boolean hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.get("hasNextPage"));
        String endCursor = pageInfo != null ? (String) pageInfo.get("endCursor") : null;

        boolean overflow = hasNextPage || (count >= 0 && count > fetchedCount);

        if (overflow) {
            log.warn(
                "GraphQL nested connection overflow: connection={}, fetchedCount={}, count={}, " +
                    "hasNextPage={}, context={}",
                connectionName,
                fetchedCount,
                count,
                hasNextPage,
                context
            );
        }

        return new NestedOverflow(overflow, endCursor, count);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private List<GitLabMergeRequestProcessor.SyncLabelData> fetchRemainingLabels(
        Long scopeId,
        String projectPath,
        String iid,
        @Nullable String afterCursor,
        String context
    ) {
        if (afterCursor == null) return null;

        List<GitLabMergeRequestProcessor.SyncLabelData> allRemaining = new ArrayList<>();
        String cursor = afterCursor;
        int followUpPages = 0;

        try {
            while (cursor != null && followUpPages < GitLabSyncConstants.MAX_PAGINATION_PAGES) {
                graphQlClientProvider.acquirePermission();
                graphQlClientProvider.waitIfRateLimitLow(scopeId);

                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(GET_MR_LABELS_DOCUMENT)
                    .variable("fullPath", projectPath)
                    .variable("iid", iid)
                    .variable("first", GitLabSyncConstants.LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(gitLabProperties.graphqlTimeout());

                if (response == null || !response.isValid()) {
                    graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                    break;
                }

                if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                    log.warn(
                        "Partial errors fetching remaining MR labels: context={}, errors={}",
                        context,
                        response.getErrors()
                    );
                }

                graphQlClientProvider.recordSuccess();

                @SuppressWarnings("rawtypes")
                List mrNodesRaw = response.field("project.mergeRequests.nodes").toEntityList(Map.class);
                List<Map<String, Object>> mrNodes = (List<Map<String, Object>>) mrNodesRaw;

                if (mrNodes == null || mrNodes.isEmpty()) break;

                Map<String, Object> labelsMap = (Map<String, Object>) mrNodes.get(0).get("labels");
                if (labelsMap == null) break;

                List<Map<String, Object>> labelNodes = (List<Map<String, Object>>) labelsMap.get("nodes");
                if (labelNodes == null || labelNodes.isEmpty()) break;

                for (Map<String, Object> lbl : labelNodes) {
                    allRemaining.add(
                        new GitLabMergeRequestProcessor.SyncLabelData(
                            (String) lbl.get("id"),
                            (String) lbl.get("title"),
                            (String) lbl.get("color")
                        )
                    );
                }

                Map<String, Object> pageInfo = (Map<String, Object>) labelsMap.get("pageInfo");
                if (pageInfo == null || !Boolean.TRUE.equals(pageInfo.get("hasNextPage"))) break;
                cursor = (String) pageInfo.get("endCursor");
                followUpPages++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("Error during label follow-up pagination, aborting to prevent data loss: context={}", context, e);
            return null;
        }

        if (!allRemaining.isEmpty()) {
            log.info(
                "Fetched {} additional MR labels via follow-up pagination: context={}",
                allRemaining.size(),
                context
            );
        }

        return allRemaining;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private List<GitLabMergeRequestProcessor.SyncUserData> fetchRemainingAssignees(
        Long scopeId,
        String projectPath,
        String iid,
        @Nullable String afterCursor,
        String context
    ) {
        if (afterCursor == null) return null;

        List<GitLabMergeRequestProcessor.SyncUserData> allRemaining = new ArrayList<>();
        String cursor = afterCursor;
        int followUpPages = 0;

        try {
            while (cursor != null && followUpPages < GitLabSyncConstants.MAX_PAGINATION_PAGES) {
                graphQlClientProvider.acquirePermission();
                graphQlClientProvider.waitIfRateLimitLow(scopeId);

                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(GET_MR_ASSIGNEES_DOCUMENT)
                    .variable("fullPath", projectPath)
                    .variable("iid", iid)
                    .variable("first", GitLabSyncConstants.LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(gitLabProperties.graphqlTimeout());

                if (response == null || !response.isValid()) {
                    graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                    break;
                }

                if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                    log.warn(
                        "Partial errors fetching remaining MR assignees: context={}, errors={}",
                        context,
                        response.getErrors()
                    );
                }

                graphQlClientProvider.recordSuccess();

                @SuppressWarnings("rawtypes")
                List mrNodesRaw = response.field("project.mergeRequests.nodes").toEntityList(Map.class);
                List<Map<String, Object>> mrNodes = (List<Map<String, Object>>) mrNodesRaw;

                if (mrNodes == null || mrNodes.isEmpty()) break;

                Map<String, Object> assigneesMap = (Map<String, Object>) mrNodes.get(0).get("assignees");
                if (assigneesMap == null) break;

                List<Map<String, Object>> assigneeNodes = (List<Map<String, Object>>) assigneesMap.get("nodes");
                if (assigneeNodes == null || assigneeNodes.isEmpty()) break;

                for (Map<String, Object> a : assigneeNodes) {
                    allRemaining.add(
                        new GitLabMergeRequestProcessor.SyncUserData(
                            (String) a.get("id"),
                            (String) a.get("username"),
                            (String) a.get("name"),
                            (String) a.get("avatarUrl"),
                            (String) a.get("webUrl")
                        )
                    );
                }

                Map<String, Object> pageInfo = (Map<String, Object>) assigneesMap.get("pageInfo");
                if (pageInfo == null || !Boolean.TRUE.equals(pageInfo.get("hasNextPage"))) break;
                cursor = (String) pageInfo.get("endCursor");
                followUpPages++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn(
                "Error during assignee follow-up pagination, aborting to prevent data loss: context={}",
                context,
                e
            );
            return null;
        }

        if (!allRemaining.isEmpty()) {
            log.info(
                "Fetched {} additional MR assignees via follow-up pagination: context={}",
                allRemaining.size(),
                context
            );
        }

        return allRemaining;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private List<GitLabMergeRequestProcessor.SyncUserData> fetchRemainingReviewers(
        Long scopeId,
        String projectPath,
        String iid,
        @Nullable String afterCursor,
        String context
    ) {
        if (afterCursor == null) return null;

        List<GitLabMergeRequestProcessor.SyncUserData> allRemaining = new ArrayList<>();
        String cursor = afterCursor;
        int followUpPages = 0;

        try {
            while (cursor != null && followUpPages < GitLabSyncConstants.MAX_PAGINATION_PAGES) {
                graphQlClientProvider.acquirePermission();
                graphQlClientProvider.waitIfRateLimitLow(scopeId);

                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(GET_MR_REVIEWERS_DOCUMENT)
                    .variable("fullPath", projectPath)
                    .variable("iid", iid)
                    .variable("first", GitLabSyncConstants.LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(gitLabProperties.graphqlTimeout());

                if (response == null || !response.isValid()) {
                    graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                    log.warn(
                        "Invalid response fetching remaining MR reviewers, aborting to prevent data loss: context={}",
                        context
                    );
                    return null;
                }

                if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                    log.warn(
                        "Partial errors fetching remaining MR reviewers: context={}, errors={}",
                        context,
                        response.getErrors()
                    );
                }

                graphQlClientProvider.recordSuccess();

                @SuppressWarnings("rawtypes")
                List mrNodesRaw = response.field("project.mergeRequests.nodes").toEntityList(Map.class);
                List<Map<String, Object>> mrNodes = (List<Map<String, Object>>) mrNodesRaw;

                if (mrNodes == null || mrNodes.isEmpty()) break;

                Map<String, Object> reviewersMap = (Map<String, Object>) mrNodes.get(0).get("reviewers");
                if (reviewersMap == null) break;

                List<Map<String, Object>> reviewerNodes = (List<Map<String, Object>>) reviewersMap.get("nodes");
                if (reviewerNodes == null || reviewerNodes.isEmpty()) break;

                for (Map<String, Object> r : reviewerNodes) {
                    allRemaining.add(
                        new GitLabMergeRequestProcessor.SyncUserData(
                            (String) r.get("id"),
                            (String) r.get("username"),
                            (String) r.get("name"),
                            (String) r.get("avatarUrl"),
                            (String) r.get("webUrl")
                        )
                    );
                }

                Map<String, Object> pageInfo = (Map<String, Object>) reviewersMap.get("pageInfo");
                if (pageInfo == null || !Boolean.TRUE.equals(pageInfo.get("hasNextPage"))) break;
                cursor = (String) pageInfo.get("endCursor");
                followUpPages++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn(
                "Error during reviewer follow-up pagination, aborting to prevent data loss: context={}",
                context,
                e
            );
            return null;
        }

        if (!allRemaining.isEmpty()) {
            log.info(
                "Fetched {} additional MR reviewers via follow-up pagination: context={}",
                allRemaining.size(),
                context
            );
        }

        return allRemaining;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private List<GitLabMergeRequestProcessor.SyncUserData> fetchRemainingApprovers(
        Long scopeId,
        String projectPath,
        String iid,
        @Nullable String afterCursor,
        String context
    ) {
        if (afterCursor == null) return null;

        List<GitLabMergeRequestProcessor.SyncUserData> allRemaining = new ArrayList<>();
        String cursor = afterCursor;
        int followUpPages = 0;

        try {
            while (cursor != null && followUpPages < GitLabSyncConstants.MAX_PAGINATION_PAGES) {
                graphQlClientProvider.acquirePermission();
                graphQlClientProvider.waitIfRateLimitLow(scopeId);

                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(GET_MR_APPROVALS_DOCUMENT)
                    .variable("fullPath", projectPath)
                    .variable("iid", iid)
                    .variable("first", GitLabSyncConstants.LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(gitLabProperties.graphqlTimeout());

                if (response == null || !response.isValid()) {
                    graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                    log.warn(
                        "Invalid response fetching remaining MR approvers, aborting to prevent data loss: context={}",
                        context
                    );
                    return null;
                }

                if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                    log.warn(
                        "Partial errors fetching remaining MR approvers: context={}, errors={}",
                        context,
                        response.getErrors()
                    );
                }

                graphQlClientProvider.recordSuccess();

                @SuppressWarnings("rawtypes")
                List mrNodesRaw = response.field("project.mergeRequests.nodes").toEntityList(Map.class);
                List<Map<String, Object>> mrNodes = (List<Map<String, Object>>) mrNodesRaw;

                if (mrNodes == null || mrNodes.isEmpty()) break;

                Map<String, Object> approvedByMap = (Map<String, Object>) mrNodes.get(0).get("approvedBy");
                if (approvedByMap == null) break;

                List<Map<String, Object>> approverNodes = (List<Map<String, Object>>) approvedByMap.get("nodes");
                if (approverNodes == null || approverNodes.isEmpty()) break;

                for (Map<String, Object> a : approverNodes) {
                    allRemaining.add(
                        new GitLabMergeRequestProcessor.SyncUserData(
                            (String) a.get("id"),
                            (String) a.get("username"),
                            (String) a.get("name"),
                            (String) a.get("avatarUrl"),
                            (String) a.get("webUrl")
                        )
                    );
                }

                Map<String, Object> pageInfo = (Map<String, Object>) approvedByMap.get("pageInfo");
                if (pageInfo == null || !Boolean.TRUE.equals(pageInfo.get("hasNextPage"))) break;
                cursor = (String) pageInfo.get("endCursor");
                followUpPages++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn(
                "Error during approver follow-up pagination, aborting to prevent data loss: context={}",
                context,
                e
            );
            return null;
        }

        if (!allRemaining.isEmpty()) {
            log.info(
                "Fetched {} additional MR approvers via follow-up pagination: context={}",
                allRemaining.size(),
                context
            );
        }

        return allRemaining;
    }
}
