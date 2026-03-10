package de.tum.in.www1.hephaestus.gitprovider.subissue.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlResponseHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncException;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Syncs sub-issue (parent-child) relationships for GitLab issues.
 * <p>
 * Uses the GitLab GraphQL Work Item hierarchy API to fetch parent-child
 * relationships. GitLab models hierarchy through WorkItem widgets, not
 * through issue links (the REST issue links API does not support
 * {@code is_child_of}/{@code is_parent_of} — those types do not exist).
 * <p>
 * Uses a single paginated GraphQL query per repository (efficient),
 * unlike the previous REST-based approach which made N+1 API calls.
 * <p>
 * Includes stale parent cleanup: if a parent-child relationship is
 * removed in GitLab, the local DB is updated to clear the parent.
 * <p>
 * No webhook support — runs only during scheduled sync.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabSubIssueSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabSubIssueSyncService.class);
    private static final String DOCUMENT_NAME = "GetProjectWorkItemHierarchy";
    private static final int PAGE_SIZE = 50;

    private final IssueRepository issueRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabGraphQlResponseHandler responseHandler;

    public GitLabSubIssueSyncService(
        IssueRepository issueRepository,
        RepositoryRepository repositoryRepository,
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabGraphQlResponseHandler responseHandler
    ) {
        this.issueRepository = issueRepository;
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.responseHandler = responseHandler;
    }

    /**
     * Syncs parent-child relationships for all issues in a repository
     * using the GitLab GraphQL WorkItem hierarchy API.
     *
     * @param scopeId    workspace scope ID
     * @param repository the repository to sync sub-issues for
     * @return sync result
     */
    @Transactional
    public SyncResult syncSubIssuesForRepository(Long scopeId, Repository repository) {
        String projectPath = repository.getNameWithOwner();
        String safeProjectPath = sanitizeForLog(projectPath);

        List<Issue> issues = issueRepository.findAllByRepository_Id(repository.getId());
        if (issues.isEmpty()) return SyncResult.completed(0);

        // Build IID -> Issue lookup map
        Map<Integer, Issue> issueByIid = new java.util.HashMap<>();
        for (Issue issue : issues) {
            if (issue.getNumber() > 0) {
                issueByIid.put(issue.getNumber(), issue);
            }
        }

        // Track which issues have a parent according to GitLab
        Set<Long> issuesWithParentInGitLab = new HashSet<>();
        int totalLinked = 0;

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        String cursor = null;
        String previousCursor = null;
        int page = 0;
        boolean errorAborted = false;

        try {
            do {
                if (page >= GitLabSyncConstants.MAX_PAGINATION_PAGES) {
                    log.warn("Reached max pagination: project={}", safeProjectPath);
                    break;
                }

                graphQlClientProvider.acquirePermission();

                ClientGraphQlResponse response = client
                    .documentName(DOCUMENT_NAME)
                    .variable("fullPath", projectPath)
                    .variable("first", PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(java.time.Duration.ofSeconds(30));

                var handleResult = responseHandler.handle(response, "sub-issues for " + safeProjectPath, log);
                if (handleResult.action() == GitLabGraphQlResponseHandler.HandleResult.Action.RETRY) {
                    continue;
                }
                if (handleResult.action() == GitLabGraphQlResponseHandler.HandleResult.Action.ABORT) {
                    graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                    errorAborted = true;
                    break;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) (List<?>) response
                    .field("project.workItems.nodes")
                    .toEntityList(Map.class);
                if (nodes == null || nodes.isEmpty()) break;

                for (Map<String, Object> node : nodes) {
                    int linked = processWorkItemNode(node, issueByIid, issuesWithParentInGitLab, repository);
                    totalLinked += linked;
                }

                // Pagination
                GitLabPageInfo pageInfo = response.field("project.workItems.pageInfo").toEntity(GitLabPageInfo.class);
                if (pageInfo == null || !pageInfo.hasNextPage()) break;
                cursor = pageInfo.endCursor();
                if (responseHandler.isPaginationLoop(cursor, previousCursor, "sub-issues for " + safeProjectPath, log)) {
                    errorAborted = true;
                    break;
                }
                previousCursor = cursor;
                page++;
            } while (cursor != null);

            // Stale parent cleanup: clear parentIssue for issues that no longer have a parent in GitLab
            int staleCleared = clearStaleParents(issues, issuesWithParentInGitLab);

            if (totalLinked > 0 || staleCleared > 0) {
                log.info(
                    "GitLab sub-issue sync: project={}, linked={}, staleCleared={}",
                    safeProjectPath,
                    totalLinked,
                    staleCleared
                );
            }

            return SyncResult.completed(totalLinked);
        } catch (Exception e) {
            log.warn("Failed sub-issue sync: project={}", safeProjectPath, e);
            return SyncResult.abortedError(0);
        }
    }

    @SuppressWarnings("unchecked")
    private int processWorkItemNode(
        Map<String, Object> node,
        Map<Integer, Issue> issueByIid,
        Set<Long> issuesWithParentInGitLab,
        Repository repository
    ) {
        String iidStr = node.get("iid") != null ? node.get("iid").toString() : null;
        if (iidStr == null) return 0;

        int iid;
        try {
            iid = Integer.parseInt(iidStr);
        } catch (NumberFormatException e) {
            return 0;
        }

        Issue issue = issueByIid.get(iid);
        if (issue == null) return 0;

        // Extract hierarchy widget
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) node.get("widgets");
        if (widgets == null) return 0;

        Integer parentIid = null;
        String parentNamespacePath = null;
        for (Map<String, Object> widget : widgets) {
            String widgetType = (String) widget.get("type");
            if ("HIERARCHY".equals(widgetType)) {
                Map<String, Object> parent = (Map<String, Object>) widget.get("parent");
                if (parent != null) {
                    parentIid = extractIid(parent);
                    parentNamespacePath = extractNamespacePath(parent);
                }
                break;
            }
        }

        if (parentIid != null) {
            issuesWithParentInGitLab.add(issue.getId());
            Issue parentIssue = issueByIid.get(parentIid);
            if (parentIssue == null) {
                // Try same-repo DB lookup first
                parentIssue = issueRepository.findByRepositoryIdAndNumber(repository.getId(), parentIid).orElse(null);
            }
            if (parentIssue == null && parentNamespacePath != null) {
                // Cross-repo: look up parent in a different monitored repo by namespace path
                parentIssue = findCrossRepoIssue(parentNamespacePath, parentIid, repository);
            }

            if (parentIssue != null && !parentIssue.getId().equals(issue.getId())) {
                if (issue.getParentIssue() == null || !issue.getParentIssue().getId().equals(parentIssue.getId())) {
                    issue.setParentIssue(parentIssue);
                    issueRepository.save(issue);
                    return 1;
                }
            }
        }

        return 0;
    }

    /**
     * Clears parentIssue for issues that had a parent locally but no longer have one in GitLab.
     */
    private int clearStaleParents(List<Issue> issues, Set<Long> issuesWithParentInGitLab) {
        int cleared = 0;
        for (Issue issue : issues) {
            if (issue.getParentIssue() != null && !issuesWithParentInGitLab.contains(issue.getId())) {
                issue.setParentIssue(null);
                issueRepository.save(issue);
                cleared++;
            }
        }
        return cleared;
    }

    /**
     * Looks up an issue in a different monitored repository by its namespace path (fullPath) and IID.
     */
    @Nullable
    private Issue findCrossRepoIssue(String namespacePath, int iid, Repository currentRepo) {
        Repository crossRepo = repositoryRepository.findByNameWithOwner(namespacePath).orElse(null);
        if (crossRepo == null || crossRepo.getId().equals(currentRepo.getId())) {
            return null;
        }
        return issueRepository.findByRepositoryIdAndNumber(crossRepo.getId(), iid).orElse(null);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static String extractNamespacePath(Map<String, Object> parentMap) {
        Object ns = parentMap.get("namespace");
        if (ns instanceof Map<?, ?> nsMap) {
            Object fullPath = nsMap.get("fullPath");
            return fullPath != null ? fullPath.toString() : null;
        }
        return null;
    }

    @Nullable
    private static Integer extractIid(Map<String, Object> map) {
        Object iid = map.get("iid");
        if (iid instanceof Number n) return n.intValue();
        if (iid instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
