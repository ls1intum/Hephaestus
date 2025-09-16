package de.tum.in.www1.hephaestus.gitprovider.organization.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayloadOrganization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles GitHub Organization events.
 */
@Component
public class GitHubOrganizationMessageHandler extends GitHubMessageHandler<GHEventPayloadOrganization> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubOrganizationMessageHandler.class);

    public GitHubOrganizationMessageHandler() {
        super(GHEventPayloadOrganization.class);
    }

    @Override
    protected void handleEvent(GHEventPayloadOrganization payload) {
        final String action = payload.getAction() == null ? "" : payload.getAction();
        final String orgLogin = payload.getOrganization() != null ? payload.getOrganization().getLogin() : "-";

        switch (action) {
            case "member_added":
            case "member_removed": {
                var membership = payload.getMembership();
                var user = (membership != null && membership.getUser() != null) ? membership.getUser().getLogin() : "-";
                var role = membership != null ? membership.getRole() : "-";
                var state = membership != null ? membership.getState() : "-";
                logger.info("org={} action={} user={} role={} state={}", orgLogin, action, user, role, state);
                break;
            }
            case "member_invited": {
                var invitation = payload.getInvitation();
                var login = invitation != null ? invitation.getLogin() : "-";
                var inviter = (invitation != null && invitation.getInviter() != null)
                    ? invitation.getInviter().getLogin()
                    : "-";
                var source = invitation != null ? invitation.getInvitationSource() : "-";
                var teams = invitation != null ? invitation.getTeamCount() : 0;
                logger.info(
                    "org={} action={} login={} inviter={} source={} teams={}",
                    orgLogin,
                    action,
                    login,
                    inviter,
                    source,
                    teams
                );
                break;
            }
            case "renamed": {
                var changes = payload.getChanges();
                var from = (changes != null && changes.getLogin() != null) ? changes.getLogin().getFrom() : "-";
                logger.info("org={} action={} from={} to={}", orgLogin, action, from, orgLogin);
                break;
            }
            default: {
                logger.info("org={} action={}", orgLogin, action);
                break;
            }
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.ORGANIZATION;
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.ORGANIZATION;
    }
}
