package de.tum.in.www1.hephaestus.gitprovider.organization.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.MAX_PAGINATION_PAGES;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.adaptPageSize;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.extractNumericId;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlResponseHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncException;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupMemberResponse;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupMemberResponse.GitLabAccessLevel;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupMemberResponse.GitLabMemberUser;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener.OrganizationSyncedEvent;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMemberRole;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service for synchronizing GitLab group memberships via GraphQL API.
 * <p>
 * Closes the security gap where users removed from a GitLab group retain
 * workspace access indefinitely. This is the GitLab counterpart of the
 * membership sync in {@code GitHubOrganizationSyncService}.
 * <p>
 * The sync populates {@code OrganizationMembership} records and fires
 * {@link OrganizationMembershipListener#onOrganizationMembershipsSynced}
 * so downstream modules (e.g., workspace) can reconcile their member lists.
 * <p>
 * <b>Concurrency:</b> User upserts follow the same pattern as
 * {@code GitHubUserProcessor}: advisory lock → free login conflicts → native SQL
 * upsert, all within an isolated REQUIRES_NEW transaction to prevent unique
 * constraint violations on login renames.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabGroupMemberSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabGroupMemberSyncService.class);
    private static final String GET_GROUP_MEMBERS_DOCUMENT = "GetGroupMembers";

    /**
     * GitLab access levels at or above this threshold map to {@link OrganizationMemberRole#ADMIN}.
     * MAINTAINER(40) and OWNER(50) are considered admins.
     */
    private static final int ADMIN_ACCESS_LEVEL_THRESHOLD = 40;

    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabGraphQlResponseHandler responseHandler;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final UserRepository userRepository;
    private final GitProviderRepository gitProviderRepository;
    private final GitLabProperties gitLabProperties;
    private final OrganizationMembershipListener organizationMembershipListener;
    private final TransactionTemplate requiresNewTransaction;

    public GitLabGroupMemberSyncService(
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabGraphQlResponseHandler responseHandler,
        OrganizationMembershipRepository organizationMembershipRepository,
        UserRepository userRepository,
        GitProviderRepository gitProviderRepository,
        GitLabProperties gitLabProperties,
        @Nullable OrganizationMembershipListener organizationMembershipListener,
        TransactionTemplate transactionTemplate
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.responseHandler = responseHandler;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.userRepository = userRepository;
        this.gitProviderRepository = gitProviderRepository;
        this.gitLabProperties = gitLabProperties;
        this.organizationMembershipListener = organizationMembershipListener;
        // Isolated transaction for each user upsert — matches GitHubUserProcessor pattern.
        this.requiresNewTransaction = new TransactionTemplate(transactionTemplate.getTransactionManager());
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Syncs all memberships for a GitLab group.
     * <p>
     * Paginates through the {@code GetGroupMembers} GraphQL query, upserts each
     * member as a User + OrganizationMembership, removes stale memberships,
     * and fires the organization-synced event.
     * <p>
     * Circuit breaker permission and rate limit checks are performed per page,
     * matching the canonical pattern in {@code GitLabGroupSyncService.reconcileDirectProjects}.
     *
     * @param scopeId       the workspace/scope ID for authentication
     * @param groupFullPath the full path of the group (e.g., "org/team")
     * @param organization  the Organization entity for this group
     * @return the number of unique members synced, or -1 on failure
     */
    public int syncGroupMemberships(Long scopeId, String groupFullPath, Organization organization) {
        if (organization == null || groupFullPath == null || groupFullPath.isBlank()) {
            log.warn(
                "Skipped group membership sync: reason=missingArgs, scopeId={}, groupPath={}",
                scopeId,
                groupFullPath != null ? sanitizeForLog(groupFullPath) : "null"
            );
            return -1;
        }

        String safeGroupPath = sanitizeForLog(groupFullPath);
        GitProvider provider = resolveProvider();
        Long providerId = provider.getId();

        Set<Long> syncedUserIds = new HashSet<>();
        String cursor = null;
        String previousCursor = null;
        int pageCount = 0;
        boolean syncCompletedNormally = false;

        try {
            do {
                // Per-page circuit breaker + rate limit wait
                // (matches reconcileDirectProjects canonical pattern)
                graphQlClientProvider.acquirePermission();
                try {
                    graphQlClientProvider.waitIfRateLimitLow(scopeId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn(
                        "Interrupted during rate limit wait: context=groupMemberSync, groupPath={}",
                        safeGroupPath
                    );
                    break;
                }

                int pageSize = adaptPageSize(DEFAULT_PAGE_SIZE, graphQlClientProvider.getRateLimitRemaining(scopeId));
                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(GET_GROUP_MEMBERS_DOCUMENT)
                    .variable("fullPath", groupFullPath)
                    .variable("first", pageSize)
                    .variable("after", cursor)
                    .execute()
                    .block(gitLabProperties.graphqlTimeout());

                var handleResult = responseHandler.handle(response, "group members for " + safeGroupPath, log);
                if (handleResult.action() == GitLabGraphQlResponseHandler.HandleResult.Action.RETRY) {
                    continue;
                }
                if (handleResult.action() == GitLabGraphQlResponseHandler.HandleResult.Action.ABORT) {
                    graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                    break;
                }

                graphQlClientProvider.recordSuccess();

                List<GitLabGroupMemberResponse> members = response
                    .field("group.groupMembers.nodes")
                    .toEntityList(GitLabGroupMemberResponse.class);

                for (GitLabGroupMemberResponse member : members) {
                    if (member == null || member.user() == null) {
                        continue;
                    }

                    Long userId = upsertUser(member.user(), providerId);
                    if (userId == null) {
                        continue;
                    }

                    OrganizationMemberRole role = mapAccessLevel(member.accessLevel());
                    organizationMembershipRepository.upsertMembership(organization.getId(), userId, role);
                    syncedUserIds.add(userId);
                }

                // Check pagination
                GitLabPageInfo pageInfo = response.field("group.groupMembers.pageInfo").toEntity(GitLabPageInfo.class);

                if (pageInfo == null || !pageInfo.hasNextPage()) {
                    syncCompletedNormally = true;
                    break;
                }

                cursor = pageInfo.endCursor();
                if (cursor == null) {
                    log.warn(
                        "Pagination cursor null despite hasNextPage=true: context=groupMemberSync, groupPath={}, page={}",
                        safeGroupPath,
                        pageCount
                    );
                    break;
                }
                if (
                    responseHandler.isPaginationLoop(cursor, previousCursor, "group members for " + safeGroupPath, log)
                ) {
                    break;
                }
                previousCursor = cursor;

                pageCount++;
                Thread.sleep(gitLabProperties.paginationThrottle().toMillis());
            } while (pageCount < MAX_PAGINATION_PAGES);

            // Remove stale memberships only if sync completed fully
            if (syncCompletedNormally) {
                removeStaleMemberships(organization, syncedUserIds);
            } else {
                log.warn(
                    "Skipped stale membership removal: reason=incompleteSync, groupPath={}, pagesProcessed={}",
                    safeGroupPath,
                    pageCount + 1
                );
            }

            // Fire sync event only on complete sync — downstream reconciliation
            // should not run on partial data to avoid incorrect member removal.
            if (syncCompletedNormally && organizationMembershipListener != null) {
                organizationMembershipListener.onOrganizationMembershipsSynced(
                    new OrganizationSyncedEvent(organization.getId(), organization.getLogin())
                );
            }

            log.info(
                "Synced group memberships: scopeId={}, groupPath={}, memberCount={}, pages={}, complete={}",
                scopeId,
                safeGroupPath,
                syncedUserIds.size(),
                pageCount + 1,
                syncCompletedNormally
            );

            return syncedUserIds.size();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted during group membership sync: groupPath={}", safeGroupPath);
            return -1;
        } catch (Exception e) {
            graphQlClientProvider.recordFailure(e);
            log.error("Failed to sync group memberships: scopeId={}, groupPath={}", scopeId, safeGroupPath, e);
            return -1;
        }
    }

    /**
     * Upserts a User entity for the given GitLab member using native SQL.
     * <p>
     * Runs in a REQUIRES_NEW transaction following the concurrency-safe pattern
     * from {@code GitHubUserProcessor}: advisory lock → free login conflicts → upsert.
     * This prevents unique constraint violations when a GitLab user renames their
     * username and another user previously held that login.
     *
     * @param memberUser the GitLab member user data
     * @param providerId the GitLab provider's database ID
     * @return the user's database ID, or null if the GID is invalid
     */
    @Nullable
    Long upsertUser(GitLabMemberUser memberUser, Long providerId) {
        long nativeId;
        try {
            nativeId = extractNumericId(memberUser.id());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid GitLab user GID: gid={}, error={}", memberUser.id(), e.getMessage());
            return null;
        }

        String login = memberUser.username();
        String name = memberUser.name();
        String avatarUrl = memberUser.avatarUrl() != null ? memberUser.avatarUrl() : "";
        String htmlUrl = memberUser.webUrl() != null ? memberUser.webUrl() : "";

        requiresNewTransaction.executeWithoutResult(status -> {
            boolean locked = userRepository.tryAcquireLoginLock(login, providerId);
            if (locked) {
                userRepository.freeLoginConflicts(login, nativeId, providerId);
            } else {
                log.debug("Could not acquire advisory lock for login={}, proceeding with upsert", login);
            }
            userRepository.upsertUser(
                nativeId,
                providerId,
                login,
                name,
                avatarUrl,
                htmlUrl,
                User.Type.USER.name(),
                null,
                null,
                null
            );
        });

        // Load the persisted entity to get the database-assigned ID.
        // The REQUIRES_NEW transaction has committed, so this query sees the upserted row.
        return userRepository.findByNativeIdAndProviderId(nativeId, providerId).map(User::getId).orElse(null);
    }

    /**
     * Maps a GitLab access level to a provider-agnostic organization member role.
     * <p>
     * OWNER(50) and MAINTAINER(40) → ADMIN; DEVELOPER(30), REPORTER(20),
     * PLANNER(15), GUEST(10), MINIMAL_ACCESS(5) → MEMBER.
     */
    static OrganizationMemberRole mapAccessLevel(@Nullable GitLabAccessLevel accessLevel) {
        if (accessLevel == null || accessLevel.integerValue() == null) {
            return OrganizationMemberRole.MEMBER;
        }
        if (accessLevel.integerValue() >= ADMIN_ACCESS_LEVEL_THRESHOLD) {
            return OrganizationMemberRole.ADMIN;
        }
        return OrganizationMemberRole.MEMBER;
    }

    private void removeStaleMemberships(Organization organization, Set<Long> syncedUserIds) {
        List<Long> existingUserIds = organizationMembershipRepository.findUserIdsByOrganizationId(organization.getId());

        Set<Long> staleUserIds = new HashSet<>(existingUserIds);
        staleUserIds.removeAll(syncedUserIds);

        if (!staleUserIds.isEmpty()) {
            organizationMembershipRepository.deleteByOrganizationIdAndUserIdIn(organization.getId(), staleUserIds);
            log.info(
                "Removed stale group memberships: orgId={}, groupPath={}, removedCount={}",
                organization.getId(),
                sanitizeForLog(organization.getLogin()),
                staleUserIds.size()
            );
        }
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
}
