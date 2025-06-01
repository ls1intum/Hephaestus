package de.tum.in.www1.hephaestus.gitprovider.teamV2.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.TeamV2Repository;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GitHubTeamMessageHandler extends GitHubMessageHandler<GHEventPayload.Team> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubTeamMessageHandler.class);

    private final TeamV2Repository teamRepository;
    private final GitHubTeamSyncService teamSyncService;
    private final GitHub gitHub;

    public GitHubTeamMessageHandler(TeamV2Repository teamRepository, GitHubTeamSyncService teamSyncService, GitHub gitHub) {
        super(GHEventPayload.Team.class);
        this.teamRepository = teamRepository;
        this.teamSyncService = teamSyncService;
        this.gitHub = gitHub;
    }

    @Override
    protected void handleEvent(GHEventPayload.Team eventPayload) {
        String action = eventPayload.getAction();
        long teamId = eventPayload.getTeam().getId();
        String orgLogin = eventPayload.getOrganization().getLogin();

        logger.info("Received team event, action: {}, teamId: {}", action, teamId);

        if (action.equals("deleted")) {
            teamRepository.deleteById(teamId);
        }
        else {
            try {
                teamSyncService.processTeam(gitHub.getOrganization(orgLogin).getTeam(teamId));
            }
            catch (IOException e) {
                logger.error("Could not fetch GHTeam id={} (org={}): {}", teamId, orgLogin, e.getMessage());
            }
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.TEAM;
    }
}
