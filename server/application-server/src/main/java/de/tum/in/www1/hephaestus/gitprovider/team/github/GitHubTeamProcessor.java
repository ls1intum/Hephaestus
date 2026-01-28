package de.tum.in.www1.hephaestus.gitprovider.team.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.github.dto.GitHubTeamEventDTO;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub teams.
 */
@Service
public class GitHubTeamProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubTeamProcessor.class);

    private final TeamRepository teamRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubTeamProcessor(TeamRepository teamRepository, ApplicationEventPublisher eventPublisher) {
        this.teamRepository = teamRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Team process(GitHubTeamEventDTO.GitHubTeamDTO dto, String orgLogin, @NonNull ProcessingContext context) {
        if (dto == null || dto.id() == null) {
            log.warn("Skipped team processing: reason=nullOrMissingId");
            return null;
        }

        // First try natural key lookup (organization + name)
        Optional<Team> existingTeam = Optional.empty();
        if (orgLogin != null && dto.name() != null) {
            existingTeam = teamRepository.findByOrganizationIgnoreCaseAndName(orgLogin, dto.name());
        }

        // Fall back to ID lookup if natural key not found (handles renames)
        if (existingTeam.isEmpty()) {
            existingTeam = teamRepository.findById(dto.id());
        }

        Team team = existingTeam.orElseGet(() -> {
            Team t = new Team();
            t.setId(dto.id());
            return t;
        });

        boolean isNew = team.getName() == null;

        // Update fields
        if (dto.name() != null) {
            team.setName(dto.name());
        }
        if (dto.description() != null) {
            team.setDescription(dto.description());
        }
        // htmlUrl is required - use DTO value or construct from org and team slug/name
        if (dto.htmlUrl() != null) {
            team.setHtmlUrl(dto.htmlUrl());
        } else if (team.getHtmlUrl() == null && orgLogin != null && dto.slug() != null) {
            // Construct htmlUrl from org and slug as fallback
            team.setHtmlUrl("https://github.com/orgs/" + orgLogin + "/teams/" + dto.slug());
            log.debug("Constructed htmlUrl from org and slug for team: teamSlug={}", dto.slug());
        } else if (team.getHtmlUrl() == null && orgLogin != null && dto.name() != null) {
            // Fallback: use name if slug is not available
            team.setHtmlUrl("https://github.com/orgs/" + orgLogin + "/teams/" + dto.name());
            log.debug("Constructed htmlUrl from org and name for team: teamName={}", dto.name());
        }
        if (dto.privacy() != null) {
            team.setPrivacy(mapPrivacy(dto.privacy()));
        }
        if (dto.createdAt() != null) {
            team.setCreatedAt(dto.createdAt());
        }
        if (dto.updatedAt() != null) {
            team.setUpdatedAt(dto.updatedAt());
        }
        if (isNew && orgLogin != null) {
            team.setOrganization(orgLogin);
        }

        Team saved;
        try {
            saved = teamRepository.save(team);
        } catch (DataIntegrityViolationException e) {
            // Unique constraint violation on (organization, name)
            // This can happen when:
            // 1. Team A had name "core" in org "myorg", renamed to "platform"
            // 2. Team B (new) was created with name "core" in org "myorg"
            // 3. We're processing Team B but Team A still has "core" in our DB
            //
            // Solution: Find and update the conflicting team's name first
            if (isNameConflict(e)) {
                saved = handleNameConflict(team, orgLogin, dto, context);
                if (saved == null) {
                    return null;
                }
            } else {
                throw e;
            }
        }

        if (isNew) {
            eventPublisher.publishEvent(
                new DomainEvent.TeamCreated(EventPayload.TeamData.from(saved), EventContext.from(context))
            );
            log.debug("Created team: teamId={}, teamSlug={}", saved.getId(), saved.getName());
        } else {
            eventPublisher.publishEvent(
                new DomainEvent.TeamUpdated(
                    EventPayload.TeamData.from(saved),
                    Set.of("name", "description"),
                    EventContext.from(context)
                )
            );
            log.debug("Updated team: teamId={}, teamSlug={}", saved.getId(), saved.getName());
        }

        return saved;
    }

    /**
     * Delete a team by its ID.
     * <p>
     * IMPORTANT: Clears all associated memberships and repository permissions
     * before deletion. While cascade REMOVE handles the deletion automatically,
     * clearing the collections ensures no stale references remain in the
     * persistence context.
     *
     * @param teamId the team database ID
     * @param context processing context with scope information
     */
    @Transactional
    public void delete(Long teamId, @NonNull ProcessingContext context) {
        if (teamId == null) {
            return;
        }

        teamRepository
            .findById(teamId)
            .ifPresent(team -> {
                String teamName = team.getName();

                // Clear collections to avoid stale references in persistence context
                team.getMemberships().clear();
                team.getRepoPermissions().clear();

                teamRepository.delete(team);
                eventPublisher.publishEvent(new DomainEvent.TeamDeleted(teamId, teamName, EventContext.from(context)));
                log.info("Deleted team: teamId={}, teamSlug={}", teamId, teamName);
            });
    }

    /**
     * Check if the exception is a team name unique constraint violation.
     */
    private boolean isNameConflict(DataIntegrityViolationException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("uk_team_organization_name") || (message.contains("team") && message.contains("name"));
    }

    /**
     * Handle a name conflict by finding the conflicting team and resolving it.
     * <p>
     * When a team is renamed, another team can take the old name. This method:
     * 1. Finds the team with the conflicting name
     * 2. Updates that team's name to a placeholder
     * 3. Retries the save for the new team
     *
     * @return the saved team, or null if unable to resolve
     */
    private Team handleNameConflict(
        Team team,
        String orgLogin,
        GitHubTeamEventDTO.GitHubTeamDTO dto,
        ProcessingContext context
    ) {
        String teamName = dto.name();

        // Find the team that has the conflicting name
        Optional<Team> conflicting = teamRepository.findByOrganizationIgnoreCaseAndName(orgLogin, teamName);
        if (conflicting.isEmpty()) {
            // Conflict resolved concurrently
            log.debug("Team name conflict resolved concurrently: org={}, name={}", orgLogin, teamName);
            return teamRepository.save(team);
        }

        Team oldTeam = conflicting.get();
        if (oldTeam.getId().equals(team.getId())) {
            // Same team - shouldn't happen but handle gracefully
            log.debug("Team name conflict was for same team: teamId={}", team.getId());
            return teamRepository.save(team);
        }

        // Rename the old team to free up the name
        String renamedName = "RENAMED_" + oldTeam.getId();
        log.info(
            "Freeing up team name for renamed team: oldTeamId={}, oldName={}, newName={}, newTeamId={}",
            oldTeam.getId(),
            teamName,
            renamedName,
            team.getId()
        );
        oldTeam.setName(renamedName);
        teamRepository.save(oldTeam);

        // Retry the save
        try {
            return teamRepository.save(team);
        } catch (DataIntegrityViolationException e) {
            log.warn("Team save still failed after freeing name: teamId={}, name={}", team.getId(), teamName, e);
            return null;
        }
    }

    private Team.Privacy mapPrivacy(String privacy) {
        if (privacy == null) {
            log.debug("Team privacy is null, using VISIBLE as default");
            return Team.Privacy.VISIBLE;
        }
        return switch (privacy.toLowerCase()) {
            case "secret" -> Team.Privacy.SECRET;
            // "visible" from GraphQL, "closed" from REST API - both map to VISIBLE
            case "visible", "closed" -> Team.Privacy.VISIBLE;
            default -> {
                log.warn("Unknown team privacy '{}', using VISIBLE as default", privacy);
                yield Team.Privacy.VISIBLE;
            }
        };
    }
}
