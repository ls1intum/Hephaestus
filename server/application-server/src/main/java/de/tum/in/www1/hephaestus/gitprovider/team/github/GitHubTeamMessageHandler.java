package de.tum.in.www1.hephaestus.gitprovider.team.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.team.github.dto.GitHubTeamEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub team webhook events.
 */
@Component
public class GitHubTeamMessageHandler extends GitHubMessageHandler<GitHubTeamEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubTeamMessageHandler.class);

    private final GitHubTeamProcessor teamProcessor;

    GitHubTeamMessageHandler(GitHubTeamProcessor teamProcessor) {
        super(GitHubTeamEventDTO.class);
        this.teamProcessor = teamProcessor;
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

        switch (event.actionType()) {
            case GitHubEventAction.Team.DELETED -> teamProcessor.delete(teamDto.id());
            case GitHubEventAction.Team.CREATED, GitHubEventAction.Team.EDITED -> teamProcessor.process(
                teamDto,
                event.organization() != null ? event.organization().login() : null
            );
            default -> logger.debug("Unhandled team action: {}", event.action());
        }
    }
}
