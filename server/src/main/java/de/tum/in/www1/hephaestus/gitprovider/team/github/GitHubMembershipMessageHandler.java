package de.tum.in.www1.hephaestus.gitprovider.team.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub membership webhook events (team member changes).
 */
@Component
public class GitHubMembershipMessageHandler extends GitHubMessageHandler<GitHubMembershipEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubMembershipMessageHandler.class);

    private static final String GITHUB_SERVER_URL = "https://github.com";

    private final GitHubUserProcessor userProcessor;
    private final GitProviderRepository gitProviderRepository;
    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    GitHubMembershipMessageHandler(
        GitHubUserProcessor userProcessor,
        GitProviderRepository gitProviderRepository,
        TeamRepository teamRepository,
        TeamMembershipRepository teamMembershipRepository,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubMembershipEventDTO.class, deserializer, transactionTemplate);
        this.userProcessor = userProcessor;
        this.gitProviderRepository = gitProviderRepository;
        this.teamRepository = teamRepository;
        this.teamMembershipRepository = teamMembershipRepository;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.MEMBERSHIP;
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
            log.warn("Received membership event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received membership event: action={}, userLogin={}, teamName={}, orgLogin={}",
            event.action(),
            sanitizeForLog(memberDto.login()),
            sanitizeForLog(teamDto.name()),
            event.organization() != null ? sanitizeForLog(event.organization().login()) : "unknown"
        );

        // Resolve GitHub provider ID for user upsert
        Long providerId = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, GITHUB_SERVER_URL)
            .orElseThrow(() -> new IllegalStateException("GitProvider not found for GitHub"))
            .getId();

        // Ensure user exists via processor
        User user = userProcessor.ensureExists(memberDto, providerId);
        if (user == null) {
            log.warn("Skipped membership event: reason=userNotFound, userLogin={}", sanitizeForLog(memberDto.login()));
            return;
        }

        // Get the team
        Team team = teamRepository.findById(teamDto.id()).orElse(null);
        if (team == null) {
            // Team should be created by team webhook
            log.warn("Skipped membership event: reason=unknownTeam, teamId={}", teamDto.id());
            return;
        }

        // Handle the membership action
        switch (event.actionType()) {
            case GitHubEventAction.Membership.ADDED -> handleMemberAdded(team, user);
            case GitHubEventAction.Membership.REMOVED -> handleMemberRemoved(team, user);
            default -> log.debug("Skipped membership event: reason=unhandledAction, action={}", event.action());
        }
    }

    private void handleMemberAdded(Team team, User user) {
        // Check if membership already exists
        if (teamMembershipRepository.existsByTeam_IdAndUser_Id(team.getId(), user.getId())) {
            log.debug(
                "Skipped membership creation: reason=alreadyExists, userLogin={}, teamName={}",
                sanitizeForLog(user.getLogin()),
                sanitizeForLog(team.getName())
            );
            return;
        }

        // Create and persist the membership
        TeamMembership membership = new TeamMembership(team, user, TeamMembership.Role.MEMBER);
        teamMembershipRepository.save(membership);
        log.info(
            "Created team membership: userLogin={}, teamName={}",
            sanitizeForLog(user.getLogin()),
            sanitizeForLog(team.getName())
        );
    }

    private void handleMemberRemoved(Team team, User user) {
        // Delete the membership if it exists
        if (!teamMembershipRepository.existsByTeam_IdAndUser_Id(team.getId(), user.getId())) {
            log.debug(
                "Skipped membership removal: reason=notFound, userLogin={}, teamName={}",
                sanitizeForLog(user.getLogin()),
                sanitizeForLog(team.getName())
            );
            return;
        }

        teamMembershipRepository.deleteByTeam_IdAndUser_Id(team.getId(), user.getId());
        log.info(
            "Removed team membership: userLogin={}, teamName={}",
            sanitizeForLog(user.getLogin()),
            sanitizeForLog(team.getName())
        );
    }
}
