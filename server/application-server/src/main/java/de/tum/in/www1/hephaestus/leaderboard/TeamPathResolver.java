package de.tum.in.www1.hephaestus.leaderboard;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.settings.WorkspaceTeamSettingsService;
import jakarta.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves team paths and manages team hierarchy traversal.
 *
 * <p>Team paths are human-readable breadcrumb-style paths like "Engineering / Backend / Core"
 * that represent the visible (non-hidden) ancestor chain of a team. This component handles
 * path resolution with proper caching to minimize database queries.
 *
 * <p>Hidden status is determined by workspace-scoped settings, enabling different
 * visibility configurations for the same team across multiple workspaces.
 *
 * @see WorkspaceTeamSettingsService
 */
@Component
public class TeamPathResolver {

    private static final Logger log = LoggerFactory.getLogger(TeamPathResolver.class);

    private final TeamRepository teamRepository;
    private final WorkspaceTeamSettingsService workspaceTeamSettingsService;

    public TeamPathResolver(TeamRepository teamRepository, WorkspaceTeamSettingsService workspaceTeamSettingsService) {
        this.teamRepository = teamRepository;
        this.workspaceTeamSettingsService = workspaceTeamSettingsService;
    }

    /**
     * Resolves a team by its visible path within a workspace.
     *
     * @param workspace the workspace context
     * @param path the team path (e.g., "Engineering / Backend / Core")
     * @return the matching team, or empty if not found
     */
    public Optional<Team> resolveByPath(Workspace workspace, @Nonnull String path) {
        if (workspace == null || workspace.getAccountLogin() == null || path.isBlank()) {
            return Optional.empty();
        }

        // Get hidden team IDs for this workspace
        Set<Long> hiddenTeamIds = workspaceTeamSettingsService.getHiddenTeamIds(workspace.getId());

        String[] parts = path.split(" / ");
        String leaf = parts[parts.length - 1];
        List<Team> candidates = teamRepository
            .findAllByName(leaf)
            .stream()
            .filter(team -> belongsToWorkspace(team, workspace))
            .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Map<Long, Team> cache = new HashMap<>();
        Map<Long, Team> currentByCandidate = new HashMap<>();

        for (Team candidate : candidates) {
            Long candidateId = candidate.getId();
            if (candidateId == null) {
                continue;
            }
            cache.put(candidateId, candidate);
            currentByCandidate.put(candidateId, candidate);
        }

        if (currentByCandidate.isEmpty()) {
            return Optional.empty();
        }

        if (parts.length == 1) {
            Long sole = currentByCandidate.keySet().stream().findFirst().get();
            return Optional.ofNullable(cache.get(sole));
        }

        for (int index = parts.length - 2; index >= 0; index--) {
            if (currentByCandidate.size() == 1) {
                break;
            }

            String expected = parts[index];
            Map<Long, Team> nextVisibleByCandidate = new HashMap<>();
            boolean pendingResolution = true;

            while (pendingResolution) {
                pendingResolution = false;
                Set<Long> missingIds = new HashSet<>();

                for (Map.Entry<Long, Team> entry : currentByCandidate.entrySet()) {
                    Long candidateId = entry.getKey();
                    Team cursor = entry.getValue();
                    if (nextVisibleByCandidate.containsKey(candidateId)) {
                        continue;
                    }

                    Long parentId = cursor.getParentId();
                    while (parentId != null) {
                        Team parent = cache.get(parentId);
                        if (parent == null) {
                            missingIds.add(parentId);
                            break;
                        }
                        // Use workspace-scoped hidden status
                        if (!hiddenTeamIds.contains(parent.getId())) {
                            nextVisibleByCandidate.put(candidateId, parent);
                            break;
                        }
                        parentId = parent.getParentId();
                    }

                    if (parentId == null && !nextVisibleByCandidate.containsKey(candidateId)) {
                        nextVisibleByCandidate.put(candidateId, null);
                    }
                }

                if (!missingIds.isEmpty()) {
                    teamRepository
                        .findAllById(missingIds)
                        .forEach(parent -> {
                            Long parentId = parent.getId();
                            if (parentId != null && belongsToWorkspace(parent, workspace)) {
                                cache.putIfAbsent(parentId, parent);
                            }
                        });
                    pendingResolution = true;
                }
            }

            Map<Long, Team> filtered = new HashMap<>();
            for (Map.Entry<Long, Team> e : currentByCandidate.entrySet()) {
                Long candidateId = e.getKey();
                Team nextVisible = nextVisibleByCandidate.get(candidateId);
                if (
                    nextVisible != null &&
                    expected.equals(nextVisible.getName()) &&
                    belongsToWorkspace(nextVisible, workspace)
                ) {
                    filtered.put(candidateId, nextVisible);
                    if (nextVisible.getId() != null) {
                        cache.putIfAbsent(nextVisible.getId(), nextVisible);
                    }
                }
            }

            currentByCandidate = filtered;

            if (currentByCandidate.isEmpty()) {
                return Optional.empty();
            }

            if (currentByCandidate.size() == 1) {
                Long onlyId = currentByCandidate.keySet().iterator().next();
                return Optional.ofNullable(cache.get(onlyId));
            }
        }

        if (currentByCandidate.size() > 1) {
            preloadAncestors(currentByCandidate.values(), cache);
            for (Long candidateId : currentByCandidate.keySet()) {
                Team candidate = cache.get(candidateId);
                if (
                    candidate != null &&
                    belongsToWorkspace(candidate, workspace) &&
                    equalsVisiblePath(candidate, parts, cache, hiddenTeamIds)
                ) {
                    return Optional.of(candidate);
                }
            }
            log.warn(
                "Ambiguous team path '{}' resolved to multiple workspace candidates; picking first.",
                sanitizeForLog(path)
            );
        }

        Long anyId = currentByCandidate.keySet().stream().findFirst().orElse(null);
        return Optional.ofNullable(cache.get(anyId));
    }

