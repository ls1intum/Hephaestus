package de.tum.in.www1.hephaestus.gitprovider.organization.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.MAX_PAGINATION_PAGES;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.adaptPageSize;

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

    public GitLabGroupSyncService(
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabGroupProcessor groupProcessor,
        GitLabProjectProcessor projectProcessor,
        GitLabProperties gitLabProperties
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.groupProcessor = groupProcessor;
        this.projectProcessor = projectProcessor;
        this.gitLabProperties = gitLabProperties;
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

            Organization organization = groupProcessor.process(group);
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
     * Syncs all projects in a GitLab group using cursor-based pagination.
     * <p>
     * For each project, the parent group is also persisted as an Organization.
     * Rate limits are tracked between pages, and page size adapts to remaining budget.
     *
     * @param scopeId       the workspace/scope ID for authentication
     * @param groupFullPath the full path of the group
     * @return list of synced Repository entities (may be empty on error)
     */
    // Note: intentionally NOT @Transactional — this is an orchestrator that makes multiple
    // API calls with throttle delays between pages. Each project upsert and the group sync
    // have their own @Transactional boundaries, so we don't hold a DB connection during
    // network calls or Thread.sleep().
    public List<Repository> syncGroupProjects(Long scopeId, String groupFullPath) {
        if (groupFullPath == null || groupFullPath.isBlank()) {
            log.warn("Skipped group projects sync: reason=nullOrBlankGroupPath, scopeId={}", scopeId);
            return Collections.emptyList();
        }
        String safeGroupPath = sanitizeForLog(groupFullPath);

        // Organization is extracted from the first page response (group fields are inlined
        // in GetGroupProjects query), eliminating a separate API call.
        Organization organization = null;

        List<Repository> syncedRepositories = new ArrayList<>();
        String cursor = null;
        int pageCount = 0;

        try {
            graphQlClientProvider.acquirePermission();

            do {
                // Rate limit tracking: values are populated when the WebClient exchange filter
                // is enhanced to call updateRateLimit(scopeId, headers) per-request.
                // Until then, getRateLimitRemaining returns the default budget.
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    graphQlClientProvider.waitIfRateLimitLow(scopeId);
                }

                int pageSize = adaptPageSize(DEFAULT_PAGE_SIZE, graphQlClientProvider.getRateLimitRemaining(scopeId));
                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(GET_GROUP_PROJECTS_DOCUMENT)
                    .variable("fullPath", groupFullPath)
                    .variable("first", pageSize)
                    .variable("after", cursor)
                    .variable("includeSubgroups", false)
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
                    break;
                }

                graphQlClientProvider.recordSuccess();

                // On the first page, extract and persist the group from the inlined fields
                if (pageCount == 0) {
                    GitLabGroupResponse groupData = response.field("group").toEntity(GitLabGroupResponse.class);
                    if (groupData != null) {
                        organization = groupProcessor.process(groupData);
                    }
                    if (organization == null) {
                        log.warn(
                            "Failed to resolve group on first page, aborting sync: scopeId={}, groupPath={}",
                            scopeId,
                            safeGroupPath
                        );
                        break;
                    }
                    log.info(
                        "Synced group: scopeId={}, orgId={}, groupPath={}",
                        scopeId,
                        organization.getId(),
                        safeGroupPath
                    );
                }

                // Parse project nodes
                List<GitLabProjectResponse> projects = response
                    .field("group.projects.nodes")
                    .toEntityList(GitLabProjectResponse.class);

                for (GitLabProjectResponse project : projects) {
                    Repository repo = projectProcessor.processGraphQlResponse(project, organization);
                    if (repo != null) {
                        syncedRepositories.add(repo);
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

            log.info(
                "Synced group projects: scopeId={}, groupPath={}, projectCount={}, pages={}",
                scopeId,
                safeGroupPath,
                syncedRepositories.size(),
                pageCount + 1
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted during group project sync: scopeId={}, groupPath={}", scopeId, safeGroupPath);
        } catch (Exception e) {
            graphQlClientProvider.recordFailure(e);
            log.error(
                "Failed to sync group projects: scopeId={}, groupPath={}, syncedSoFar={}",
                scopeId,
                safeGroupPath,
                syncedRepositories.size(),
                e
            );
        }

        return Collections.unmodifiableList(syncedRepositories);
    }
}
