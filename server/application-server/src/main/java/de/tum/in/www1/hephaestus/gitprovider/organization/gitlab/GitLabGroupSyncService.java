package de.tum.in.www1.hephaestus.gitprovider.organization.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.MAX_PAGINATION_PAGES;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.adaptPageSize;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncException;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupResponse;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabProjectResponse;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.gitlab.GitLabProjectProcessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for syncing GitLab groups and their projects via GraphQL API.
 * <p>
 * This service:
 * <ul>
 *   <li>Fetches group metadata via {@code GetGroup} query → delegates to {@link GitLabGroupProcessor}</li>
 *   <li>Fetches all projects in a group via paginated {@code GetGroupProjects} query</li>
 *   <li>Delegates each project to {@link GitLabProjectProcessor} for entity mapping</li>
 * </ul>
 * <p>
 * Rate limit awareness is handled by checking the rate limit tracker between pagination
 * pages and adjusting page size when the budget is low.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabGroupSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabGroupSyncService.class);

    private static final String GET_GROUP_DOCUMENT = "GetGroup";
    private static final String GET_GROUP_PROJECTS_DOCUMENT = "GetGroupProjects";

    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabGroupProcessor groupProcessor;
    private final GitLabProjectProcessor projectProcessor;
    private final GitLabProperties gitLabProperties;
    private final GitProviderRepository gitProviderRepository;

    public GitLabGroupSyncService(
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabGroupProcessor groupProcessor,
        GitLabProjectProcessor projectProcessor,
        GitLabProperties gitLabProperties,
        GitProviderRepository gitProviderRepository
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.groupProcessor = groupProcessor;
        this.projectProcessor = projectProcessor;
        this.gitLabProperties = gitLabProperties;
        this.gitProviderRepository = gitProviderRepository;
    }

    /**
     * Resolves the GitLab provider entity from the database.
     *
     * @return the GitLab provider
     * @throws IllegalStateException if no GitLab provider is found
     */
    private GitProvider resolveProvider() {
        return gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, gitLabProperties.defaultServerUrl())
            .orElseThrow(() ->
                new IllegalStateException(
                    "GitProvider not found for type=GITLAB, serverUrl=" + gitLabProperties.defaultServerUrl()
                )
            );
    }

    /**
     * Syncs a single GitLab group (without projects) from the GraphQL API.
     *
     * @param scopeId       the workspace/scope ID for authentication
     * @param groupFullPath the full path of the group (e.g., {@code org/team})
     * @return the synced Organization entity, or empty if not found or on error
     */
    @Transactional
    public Optional<Organization> syncGroup(Long scopeId, String groupFullPath) {
        if (groupFullPath == null || groupFullPath.isBlank()) {
            log.warn("Skipped group sync: reason=nullOrBlankGroupPath, scopeId={}", scopeId);
            return Optional.empty();
        }
        String safeGroupPath = sanitizeForLog(groupFullPath);

        try {
            GitProvider provider = resolveProvider();
            Long providerId = provider.getId();
            graphQlClientProvider.acquirePermission();
            HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

            ClientGraphQlResponse response = client
                .documentName(GET_GROUP_DOCUMENT)
                .variable("fullPath", groupFullPath)
                .execute()
                .block(gitLabProperties.graphqlTimeout());

            if (response == null || !response.isValid()) {
                log.warn(
                    "Failed to fetch group: scopeId={}, groupPath={}, errors={}",
                    scopeId,
                    safeGroupPath,
                    response != null ? response.getErrors() : "null response"
                );
                graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                return Optional.empty();
            }

            graphQlClientProvider.recordSuccess();

            GitLabGroupResponse group = response.field("group").toEntity(GitLabGroupResponse.class);
            if (group == null) {
                log.warn(
                    "Skipped group sync: reason=notFoundOnGitLab, scopeId={}, groupPath={}",
                    scopeId,
                    safeGroupPath
                );
                return Optional.empty();
            }

            Organization organization = groupProcessor.process(group, providerId);
            if (organization != null) {
                log.info(
                    "Synced group: scopeId={}, orgId={}, groupPath={}",
                    scopeId,
                    organization.getId(),
                    safeGroupPath
                );
            }
            return Optional.ofNullable(organization);
        } catch (Exception e) {
            graphQlClientProvider.recordFailure(e);
            log.error("Failed to sync group: scopeId={}, groupPath={}", scopeId, safeGroupPath, e);
            return Optional.empty();
        }
    }

    /**
     * Syncs all projects in a GitLab group (including subgroups) using cursor-based pagination.
     * <p>
     * For each project, the immediate parent group is resolved and persisted as an Organization.
     * Subgroup projects are included automatically — each project's {@code group} field provides
     * the immediate parent, which is upserted independently of the top-level group.
     * <p>
     * Rate limits are tracked between pages and page size adapts to remaining budget.
     * Individual project failures do not abort the sync — they are counted in the result.
     *
     * @param scopeId       the workspace/scope ID for authentication
     * @param groupFullPath the full path of the group
     * @return structured result with synced repositories, page counts, and error counts
     */
    // Note: intentionally NOT @Transactional — this is an orchestrator that makes multiple
    // API calls with throttle delays between pages. Each project upsert and the group sync
    // have their own @Transactional boundaries, so we don't hold a DB connection during
    // network calls or Thread.sleep().
    public GitLabSyncResult syncGroupProjects(Long scopeId, String groupFullPath) {
        if (groupFullPath == null || groupFullPath.isBlank()) {
            log.warn("Skipped group projects sync: reason=nullOrBlankGroupPath, scopeId={}", scopeId);
            return GitLabSyncResult.aborted(GitLabSyncResult.Status.ABORTED_ERROR, Collections.emptyList(), 0, 0);
        }
        String safeGroupPath = sanitizeForLog(groupFullPath);

        // Top-level organization is extracted from the first page response (group fields are
        // inlined in GetGroupProjects query), eliminating a separate API call.
        Organization topLevelOrganization = null;

        List<Repository> syncedRepositories = new ArrayList<>();
        String cursor = null;
        int pageCount = 0;
        int projectsSkipped = 0;
        boolean hadApiFailure = false;

        try {
            GitProvider provider = resolveProvider();
            Long providerId = provider.getId();
            graphQlClientProvider.acquirePermission();

            do {
                // Rate limit values are automatically updated by the WebClient exchange filter
                // that parses RateLimit-* response headers per-request.
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    try {
                        graphQlClientProvider.waitIfRateLimitLow(scopeId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while waiting for rate limit reset: scopeId={}", scopeId);
                        return GitLabSyncResult.aborted(
                            GitLabSyncResult.Status.ABORTED_RATE_LIMIT,
                            syncedRepositories,
                            pageCount,
                            projectsSkipped
                        );
                    }
                }

                int pageSize = adaptPageSize(DEFAULT_PAGE_SIZE, graphQlClientProvider.getRateLimitRemaining(scopeId));
                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(GET_GROUP_PROJECTS_DOCUMENT)
                    .variable("fullPath", groupFullPath)
                    .variable("first", pageSize)
                    .variable("after", cursor)
                    .variable("includeSubgroups", true)
                    .execute()
                    .block(gitLabProperties.extendedGraphqlTimeout());

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Failed to fetch group projects page: scopeId={}, groupPath={}, page={}, errors={}",
                        scopeId,
                        safeGroupPath,
                        pageCount,
                        response != null ? response.getErrors() : "null response"
                    );
                    graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                    hadApiFailure = true;
                    break;
                }

                graphQlClientProvider.recordSuccess();

                // On the first page, extract and persist the top-level group from inlined fields
                if (pageCount == 0) {
                    GitLabGroupResponse groupData = response.field("group").toEntity(GitLabGroupResponse.class);
                    if (groupData != null) {
                        topLevelOrganization = groupProcessor.process(groupData, providerId);
                    }
                    if (topLevelOrganization == null) {
                        log.warn(
                            "Failed to resolve group on first page, aborting sync: scopeId={}, groupPath={}",
                            scopeId,
                            safeGroupPath
                        );
                        hadApiFailure = true;
                        break;
                    }
                    log.info(
                        "Synced group: scopeId={}, orgId={}, groupPath={}",
                        scopeId,
                        topLevelOrganization.getId(),
                        safeGroupPath
                    );
                }

                // Parse project nodes — each project may belong to a different subgroup
                List<GitLabProjectResponse> projects = response
                    .field("group.projects.nodes")
                    .toEntityList(GitLabProjectResponse.class);

                for (GitLabProjectResponse project : projects) {
                    try {
                        // Resolve the project's immediate parent group (may differ from top-level)
                        Organization projectOrg = resolveProjectOrganization(project, topLevelOrganization, providerId);
                        Repository repo = projectProcessor.processGraphQlResponse(project, projectOrg, provider);
                        if (repo != null) {
                            syncedRepositories.add(repo);
                        } else {
                            projectsSkipped++;
                        }
                    } catch (Exception e) {
                        projectsSkipped++;
                        log.warn(
                            "Failed to process project: fullPath={}, error={}",
                            sanitizeForLog(project.fullPath()),
                            e.getMessage()
                        );
                    }
                }

                // Check pagination
                GitLabPageInfo pageInfo = response.field("group.projects.pageInfo").toEntity(GitLabPageInfo.class);

                if (pageInfo == null || !pageInfo.hasNextPage()) {
                    break;
                }

                cursor = pageInfo.endCursor();
                if (cursor == null) {
                    log.warn(
                        "Pagination cursor is null despite hasNextPage=true: groupPath={}, page={}",
                        safeGroupPath,
                        pageCount
                    );
                    break;
                }
                pageCount++;

                // Throttle between pages to respect rate limits
                Thread.sleep(gitLabProperties.paginationThrottle().toMillis());
            } while (pageCount < MAX_PAGINATION_PAGES);

            int totalPages = pageCount + 1;

            // Warn if pagination was truncated (more pages exist but we hit the safety limit)
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Pagination truncated at {} pages for group: scopeId={}, groupPath={}, syncedSoFar={}",
                    MAX_PAGINATION_PAGES,
                    scopeId,
                    safeGroupPath,
                    syncedRepositories.size()
                );
                return GitLabSyncResult.aborted(
                    GitLabSyncResult.Status.ABORTED_ERROR,
                    syncedRepositories,
                    totalPages,
                    projectsSkipped
                );
            }

            log.info(
                "Synced group projects: scopeId={}, groupPath={}, projectCount={}, skipped={}, pages={}",
                scopeId,
                safeGroupPath,
                syncedRepositories.size(),
                projectsSkipped,
                totalPages
            );

            // API failure during pagination → report as aborted, not completed
            if (hadApiFailure) {
                return GitLabSyncResult.aborted(
                    GitLabSyncResult.Status.ABORTED_ERROR,
                    syncedRepositories,
                    totalPages,
                    projectsSkipped
                );
            }
            if (projectsSkipped > 0) {
                return GitLabSyncResult.withErrors(syncedRepositories, totalPages, projectsSkipped);
            }
            return GitLabSyncResult.completed(syncedRepositories, totalPages);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted during group project sync: scopeId={}, groupPath={}", scopeId, safeGroupPath);
            return GitLabSyncResult.aborted(
                GitLabSyncResult.Status.ABORTED_ERROR,
                syncedRepositories,
                pageCount,
                projectsSkipped
            );
        } catch (Exception e) {
            graphQlClientProvider.recordFailure(e);
            log.error(
                "Failed to sync group projects: scopeId={}, groupPath={}, syncedSoFar={}",
                scopeId,
                safeGroupPath,
                syncedRepositories.size(),
                e
            );
            return GitLabSyncResult.aborted(
                GitLabSyncResult.Status.ABORTED_ERROR,
                syncedRepositories,
                pageCount,
                projectsSkipped
            );
        }
    }

    /**
     * Resolves the Organization for a project.
     * <p>
     * If the project has an inline {@code group} field (from GetGroupProjects with subgroups),
     * that group is upserted as the project's organization. Otherwise, the top-level group
     * is used as fallback. This correctly handles nested group hierarchies like
     * {@code org/team/subteam/project}.
     */
    private Organization resolveProjectOrganization(
        GitLabProjectResponse project,
        Organization topLevelOrganization,
        Long providerId
    ) {
        if (project.group() != null) {
            Organization subOrg = groupProcessor.process(project.group(), providerId);
            if (subOrg != null) {
                return subOrg;
            }
        }
        return topLevelOrganization;
    }
}