    /**
     * Builds a map of parent ID to child teams for efficient hierarchy traversal.
     *
     * @param workspace the workspace context
     * @return map from parent ID (0 for root) to list of child teams
     */
    public Map<Long, List<Team>> buildHierarchy(Workspace workspace) {
        if (workspace == null || workspace.getAccountLogin() == null) {
            return Collections.emptyMap();
        }
        List<Team> all = teamRepository.findAllByOrganizationIgnoreCase(workspace.getAccountLogin());
        return all
            .stream()
            .collect(
                Collectors.groupingBy(
                    t -> Optional.ofNullable(t.getParentId()).orElse(0L),
                    HashMap::new,
                    Collectors.toList()
                )
            );
    }

    /**
     * Collects a team and all its descendants (transitive children).
     *
     * @param team the root team
     * @param hierarchy the pre-built hierarchy map
     * @return set of team IDs including the root and all descendants
     */
    public Set<Long> collectDescendantIds(Team team, Map<Long, List<Team>> hierarchy) {
        Set<Long> result = new HashSet<>();
        ArrayDeque<Team> queue = new ArrayDeque<>();
        queue.add(team);

        while (!queue.isEmpty()) {
            Team current = queue.removeFirst();
            Long currentId = current.getId();
            if (currentId == null) {
                continue;
            }
            if (result.add(currentId)) {
                List<Team> children = hierarchy.getOrDefault(currentId, Collections.emptyList());
                queue.addAll(children);
            }
        }
        return result;
    }

    /**
     * Checks if a team's visible path matches the given parts.
     *
     * @param team the team to check
     * @param parts the path parts to match
     * @param cache the team cache
     * @param hiddenTeamIds set of team IDs hidden in the workspace
     * @return true if the visible path matches
     */
    private boolean equalsVisiblePath(Team team, String[] parts, Map<Long, Team> cache, Set<Long> hiddenTeamIds) {
        int index = parts.length - 1;
        Team current = team;
        while (current != null) {
            // Use workspace-scoped hidden status
            if (!hiddenTeamIds.contains(current.getId())) {
                if (index < 0 || !parts[index].equals(current.getName())) {
                    return false;
                }
                index--;
            }
            Long parentId = current.getParentId();
            current = parentId == null ? null : cache.get(parentId);
        }
        return index < 0;
    }

    private void preloadAncestors(Collection<Team> teams, Map<Long, Team> cache) {
        Set<Long> pending = teams
            .stream()
            .map(Team::getParentId)
            .filter(Objects::nonNull)
            .filter(id -> !cache.containsKey(id))
            .collect(Collectors.toSet());

        while (!pending.isEmpty()) {
            Set<Long> nextRound = new HashSet<>();
            teamRepository
                .findAllById(pending)
                .forEach(parent -> {
                    Long parentId = parent.getId();
                    if (parentId == null) {
                        return;
                    }
                    if (!cache.containsKey(parentId)) {
                        cache.put(parentId, parent);
                    }
                    Long ancestorId = parent.getParentId();
                    if (ancestorId != null && !cache.containsKey(ancestorId)) {
                        nextRound.add(ancestorId);
                    }
                });
            pending = nextRound;
        }
    }

    private boolean belongsToWorkspace(Team team, Workspace workspace) {
        if (team == null || workspace == null) {
            return false;
        }
        String org = team.getOrganization();
        String workspaceLogin = workspace.getAccountLogin();
        return org != null && workspaceLogin != null && org.equalsIgnoreCase(workspaceLogin);
    }
}
