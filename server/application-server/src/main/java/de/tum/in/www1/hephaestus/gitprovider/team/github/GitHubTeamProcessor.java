package de.tum.in.www1.hephaestus.gitprovider.team.github;

import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.github.dto.GitHubTeamEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub teams.
 * <p>
 * This service handles the conversion of GitHubTeamDTO to Team entities,
 * persists them, and manages team lifecycle events.
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations via upsert pattern</li>
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * </ul>
 */
@Service
public class GitHubTeamProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubTeamProcessor.class);

    private final TeamRepository teamRepository;

    public GitHubTeamProcessor(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    /**
     * Process a GitHub team DTO and persist it as a Team entity.
     * Uses upsert pattern to handle both create and update scenarios.
     *
     * @param dto the GitHub team DTO
     * @param orgLogin the organization login (for new teams)
     * @return the persisted Team entity, or null if dto is invalid
     */
    @Transactional
    public Team process(GitHubTeamEventDTO.GitHubTeamDTO dto, String orgLogin) {
        if (dto == null || dto.id() == null) {
            logger.warn("Team DTO is null or missing ID, skipping");
            return null;
        }

        Team team = teamRepository
            .findById(dto.id())
            .orElseGet(() -> {
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
        if (dto.htmlUrl() != null) {
            team.setHtmlUrl(dto.htmlUrl());
        }
        if (dto.privacy() != null) {
            team.setPrivacy(mapPrivacy(dto.privacy()));
        }
        if (isNew && orgLogin != null) {
            team.setOrganization(orgLogin);
        }

        Team saved = teamRepository.save(team);
        logger.debug("Processed team {} ({}): {}", saved.getName(), saved.getId(), isNew ? "created" : "updated");
        return saved;
    }

    /**
     * Delete a team by its ID.
     *
     * @param teamId the team ID
     */
    @Transactional
    public void delete(Long teamId) {
        if (teamId == null) {
            return;
        }

        teamRepository
            .findById(teamId)
            .ifPresent(team -> {
                teamRepository.delete(team);
                logger.info("Deleted team {} ({})", team.getName(), teamId);
            });
    }

    /**
     * Check if a team exists by ID.
     *
     * @param teamId the team ID
     * @return true if the team exists
     */
    public boolean exists(Long teamId) {
        return teamId != null && teamRepository.existsById(teamId);
    }

    /**
     * Map GitHub privacy string to Team.Privacy enum.
     */
    private Team.Privacy mapPrivacy(String privacy) {
        if (privacy == null) {
            return Team.Privacy.CLOSED;
        }
        return switch (privacy.toLowerCase()) {
            case "secret" -> Team.Privacy.SECRET;
            default -> Team.Privacy.CLOSED;
        };
    }
}
