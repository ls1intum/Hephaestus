package de.tum.in.www1.hephaestus.gitprovider.team.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabDescendantGroupResponse;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitLab teams (subgroups).
 * <p>
 * Maps GitLab descendant group data to {@link Team} entities. Each subgroup
 * becomes a team with:
 * <ul>
 *   <li>{@code name} = display name (e.g., "alpha")</li>
 *   <li>{@code slug} = relative path from root group (e.g., "group1/alpha")</li>
 *   <li>{@code organization} = root group full path (e.g., "ase/introcourse")</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabTeamProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitLabTeamProcessor.class);

    private final TeamRepository teamRepository;

    public GitLabTeamProcessor(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    /**
     * Processes a GitLab descendant group into a Team entity (upsert).
     *
     * @param group         the GraphQL descendant group response
     * @param rootFullPath  the root group full path (workspace account login)
     * @param provider      the git provider entity
     * @return the persisted Team, or null if input is invalid
     */
    @Transactional
    @Nullable
    public Team process(GitLabDescendantGroupResponse group, String rootFullPath, GitProvider provider) {
        if (group == null || group.id() == null || group.fullPath() == null) {
            log.warn("Skipped team processing: reason=nullOrMissingId");
            return null;
        }

        long nativeId;
        try {
            nativeId = GitLabSyncConstants.extractNumericId(group.id());
        } catch (IllegalArgumentException e) {
            log.warn("Skipped team processing: reason=invalidGlobalId, gid={}", group.id());
            return null;
        }

        Long providerId = provider.getId();
        String slug = computeRelativePath(group.fullPath(), rootFullPath);

        // Upsert: try nativeId first, then natural key (organization + slug)
        Team team = teamRepository
            .findByNativeIdAndProviderId(nativeId, providerId)
            .orElseGet(() ->
                teamRepository
                    .findByOrganizationIgnoreCaseAndSlug(rootFullPath, slug)
                    .orElseGet(() -> {
                        Team t = new Team();
                        t.setNativeId(nativeId);
                        t.setProvider(provider);
                        t.setOrganization(rootFullPath);
                        return t;
                    })
            );

        boolean isNew = team.getId() == null;

        // Update fields
        team.setName(group.name());
        team.setSlug(slug);
        team.setHtmlUrl(group.webUrl());
        team.setPrivacy(Team.Privacy.VISIBLE);

        if (group.description() != null) {
            team.setDescription(group.description());
        }

        Team saved = teamRepository.save(team);

        if (isNew) {
            log.debug("Created GitLab team: teamId={}, slug={}, name={}", saved.getId(), slug, group.name());
        } else {
            log.debug("Updated GitLab team: teamId={}, slug={}, name={}", saved.getId(), slug, group.name());
        }

        return saved;
    }

    /**
     * Deletes a team by native ID and provider ID.
     */
    @Transactional
    public void delete(Long nativeId, Long providerId) {
        if (nativeId == null) {
            return;
        }

        teamRepository
            .findByNativeIdAndProviderId(nativeId, providerId)
            .ifPresent(team -> {
                Long teamId = team.getId();
                String teamName = team.getName();

                team.clearMemberships();
                team.clearRepoPermissions();

                teamRepository.delete(team);
                log.info("Deleted GitLab team: teamId={}, name={}", teamId, teamName);
            });
    }

    /**
     * Computes the relative path from the root group.
     * <p>
     * Example: fullPath = "ase/introcourse/group1/alpha", rootFullPath = "ase/introcourse"
     * → returns "group1/alpha"
     */
    static String computeRelativePath(String fullPath, String rootFullPath) {
        if (fullPath.startsWith(rootFullPath + "/")) {
            return fullPath.substring(rootFullPath.length() + 1);
        }
        // Fallback: return the full path if it doesn't start with root
        return fullPath;
    }
}
