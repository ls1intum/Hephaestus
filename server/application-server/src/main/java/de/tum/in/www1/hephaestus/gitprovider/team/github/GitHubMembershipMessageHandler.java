package de.tum.in.www1.hephaestus.gitprovider.team.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembership;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import java.util.Objects;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubMembershipMessageHandler extends GitHubMessageHandler<GHEventPayload.Membership> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMembershipMessageHandler.class);

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final GitHubTeamConverter teamConverter;
    private final GitHubUserConverter userConverter;

    public GitHubMembershipMessageHandler(
        TeamRepository teamRepository,
        UserRepository userRepository,
        GitHubTeamConverter teamConverter,
        GitHubUserConverter userConverter
    ) {
        super(GHEventPayload.Membership.class);
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.teamConverter = teamConverter;
        this.userConverter = userConverter;
    }

    @Override
    protected void handleEvent(GHEventPayload.Membership eventPayload) {
        var action = eventPayload.getAction();
        GHTeam ghTeam = eventPayload.getTeam();
        GHUser ghUser = eventPayload.getMember();
        String orgLogin = eventPayload.getOrganization() != null ? eventPayload.getOrganization().getLogin() : null;
        Long teamId = ghTeam != null ? ghTeam.getId() : null;
        Long userId = ghUser != null ? ghUser.getId() : null;

        logger.info("org={} action={} teamId={} userId={}", orgLogin, action, teamId, userId);

        if (ghTeam == null || ghUser == null) {
            // nothing to do
            return;
        }

        // Upsert team and force organization from payload to avoid any network lookups in converter
        Team team = teamRepository.findById(ghTeam.getId()).orElseGet(Team::new);
        team.setId(ghTeam.getId());
        team = teamConverter.update(ghTeam, team);
        if (orgLogin != null) {
            team.setOrganization(orgLogin);
        }
        team = teamRepository.save(team);

        // Upsert user from payload
        User toUpdate = userRepository.findById(ghUser.getId()).orElseGet(User::new);
        toUpdate.setId(ghUser.getId());
        toUpdate = userConverter.update(ghUser, toUpdate);
        User user = userRepository.save(toUpdate);
        final Long uid = user.getId();

        if ("removed".equals(action)) {
            // remove membership if present
            var toRemove = team
                .getMemberships()
                .stream()
                .filter(m -> m.getUser() != null && Objects.equals(m.getUser().getId(), uid))
                .findFirst()
                .orElse(null);
            if (toRemove != null) {
                team.removeMembership(toRemove);
                teamRepository.save(team);
            }
            return;
        }

        if ("added".equals(action)) {
            // ensure membership exists (default role MEMBER for team membership events)
            var targetRole = TeamMembership.Role.MEMBER;
            var existing = team
                .getMemberships()
                .stream()
                .filter(m -> m.getUser() != null && Objects.equals(m.getUser().getId(), uid))
                .findFirst()
                .orElse(null);
            if (existing == null) {
                team.addMembership(new TeamMembership(team, user, targetRole));
                teamRepository.save(team);
            } else if (existing.getRole() != targetRole) {
                existing.setRole(targetRole);
                teamRepository.save(team);
            }
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.MEMBERSHIP;
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.ORGANIZATION;
    }
}
