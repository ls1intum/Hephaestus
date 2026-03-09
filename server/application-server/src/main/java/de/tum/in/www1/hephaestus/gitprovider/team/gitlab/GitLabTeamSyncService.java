package de.tum.in.www1.hephaestus.gitprovider.team.gitlab;

import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.MAX_PAGINATION_PAGES;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.adaptPageSize;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.extractNumericId;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncException;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabDescendantGroupResponse;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupMemberResponse;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembership;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.gitlab.GitLabUserService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Synchronizes GitLab subgroups as teams via GraphQL API.
 * <p>
 * Intentionally NOT @Transactional at the orchestrator level — each phase
 * manages its own transactional boundaries via TransactionTemplate to avoid
 * holding a DB connection during network calls and throttle delays.
 * <p>
 * Uses TransactionTemplate instead of @Transactional because Spring's
 * proxy-based AOP doesn't intercept internal method calls within the same class.
 * <p>
 * Phases:
 * <ol>
 *   <li>A — Fetch descendant groups and create Team entities</li>
 *   <li>B — Resolve parent references (two-pass)</li>
 *   <li>C — Sync direct members per team</li>
 *   <li>D — Sync team-repo permissions (repos whose org = subgroup fullPath)</li>
 *   <li>E — Cleanup stale teams (only if sync completed fully)</li>
 * </ol>
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabTeamSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabTeamSyncService.class);
    private static final String GET_GROUP_DESCENDANTS_DOCUMENT = "GetGroupDescendants";
    private static final String GET_GROUP_MEMBERS_DOCUMENT = "GetGroupMembers";
    private static final int TEAM_PAGE_SIZE = 20;
    private static final int MEMBER_PAGE_SIZE = 100;

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabTeamProcessor teamProcessor;
    private final GitLabUserService gitLabUserService;
    private final GitProviderRepository gitProviderRepository;
    private final GitLabProperties gitLabProperties;
    private final TransactionTemplate transactionTemplate;

    public GitLabTeamSyncService(
        TeamRepository teamRepository,
        TeamMembershipRepository teamMembershipRepository,
        RepositoryRepository repositoryRepository,
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabTeamProcessor teamProcessor,
        GitLabUserService gitLabUserService,
        GitProviderRepository gitProviderRepository,
        GitLabProperties gitLabProperties,
        TransactionTemplate transactionTemplate
    ) {
        this.teamRepository = teamRepository;
        this.teamMembershipRepository = teamMembershipRepository;
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.teamProcessor = teamProcessor;
        this.gitLabUserService = gitLabUserService;
        this.gitProviderRepository = gitProviderRepository;
        this.gitLabProperties = gitLabProperties;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Synchronizes all descendant subgroups of a GitLab group as teams.
     * <p>
     * NOT @Transactional — orchestrator delegates to TransactionTemplate-wrapped helpers
     * so DB connections are not held during API calls or throttle delays.
     *
     * @param scopeId       the workspace/scope ID
     * @param groupFullPath the root group full path (e.g., "ase/introcourse")
     * @return number of teams synced
     */
    public int syncTeamsForGroup(Long scopeId, String groupFullPath) {
        if (groupFullPath == null || groupFullPath.isBlank()) {
            log.warn("Skipped team sync: reason=missingGroupPath, scopeId={}", scopeId);
            return 0;
        }

        GitProvider provider = resolveProvider();
        if (provider == null) {
            log.warn("Skipped team sync: reason=providerNotResolved, scopeId={}", scopeId);
            return 0;
        }
        Long providerId = provider.getId();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

        // Phase A: Fetch descendant groups and create teams
        Map<Long, Team> syncedTeamsByNativeId = new HashMap<>();
        Map<Long, Long> parentNativeIdByChildNativeId = new HashMap<>();
        Map<Long, String> teamFullPathsByNativeId = new HashMap<>();
        Set<Long> syncedNativeIds = new HashSet<>();

        boolean syncCompletedNormally = fetchAndProcessDescendantGroups(
            client,
            scopeId,
            groupFullPath,
            provider,
            syncedTeamsByNativeId,
            parentNativeIdByChildNativeId,
            teamFullPathsByNativeId,
            syncedNativeIds
        );

        int totalSynced = syncedTeamsByNativeId.size();
        log.info("Phase A complete: groupPath={}, teamsFound={}", groupFullPath, totalSynced);

        if (totalSynced == 0) {
            log.info("No descendant groups found: groupPath={}", groupFullPath);
            return 0;
        }

        // Phase B: Resolve parent references
        resolveParentReferences(syncedTeamsByNativeId, parentNativeIdByChildNativeId, groupFullPath);

        // Phase C: Sync members per team
        int totalMembers = 0;
        for (Map.Entry<Long, Team> entry : syncedTeamsByNativeId.entrySet()) {
            String fullPath = teamFullPathsByNativeId.get(entry.getKey());
            if (fullPath != null) {
                try {
                    int members = syncTeamMembers(client, scopeId, entry.getValue().getId(), fullPath, providerId);
                    totalMembers += members;
                } catch (Exception e) {
                    log.warn(
                        "Failed to sync members for team: teamSlug={}, error={}",
                        entry.getValue().getSlug(),
                        e.getMessage()
                    );
                }
            }
        }
        log.info("Phase C complete: groupPath={}, totalMembers={}", groupFullPath, totalMembers);

        // Phase D: Sync team-repo permissions
        int totalPermissions = 0;
        for (Map.Entry<Long, Team> entry : syncedTeamsByNativeId.entrySet()) {
            String fullPath = teamFullPathsByNativeId.get(entry.getKey());
            if (fullPath != null) {
                try {
                    int perms = syncTeamRepoPermissions(entry.getValue().getId(), fullPath, providerId);
                    totalPermissions += perms;
                } catch (Exception e) {
                    log.warn(
                        "Failed to sync repo permissions for team: teamSlug={}, error={}",
                        entry.getValue().getSlug(),
                        e.getMessage()
                    );
                }
            }
        }
        log.info("Phase D complete: groupPath={}, totalPermissions={}", groupFullPath, totalPermissions);

        // Phase E: Cleanup stale teams (only if sync completed normally)
        if (syncCompletedNormally) {
            removeDeletedTeams(groupFullPath, syncedNativeIds, providerId);
        }

        log.info(
            "GitLab team sync complete: groupPath={}, teams={}, members={}, permissions={}",
            groupFullPath,
            totalSynced,
            totalMembers,
            totalPermissions
        );

        return totalSynced;
    }

    // ========================================================================
    // Phase A: Fetch Descendant Groups
    // ========================================================================

    private boolean fetchAndProcessDescendantGroups(
        HttpGraphQlClient client,
        Long scopeId,
        String groupFullPath,
        GitProvider provider,
        Map<Long, Team> syncedTeamsByNativeId,
        Map<Long, Long> parentNativeIdByChildNativeId,
        Map<Long, String> teamFullPathsByNativeId,
        Set<Long> syncedNativeIds
    ) {
        String cursor = null;
        int pageCount = 0;

        while (true) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn("Reached maximum pagination limit for descendant groups: groupPath={}", groupFullPath);
                return false;
            }

            try {
                graphQlClientProvider.acquirePermission();
                graphQlClientProvider.waitIfRateLimitLow(scopeId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted during rate limit wait: groupPath={}", groupFullPath);
                return false;
            }

            int pageSize = adaptPageSize(TEAM_PAGE_SIZE, graphQlClientProvider.getRateLimitRemaining(scopeId));

            ClientGraphQlResponse response = client
                .documentName(GET_GROUP_DESCENDANTS_DOCUMENT)
                .variable("fullPath", groupFullPath)
                .variable("first", pageSize)
                .variable("after", cursor)
                .execute()
                .block(gitLabProperties.graphqlTimeout());

            if (response == null || !response.isValid()) {
                log.warn(
                    "Failed to fetch descendant groups: groupPath={}, page={}, errors={}",
                    groupFullPath,
                    pageCount,
                    response != null ? response.getErrors() : "null"
                );
                graphQlClientProvider.recordFailure(
                    new GitLabSyncException("Invalid GraphQL response for descendant groups")
                );
                return false;
            }
            graphQlClientProvider.recordSuccess();

            // Parse nodes
            List<GitLabDescendantGroupResponse> groups = response
                .field("group.descendantGroups.nodes")
                .toEntityList(GitLabDescendantGroupResponse.class);

            if (groups != null) {
                for (GitLabDescendantGroupResponse group : groups) {
                    Team team = teamProcessor.process(group, groupFullPath, provider);
                    if (team != null) {
                        long nativeId = team.getNativeId();
                        syncedTeamsByNativeId.put(nativeId, team);
                        syncedNativeIds.add(nativeId);
                        teamFullPathsByNativeId.put(nativeId, group.fullPath());

                        // Track parent for resolution in Phase B
                        if (
                            group.parent() != null &&
                            group.parent().fullPath() != null &&
                            !group.parent().fullPath().equals(groupFullPath)
                        ) {
                            try {
                                long parentNativeId = extractNumericId(group.parent().id());
                                parentNativeIdByChildNativeId.put(nativeId, parentNativeId);
                            } catch (IllegalArgumentException e) {
                                log.warn(
                                    "Invalid parent GID: child={}, parentGid={}",
                                    group.fullPath(),
                                    group.parent().id()
                                );
                            }
                        }
                    }
                }
            }

            // Parse page info
            GitLabPageInfo pageInfo = response.field("group.descendantGroups.pageInfo").toEntity(GitLabPageInfo.class);

            if (pageInfo == null || !pageInfo.hasNextPage()) {
                break;
            }

            cursor = pageInfo.endCursor();
            if (cursor == null) {
                log.warn(
                    "Pagination cursor is null despite hasNextPage=true: groupPath={}, page={}",
                    groupFullPath,
                    pageCount
                );
                break;
            }

            throttle();
        }

        return true;
    }

    // ========================================================================
    // Phase B: Resolve Parent References
    // ========================================================================

    void resolveParentReferences(
        Map<Long, Team> syncedTeamsByNativeId,
        Map<Long, Long> parentNativeIdByChildNativeId,
        String groupFullPath
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            Map<Long, Team> managedTeams = new HashMap<>();
            for (Map.Entry<Long, Team> entry : syncedTeamsByNativeId.entrySet()) {
                teamRepository.findById(entry.getValue().getId()).ifPresent(t -> managedTeams.put(entry.getKey(), t));
            }

            Set<Team> changed = new HashSet<>();

            for (Map.Entry<Long, Team> entry : managedTeams.entrySet()) {
                Team child = entry.getValue();
                Long parentNativeId = parentNativeIdByChildNativeId.get(entry.getKey());

                Long correctParentId = null;
                if (parentNativeId != null) {
                    Team parent = managedTeams.get(parentNativeId);
                    if (parent != null) {
                        correctParentId = parent.getId();
                    } else {
                        log.warn(
                            "Parent team not found in sync: child={}, parentNativeId={}",
                            child.getSlug(),
                            parentNativeId
                        );
                    }
                }

                if (!Objects.equals(correctParentId, child.getParentId())) {
                    child.setParentId(correctParentId);
                    changed.add(child);
                }
            }

            if (!changed.isEmpty()) {
                teamRepository.saveAll(changed);
                log.info("Resolved parent references: groupPath={}, updated={}", groupFullPath, changed.size());
            }
        });
    }

    // ========================================================================
    // Phase C: Sync Members
    // ========================================================================

    int syncTeamMembers(HttpGraphQlClient client, Long scopeId, Long teamId, String groupFullPath, Long providerId) {
        // Phase C.1: Fetch all members via GraphQL OUTSIDE a transaction
        // to avoid holding a DB connection during network I/O and throttle delays.
        List<GitLabGroupMemberResponse> allMembers = new java.util.ArrayList<>();
        boolean memberSyncComplete = fetchAllGroupMembers(client, scopeId, groupFullPath, allMembers);

        // Phase C.2: Apply membership diff in a short transaction
        Integer result = transactionTemplate.execute(status -> {
            Team team = teamRepository
                .findById(teamId)
                .orElseThrow(() -> new IllegalStateException("Team not found: teamId=" + teamId));

            Map<Long, TeamMembership> existingMemberships = team
                .getMemberships()
                .stream()
                .collect(Collectors.toMap(tm -> tm.getUser().getId(), tm -> tm));

            Set<Long> syncedMemberIds = new HashSet<>();

            for (GitLabGroupMemberResponse member : allMembers) {
                processMember(member, team, providerId, existingMemberships, syncedMemberIds);
            }

            if (memberSyncComplete) {
                removeStaleTeamMemberships(team, syncedMemberIds);
            } else {
                log.warn("Skipped stale membership cleanup due to incomplete pagination: teamSlug={}", team.getSlug());
            }

            return syncedMemberIds.size();
        });

        return result != null ? result : 0;
    }

    /**
     * Fetches all group members via paginated GraphQL calls.
     * Runs OUTSIDE a transaction to avoid holding a DB connection during network I/O.
     *
     * @return true if pagination completed normally, false if interrupted or failed
     */
    private boolean fetchAllGroupMembers(
        HttpGraphQlClient client,
        Long scopeId,
        String groupFullPath,
        List<GitLabGroupMemberResponse> allMembers
    ) {
        String cursor = null;
        int pageCount = 0;

        while (true) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn("Reached max pagination for members: groupPath={}", groupFullPath);
                return false;
            }

            try {
                graphQlClientProvider.acquirePermission();
                graphQlClientProvider.waitIfRateLimitLow(scopeId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

            int pageSize = adaptPageSize(MEMBER_PAGE_SIZE, graphQlClientProvider.getRateLimitRemaining(scopeId));

            ClientGraphQlResponse response = client
                .documentName(GET_GROUP_MEMBERS_DOCUMENT)
                .variable("fullPath", groupFullPath)
                .variable("first", pageSize)
                .variable("after", cursor)
                .execute()
                .block(gitLabProperties.graphqlTimeout());

            if (response == null || !response.isValid()) {
                log.warn("Failed to fetch group members: groupPath={}, page={}", groupFullPath, pageCount);
                graphQlClientProvider.recordFailure(
                    new GitLabSyncException("Invalid GraphQL response for group members")
                );
                return false;
            }
            graphQlClientProvider.recordSuccess();

            List<GitLabGroupMemberResponse> members = response
                .field("group.groupMembers.nodes")
                .toEntityList(GitLabGroupMemberResponse.class);

            if (members != null) {
                allMembers.addAll(members);
            }

            GitLabPageInfo memberPageInfo = response
                .field("group.groupMembers.pageInfo")
                .toEntity(GitLabPageInfo.class);

            if (memberPageInfo == null || !memberPageInfo.hasNextPage()) {
                return true;
            }

            cursor = memberPageInfo.endCursor();
            if (cursor == null) {
                log.warn(
                    "Member pagination cursor is null despite hasNextPage=true: groupPath={}, page={}",
                    groupFullPath,
                    pageCount
                );
                return false;
            }

            throttle();
        }
    }

    private void processMember(
        GitLabGroupMemberResponse member,
        Team team,
        Long providerId,
        Map<Long, TeamMembership> existingMemberships,
        Set<Long> syncedMemberIds
    ) {
        if (member == null || member.user() == null || member.user().id() == null) {
            return;
        }

        // Map access level
        TeamMembership.Role role = mapAccessLevel(
            member.accessLevel() != null ? member.accessLevel().stringValue() : null
        );
        if (role == null) {
            // NO_ACCESS or MINIMAL_ACCESS → skip
            return;
        }

        var userRef = member.user();
        User user = gitLabUserService.findOrCreateUser(
            userRef.id(),
            userRef.username(),
            userRef.name(),
            userRef.avatarUrl(),
            userRef.webUrl(),
            providerId
        );

        if (user == null) {
            return;
        }

        // Deduplicate: same user could appear on multiple pages in edge cases.
        if (!syncedMemberIds.add(user.getId())) {
            return;
        }

        TeamMembership existing = existingMemberships.get(user.getId());
        if (existing != null) {
            if (existing.getRole() != role) {
                existing.setRole(role);
                teamMembershipRepository.save(existing);
            }
        } else {
            TeamMembership membership = new TeamMembership(team, user, role);
            teamMembershipRepository.save(membership);
        }
    }

    /**
     * Maps GitLab access level string to TeamMembership.Role.
     * <p>
     * Returns null for access levels that should be skipped.
     */
    static TeamMembership.Role mapAccessLevel(String accessLevel) {
        if (accessLevel == null) {
            return TeamMembership.Role.MEMBER;
        }
        return switch (accessLevel.toUpperCase()) {
            case "NO_ACCESS", "MINIMAL_ACCESS" -> null;
            case "GUEST", "PLANNER", "REPORTER", "DEVELOPER" -> TeamMembership.Role.MEMBER;
            case "MAINTAINER", "OWNER", "ADMIN" -> TeamMembership.Role.MAINTAINER;
            default -> {
                log.warn("Unknown GitLab access level '{}', using MEMBER as default", accessLevel);
                yield TeamMembership.Role.MEMBER;
            }
        };
    }

    private void removeStaleTeamMemberships(Team team, Set<Long> syncedMemberIds) {
        int removed = 0;
        for (TeamMembership membership : new HashSet<>(team.getMemberships())) {
            if (!syncedMemberIds.contains(membership.getUser().getId())) {
                team.removeMembership(membership);
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Removed stale memberships: teamSlug={}, count={}", team.getSlug(), removed);
        }
    }

    // ========================================================================
    // Phase D: Sync Team-Repo Permissions
    // ========================================================================

    int syncTeamRepoPermissions(Long teamId, String groupFullPath, Long providerId) {
        Integer result = transactionTemplate.execute(status -> {
            Team team = teamRepository
                .findById(teamId)
                .orElseThrow(() -> new IllegalStateException("Team not found: teamId=" + teamId));

            List<Repository> repos = repositoryRepository.findAllByOrganization_LoginIgnoreCaseAndProviderId(
                groupFullPath,
                providerId
            );

            if (repos.isEmpty()) {
                return 0;
            }

            Set<Long> freshRepoIds = repos.stream().map(Repository::getId).collect(Collectors.toSet());

            // Remove stale permissions (repos no longer in this group)
            team.getRepoPermissions().removeIf(p -> !freshRepoIds.contains(p.getRepository().getId()));

            // Add or update permissions
            Set<Long> existingRepoIds = team
                .getRepoPermissions()
                .stream()
                .map(p -> p.getRepository().getId())
                .collect(Collectors.toSet());

            for (Repository repo : repos) {
                if (!existingRepoIds.contains(repo.getId())) {
                    team.addRepoPermission(
                        new TeamRepositoryPermission(team, repo, TeamRepositoryPermission.PermissionLevel.WRITE)
                    );
                }
            }

            teamRepository.save(team);

            log.debug(
                "Synced repo permissions: teamSlug={}, repos={}",
                team.getSlug(),
                team.getRepoPermissions().size()
            );
            return team.getRepoPermissions().size();
        });

        return result != null ? result : 0;
    }

    // ========================================================================
    // Phase E: Cleanup
    // ========================================================================

    private void removeDeletedTeams(String groupFullPath, Set<Long> syncedNativeIds, Long providerId) {
        transactionTemplate.executeWithoutResult(status -> {
            List<Team> existingTeams = teamRepository.findAllByOrganizationIgnoreCase(groupFullPath);
            int removed = 0;

            for (Team team : existingTeams) {
                // Only delete teams from the same provider
                if (
                    team.getProvider() != null &&
                    team.getProvider().getId().equals(providerId) &&
                    !syncedNativeIds.contains(team.getNativeId())
                ) {
                    teamProcessor.delete(team.getNativeId(), providerId);
                    removed++;
                }
            }

            if (removed > 0) {
                log.info("Removed stale teams: groupPath={}, count={}", groupFullPath, removed);
            }
        });
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private GitProvider resolveProvider() {
        return gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, gitLabProperties.defaultServerUrl())
            .orElse(null);
    }

    private void throttle() {
        try {
            Thread.sleep(gitLabProperties.paginationThrottle().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
