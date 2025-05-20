package de.tum.in.www1.hephaestus.gitprovider.teamV2.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import org.springframework.stereotype.Component;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class GitHubTeamMessageHandler extends GitHubMessageHandler<GHEventPayload.Team> {
    private static final Logger logger = LoggerFactory.getLogger(GitHubTeamMessageHandler.class);

    private final TeamRepository teamRepository;
    private final GitHubTeamSyncService teamSyncService;

    public GitHubTeamMessageHandler(TeamRepository teamRepository, GitHubTeamSyncService teamSyncService) {
        super(GHEventPayload.Team.class);
        this.teamRepository = teamRepository;
        this.teamSyncService = teamSyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.Team eventPayload) {
        var action = eventPayload.getAction();
        var team = eventPayload.getTeam();
        logger.info("Received team event, action: {}, teamId: {}", action, team.getId());

        if (action.equals("deleted")) {
            teamRepository.deleteById(team.getId());
        } else {
            teamSyncService.processTeam(team);
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.TEAM;
    }
}
