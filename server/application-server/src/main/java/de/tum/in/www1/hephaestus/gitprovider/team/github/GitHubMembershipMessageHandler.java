package de.tum.in.www1.hephaestus.gitprovider.team.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.github.dto.GitHubMembershipEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembership;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub membership webhook events (team member changes).
 * <p>
 * Uses DTOs directly for complete field coverage.
 * Delegates user creation to {@link GitHubUserProcessor}.
 * Persists team memberships when members are added or removed from teams.
 */
@Component
public class GitHubMembershipMessageHandler extends GitHubMessageHandler<GitHubMembershipEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMembershipMessageHandler.class);

    private final GitHubUserProcessor userProcessor;
    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    GitHubMembershipMessageHandler(
        GitHubUserProcessor userProcessor,
        TeamRepository teamRepository,
        TeamMembershipRepository teamMembershipRepository
    ) {
        super(GitHubMembershipEventDTO.class);
        this.userProcessor = userProcessor;
        this.teamRepository = teamRepository;
        this.teamMembershipRepository = teamMembershipRepository;
    }

    @Override
    protected String getEventKey() {
        return "membership";
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.ORGANIZATION;
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubMembershipEventDTO event) {
        var memberDto = event.member();
        var teamDto = event.team();

        if (memberDto == null || teamDto == null) {
            logger.warn("Received membership event with missing data");
            return;
        }

        logger.info(
            "Received membership event: action={}, member={}, team={}, org={}",
            event.action(),
            memberDto.login(),
            teamDto.name(),
            event.organization() != null ? event.organization().login() : "unknown"
        );

        // Ensure user exists via processor
        User user = userProcessor.ensureExists(memberDto);
        if (user == null) {
            logger.warn("Could not create/find user {} for membership event", memberDto.login());
            return;
        }

        // Get the team
        Team team = teamRepository.findById(teamDto.id()).orElse(null);
        if (team == null) {
            // Team should be created by team webhook, just log
            logger.warn("Team {} not found for membership event", teamDto.id());
            return;
        }

        // Handle the membership action
        switch (event.action()) {
            case "added" -> handleMemberAdded(team, user);
            case "removed" -> handleMemberRemoved(team, user);
            default -> logger.debug("Unhandled membership action: {}", event.action());
        }
    }

    private void handleMemberAdded(Team team, User user) {
        // Check if membership already exists
        if (teamMembershipRepository.existsByTeam_IdAndUser_Id(team.getId(), user.getId())) {
            logger.debug("Membership already exists for user {} in team {}", user.getLogin(), team.getName());
            return;
        }

        // Create and persist the membership
        TeamMembership membership = new TeamMembership(team, user, TeamMembership.Role.MEMBER);
        teamMembershipRepository.save(membership);
        logger.info("Created membership for user {} in team {}", user.getLogin(), team.getName());
    }

    private void handleMemberRemoved(Team team, User user) {
        // Delete the membership if it exists
        if (!teamMembershipRepository.existsByTeam_IdAndUser_Id(team.getId(), user.getId())) {
            logger.debug("No membership found to remove for user {} in team {}", user.getLogin(), team.getName());
            return;
        }

        teamMembershipRepository.deleteByTeam_IdAndUser_Id(team.getId(), user.getId());
        logger.info("Removed membership for user {} from team {}", user.getLogin(), team.getName());
    }
}
