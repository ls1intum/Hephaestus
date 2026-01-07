package de.tum.in.www1.hephaestus.gitprovider.team.github;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
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

    private static final Logger log = LoggerFactory.getLogger(GitHubTeamMessageHandler.class);

    private final GitHubTeamProcessor teamProcessor;

    GitHubTeamMessageHandler(GitHubTeamProcessor teamProcessor, NatsMessageDeserializer deserializer) {
        super(GitHubTeamEventDTO.class, deserializer);
        this.teamProcessor = teamProcessor;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.TEAM;
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
            log.warn("Received team event with missing data");
            return;
        }

        log.info(
            "Received team event: action={}, team={}, org={}",
            event.action(),
            teamDto.name(),
            event.organization() != null ? event.organization().login() : "unknown"
        );

        String orgLogin = event.organization() != null ? event.organization().login() : null;
        // Create a minimal context for team events (no repository context available)
        ProcessingContext context = ProcessingContext.forWebhook(null, null, event.action());

        switch (event.actionType()) {
            case GitHubEventAction.Team.DELETED -> teamProcessor.delete(teamDto.id(), context);
            case GitHubEventAction.Team.CREATED, GitHubEventAction.Team.EDITED -> teamProcessor.process(
                teamDto,
                orgLogin,
                context
            );
            default -> log.debug("Unhandled team action: {}", event.action());
        }
    }
}
