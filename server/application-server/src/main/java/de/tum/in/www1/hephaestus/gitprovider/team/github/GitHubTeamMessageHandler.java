package de.tum.in.www1.hephaestus.gitprovider.team.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.github.dto.GitHubTeamEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub team webhook events.
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
 */
@Component
public class GitHubTeamMessageHandler extends GitHubMessageHandler<GitHubTeamEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubTeamMessageHandler.class);

    private final TeamRepository teamRepository;

    GitHubTeamMessageHandler(TeamRepository teamRepository) {
        super(GitHubTeamEventDTO.class);
        this.teamRepository = teamRepository;
    }

    @Override
    protected String getEventKey() {
        return "team";
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.ORGANIZATION;
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubTeamEventDTO event) {
        var teamDto = event.team();

        if (teamDto == null) {
            logger.warn("Received team event with missing data");
            return;
        }

        logger.info(
            "Received team event: action={}, team={}, org={}",
            event.action(),
            teamDto.name(),
            event.organization() != null ? event.organization().login() : "unknown"
        );

        switch (event.action()) {
            case "deleted" -> teamRepository.deleteById(teamDto.id());
            case "created", "edited" -> processTeam(teamDto, event.organization());
            default -> logger.debug("Unhandled team action: {}", event.action());
        }
    }

    private void processTeam(GitHubTeamEventDTO.GitHubTeamDTO dto, GitHubTeamEventDTO.GitHubOrgRefDTO org) {
        teamRepository
            .findById(dto.id())
            .ifPresentOrElse(
                team -> {
                    if (dto.name() != null) team.setName(dto.name());
                    if (dto.description() != null) team.setDescription(dto.description());
                    if (dto.htmlUrl() != null) team.setHtmlUrl(dto.htmlUrl());
                    if (dto.privacy() != null) team.setPrivacy(mapPrivacy(dto.privacy()));
                    teamRepository.save(team);
                },
                () -> {
                    Team team = new Team();
                    team.setId(dto.id());
                    team.setName(dto.name());
                    team.setDescription(dto.description());
                    team.setHtmlUrl(dto.htmlUrl());
                    team.setPrivacy(mapPrivacy(dto.privacy()));
                    if (org != null) {
                        team.setOrganization(org.login());
                    }
                    teamRepository.save(team);
                }
            );
    }

    private Team.Privacy mapPrivacy(String privacy) {
        if (privacy == null) return Team.Privacy.CLOSED;
        return switch (privacy.toLowerCase()) {
            case "secret" -> Team.Privacy.SECRET;
            default -> Team.Privacy.CLOSED;
        };
    }
}
