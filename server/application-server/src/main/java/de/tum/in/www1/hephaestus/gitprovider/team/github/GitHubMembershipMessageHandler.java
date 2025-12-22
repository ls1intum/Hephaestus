package de.tum.in.www1.hephaestus.gitprovider.team.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.github.dto.GitHubMembershipEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub membership webhook events (team member changes).
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
 * <p>
 * TODO: Full implementation when TeamMembershipRepository is available.
 */
@Component
public class GitHubMembershipMessageHandler extends GitHubMessageHandler<GitHubMembershipEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMembershipMessageHandler.class);

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    GitHubMembershipMessageHandler(UserRepository userRepository, TeamRepository teamRepository) {
        super(GitHubMembershipEventDTO.class);
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
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

        // Ensure user exists
        if (!userRepository.existsById(memberDto.id())) {
            User newUser = new User();
            newUser.setId(memberDto.id());
            newUser.setLogin(memberDto.login());
            newUser.setAvatarUrl(memberDto.avatarUrl());
            newUser.setName(memberDto.name());
            userRepository.save(newUser);
        }

        // Ensure team exists
        if (!teamRepository.existsById(teamDto.id())) {
            // Team should be created by team webhook, just log
            logger.warn("Team {} not found for membership event", teamDto.id());
            return;
        }

        // Log the action - full membership tracking can be added later
        switch (event.action()) {
            case "added" -> logger.info("Member {} added to team {}", memberDto.login(), teamDto.name());
            case "removed" -> logger.info("Member {} removed from team {}", memberDto.login(), teamDto.name());
            default -> logger.debug("Unhandled membership action: {}", event.action());
        }
    }
}
