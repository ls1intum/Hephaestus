package de.tum.cit.aet.hephaestus.integration.scm.gitlab.subissue;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResult;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemEvents;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.scm.events.RepositoryRef;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlResponseHandler;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabSyncConstants;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabSyncException;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.graphql.GitLabPageInfo;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace.GitLabProjectAdmission;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;

/**
 * Syncs sub-issue (parent-child) relationships for GitLab issues.
 * <p>
 * Uses the GitLab GraphQL Work Item hierarchy API to fetch parent-child
 * relationships. GitLab models hierarchy through WorkItem widgets, not
 * through issue links (the REST issue links API does not support
 * {@code is_child_of}/{@code is_parent_of} — those types do not exist).
 * <p>
 * Uses a single paginated GraphQL query per repository.
 * <p>
 * Includes stale parent cleanup: if a parent-child relationship is
 * removed in GitLab, the local DB is updated to clear the parent.
 * <p>
 * No webhook support — runs only during scheduled sync.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.gitlab.enabled", havingValue = "true", matchIfMissing = false)
public class GitLabSubIssueSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabSubIssueSyncService.class);
    private static final String DOCUMENT_NAME = "GetProjectWorkItemHierarchy";
    private static final int PAGE_SIZE = 50;

    private final IssueRepository issueRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabGraphQlResponseHandler responseHandler;
    private final GitLabProjectAdmission admission;
    private final ApplicationEventPublisher events;

    public GitLabSubIssueSyncService(
            IssueRepository issueRepository,
            RepositoryRepository repositoryRepository,
            GitLabGraphQlClientProvider graphQlClientProvider,
            GitLabGraphQlResponseHandler responseHandler,
            GitLabProjectAdmission admission,
            ApplicationEventPublisher events) {
        this.issueRepository = issueRepository;
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.responseHandler = responseHandler;
        this.admission = admission;
        this.events = events;
    }

    /**
     * Syncs parent-child relationships for all issues in a repository
     * using the GitLab GraphQL WorkItem hierarchy API.
     *
     * @param scopeId    workspace scope ID
     * @param repository the repository to sync sub-issues for
     * @return sync result
     */
    public SyncResult syncSubIssuesForRepository(Long scopeId, Repository repository) {
        return admission
                .capture(scopeId, repository)
                .map(read -> syncSubIssuesForRepository(scopeId, repository, read))
                .orElseGet(() -> SyncResult.completed(0));
    }

    public SyncResult syncSubIssuesForRepository(
            Long scopeId, Repository repository, GitLabProjectAdmission.ProjectRead read) {
        String projectPath = repository.getNameWithOwner();
        String safeProjectPath = Objects.requireNonNullElse(sanitizeForLog(projectPath), "<unknown>");

        // Issues only: GitLab keeps issue IIDs and merge-request IIDs in separate per-project
        // namespaces (issue #5 and MR !5 coexist), and both live in the single-table `issue` table. The
        // polymorphic list would collide the two in issueByIid (last-writer-wins) and let a merge request
        // be mistaken for the issue of the same IID — writing a bogus parent onto the MR while the real
        // issue never gets parented, and clearStaleParents clearing the real issue's legitimate parent.
        // Sub-issue parenting is an issue-domain concept a merge request has no part in.
        List<Issue> issues = issueRepository.findAllIssuesByRepositoryId(repository.getId());
        if (issues.isEmpty()) return SyncResult.completed(0);

        // Track which issues have a parent according to GitLab
        Set<Long> issuesWithParentInGitLab = new HashSet<>();
        Set<Long> unknownHierarchyIssueIds = new HashSet<>();
        int totalLinked = 0;

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        String cursor = null;
        String previousCursor = null;
        int page = 0;
        boolean complete = false;

        try {
            do {
                if (page >= GitLabSyncConstants.MAX_PAGINATION_PAGES) {
                    log.warn("Reached max pagination: project={}", safeProjectPath);
                    break;
                }

                graphQlClientProvider.acquirePermission();

                ClientGraphQlResponse response = client.documentName(DOCUMENT_NAME)
                        .variable("fullPath", projectPath)
                        .variable("first", PAGE_SIZE)
                        .variable("after", cursor)
                        .execute()
                        .block(Duration.ofSeconds(30));

                var handleResult = responseHandler.handle(response, "sub-issues for " + safeProjectPath, log);
                if (handleResult.action() == GitLabGraphQlResponseHandler.HandleResult.Action.RETRY) {
                    continue;
                }
                if (handleResult.action() == GitLabGraphQlResponseHandler.HandleResult.Action.ABORT) {
                    graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                    break;
                }
                if (!Objects.requireNonNull(response).getErrors().isEmpty()
                        || !responseHandler.isWholePage(response, "project.workItems")) {
                    break;
                }

                // The nodes as GitLab sent them: decoding them into maps again would drop an explicit
                // "parent": null. A page with an unreadable node applies none of its nodes.
                var nodesField = response.field("project.workItems.nodes");
                if (!(nodesField.getValue() instanceof List<?> workItems)
                        || !workItems.stream().allMatch(node -> node instanceof Map<?, ?>)) {
                    break;
                }
                List<Map<String, Object>> nodes = Objects.requireNonNull(nodesField.getValue());

                Integer linked = admission
                        .write(read, () -> {
                            Map<Integer, Issue> issueByIid = new HashMap<>();
                            for (Issue issue : issueRepository.findAllIssuesByRepositoryId(repository.getId())) {
                                if (issue.getNumber() > 0) issueByIid.put(issue.getNumber(), issue);
                            }
                            int count = 0;
                            for (Map<String, Object> node : nodes) {
                                count += processWorkItemNode(
                                        node,
                                        issueByIid,
                                        issuesWithParentInGitLab,
                                        unknownHierarchyIssueIds,
                                        repository,
                                        scopeId);
                            }
                            return count;
                        })
                        .orElse(null);
                if (linked == null) return SyncResult.abortedError(totalLinked);
                totalLinked += linked;

                // Pagination
                GitLabPageInfo pageInfo = Objects.requireNonNull(
                        response.field("project.workItems.pageInfo").toEntity(GitLabPageInfo.class));
                if (!pageInfo.hasNextPage()) {
                    complete = true;
                    break;
                }
                cursor = pageInfo.endCursor();
                if (cursor == null
                        || cursor.isBlank()
                        || responseHandler.isPaginationLoop(
                                cursor, previousCursor, "sub-issues for " + safeProjectPath, log)) {
                    break;
                }
                previousCursor = cursor;
                page++;
            } while (true);

            // Unfetched issues may hide real parents. Cleanup requires a whole, error-free final page;
            // identified issues with unreadable hierarchy remain protected individually.
            if (!complete) {
                log.warn("Skipping stale-parent cleanup after an incomplete walk: project={}", safeProjectPath);
                return SyncResult.abortedError(totalLinked);
            }
            int staleCleared = admission
                    .write(
                            read,
                            () -> clearStaleParents(
                                    issueRepository.findAllIssuesByRepositoryId(repository.getId()),
                                    issuesWithParentInGitLab,
                                    unknownHierarchyIssueIds,
                                    scopeId))
                    .orElse(0);

            if (totalLinked > 0 || staleCleared > 0) {
                log.info(
                        "GitLab sub-issue sync: project={}, linked={}, staleCleared={}",
                        safeProjectPath,
                        totalLinked,
                        staleCleared);
            }

            if (!admission.current(read)) return SyncResult.completed(0);
            return unknownHierarchyIssueIds.isEmpty()
                    ? SyncResult.completed(totalLinked)
                    : SyncResult.abortedError(totalLinked);
        } catch (Exception e) {
            log.warn("Failed sub-issue sync: project={}", safeProjectPath, e);
            return SyncResult.abortedError(totalLinked);
        } finally {
            if (!unknownHierarchyIssueIds.isEmpty()) {
                log.warn(
                        "Incomplete sub-issue sync: project={}, unknownHierarchyItems={}, reason=unreadableHierarchy",
                        safeProjectPath,
                        unknownHierarchyIssueIds.size());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private int processWorkItemNode(
            Map<String, Object> node,
            Map<Integer, Issue> issueByIid,
            Set<Long> issuesWithParentInGitLab,
            Set<Long> unknownHierarchyIssueIds,
            Repository repository,
            Long scopeId) {
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
        if (widgets == null) {
            unknownHierarchyIssueIds.add(issue.getId());
            return 0;
        }

        Integer parentIid = null;
        String parentNamespacePath = null;
        for (Map<String, Object> widget : widgets) {
            String widgetType = (String) widget.get("type");
            if ("HIERARCHY".equals(widgetType)) {
                if (!widget.containsKey("parent")) {
                    unknownHierarchyIssueIds.add(issue.getId());
                    return 0;
                }
                Map<String, Object> parent = (Map<String, Object>) widget.get("parent");
                Object hasParent = widget.get("hasParent");
                if (Boolean.FALSE.equals(hasParent) && parent == null) {
                    return 0;
                }
                if (!Boolean.TRUE.equals(hasParent) || parent == null) {
                    unknownHierarchyIssueIds.add(issue.getId());
                    return 0;
                }
                parentIid = extractIid(parent);
                parentNamespacePath = extractNamespacePath(parent);
                break;
            }
        }

        if (parentIid == null || parentIid <= 0 || parentNamespacePath == null || parentNamespacePath.isBlank()) {
            unknownHierarchyIssueIds.add(issue.getId());
            return 0;
        }
        issuesWithParentInGitLab.add(issue.getId());
        Issue parentIssue;
        if (parentNamespacePath.equals(repository.getNameWithOwner())) {
            parentIssue = issueByIid.get(parentIid);
            if (parentIssue == null) {
                parentIssue = issueRepository
                        .findByRepositoryIdAndNumber(repository.getId(), parentIid)
                        .orElse(null);
            }
        } else {
            parentIssue = findCrossRepoIssue(parentNamespacePath, parentIid, repository);
        }
        if (parentIssue == null) {
            unknownHierarchyIssueIds.add(issue.getId());
            return 0;
        }

        if (!parentIssue.getId().equals(issue.getId())) {
            if (issue.getParentIssue() == null
                    || !issue.getParentIssue().getId().equals(parentIssue.getId())) {
                var previous = issue.getParentIssue();
                issue.setParentIssue(parentIssue);
                issueRepository.saveAndFlush(issue);
                if (previous != null) publishProgress(previous, scopeId);
                publishProgress(parentIssue, scopeId);
                return 1;
            }
        }

        return 0;
    }

    /**
     * Clears stored parents absent from a complete inventory, except issues with unreadable hierarchy.
     */
    private int clearStaleParents(
            List<Issue> issues, Set<Long> issuesWithParentInGitLab, Set<Long> unknownHierarchyIssueIds, Long scopeId) {
        int cleared = 0;
        for (Issue issue : issues) {
            if (issue.getParentIssue() != null
                    && !issuesWithParentInGitLab.contains(issue.getId())
                    && !unknownHierarchyIssueIds.contains(issue.getId())) {
                var previous = Objects.requireNonNull(issue.getParentIssue());
                issue.setParentIssue(null);
                issueRepository.saveAndFlush(issue);
                publishProgress(previous, scopeId);
                cleared++;
            }
        }
        return cleared;
    }

    private void publishProgress(Issue parent, Long scopeId) {
        var context = EventContext.forSync(
                scopeId, RepositoryRef.from(parent.getRepository()), IdentityProviderType.GITLAB, Instant.now());
        events.publishEvent(new ScmDomainEvent.IssueUpdated(
                ScmEventPayload.IssueData.from(parent), Set.of(ScmItemEvents.ISSUE_PROGRESS_FIELD), context));
    }

    /**
     * Looks up an issue in a different repository on the current provider by namespace path and IID.
     */
    @Nullable
    private Issue findCrossRepoIssue(String namespacePath, int iid, Repository currentRepo) {
        Repository crossRepo = repositoryRepository
                .findByNameWithOwnerAndProviderId(
                        namespacePath,
                        Objects.requireNonNull(currentRepo.getProvider().getId()))
                .orElse(null);
        if (crossRepo == null || crossRepo.getId().equals(currentRepo.getId())) {
            return null;
        }
        return issueRepository
                .findByRepositoryIdAndNumber(crossRepo.getId(), iid)
                .orElse(null);
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
