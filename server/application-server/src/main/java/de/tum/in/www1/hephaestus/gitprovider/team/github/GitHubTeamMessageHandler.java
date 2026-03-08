package de.tum.in.www1.hephaestus.gitprovider.team.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.github.dto.GitHubTeamEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub team webhook events.
 */
@Component
public class GitHubTeamMessageHandler extends GitHubMessageHandler<GitHubTeamEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubTeamMessageHandler.class);

    private final GitHubTeamProcessor teamProcessor;
    private final ScopeIdResolver scopeIdResolver;
    private final GitProviderRepository gitProviderRepository;
    private final TeamRepository teamRepository;
    private final RepositoryRepository repositoryRepository;
    private final TeamRepositoryPermissionRepository permissionRepository;

    GitHubTeamMessageHandler(
        GitHubTeamProcessor teamProcessor,
        ScopeIdResolver scopeIdResolver,
        GitProviderRepository gitProviderRepository,
        TeamRepository teamRepository,
        RepositoryRepository repositoryRepository,
        TeamRepositoryPermissionRepository permissionRepository,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubTeamEventDTO.class, deserializer, transactionTemplate);
        this.teamProcessor = teamProcessor;
        this.scopeIdResolver = scopeIdResolver;
        this.gitProviderRepository = gitProviderRepository;
        this.teamRepository = teamRepository;
        this.repositoryRepository = repositoryRepository;
        this.permissionRepository = permissionRepository;
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
    protected void handleEvent(GitHubTeamEventDTO event) {
        var teamDto = event.team();

        if (teamDto == null) {
            log.warn("Received team event with missing data: action={}", event.action());
            return;
        }

        String orgLogin = event.organization() != null ? event.organization().login() : null;

        log.info(
            "Received team event: action={}, teamName={}, orgLogin={}",
            event.action(),
            sanitizeForLog(teamDto.name()),
            orgLogin != null ? sanitizeForLog(orgLogin) : "unknown"
        );

        // Resolve scope from organization login
        Long scopeId = orgLogin != null ? scopeIdResolver.findScopeIdByOrgLogin(orgLogin).orElse(null) : null;
        if (scopeId == null) {
            log.debug("Skipped team event: reason=noAssociatedScope, orgLogin={}", sanitizeForLog(orgLogin));
            return;
        }
        // Create context for team events (no repository context available, but scope is resolved)
        GitProvider gitHubProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseThrow(() -> new IllegalStateException("GitHub provider not configured"));
        ProcessingContext context = ProcessingContext.forWebhook(scopeId, gitHubProvider, event.action());

        switch (event.actionType()) {
            case GitHubEventAction.Team.DELETED -> teamProcessor.delete(teamDto.id(), context);
            case GitHubEventAction.Team.CREATED, GitHubEventAction.Team.EDITED -> teamProcessor.process(
                teamDto,
                orgLogin,
                context
            );
            case GitHubEventAction.Team.ADDED_TO_REPOSITORY -> handleAddedToRepository(event, gitHubProvider);
            case GitHubEventAction.Team.REMOVED_FROM_REPOSITORY -> handleRemovedFromRepository(event, gitHubProvider);
            default -> log.debug("Skipped team event: reason=unhandledAction, action={}", event.action());
        }
    }

    /**
     * Handles the {@code added_to_repository} action by creating a TeamRepositoryPermission.
     * <p>
     * The webhook payload includes the team (with its native id) and the repository
     * (with its native id). We look up both by native ID + provider to find internal entities,
     * then create a permission record with the team's default permission level.
     */
    private void handleAddedToRepository(GitHubTeamEventDTO event, GitProvider provider) {
        var repoRef = event.repository();
        var teamDto = event.team();

        if (repoRef == null || repoRef.id() == null) {
            log.warn("Skipped added_to_repository: reason=noRepository, teamName={}", sanitizeForLog(teamDto.name()));
            return;
        }

        Long providerId = provider.getId();

        var teamOpt = teamRepository.findByNativeIdAndProviderId(teamDto.id(), providerId);
        if (teamOpt.isEmpty()) {
            log.debug(
                "Skipped added_to_repository: reason=teamNotFound, teamNativeId={}, teamName={}",
                teamDto.id(),
                sanitizeForLog(teamDto.name())
            );
            return;
        }

        var repoOpt = repositoryRepository.findByNativeIdAndProviderId(repoRef.id(), providerId);
        if (repoOpt.isEmpty()) {
            log.debug(
                "Skipped added_to_repository: reason=repoNotMonitored, repoNativeId={}, repoName={}",
                repoRef.id(),
                sanitizeForLog(repoRef.fullName())
            );
            return;
        }

        Team team = teamOpt.get();
        Repository repo = repoOpt.get();

        // Map the team's default permission from the DTO (e.g., "pull", "push", "admin")
        TeamRepositoryPermission.PermissionLevel level = mapWebhookPermission(teamDto.permission());

        // Check if permission already exists
        var existingOpt = permissionRepository.findByTeam_IdAndRepository_Id(team.getId(), repo.getId());
        if (existingOpt.isPresent()) {
            // Update permission level if changed
            TeamRepositoryPermission existing = existingOpt.get();
            if (existing.getPermission() != level) {
                existing.setPermission(level);
                permissionRepository.save(existing);
                log.info(
                    "Updated team repo permission: teamName={}, repoName={}, permission={}",
                    sanitizeForLog(team.getName()),
                    sanitizeForLog(repo.getNameWithOwner()),
                    level
                );
            }
        } else {
            TeamRepositoryPermission permission = new TeamRepositoryPermission(team, repo, level);
            permissionRepository.save(permission);
            log.info(
                "Created team repo permission: teamName={}, repoName={}, permission={}",
                sanitizeForLog(team.getName()),
                sanitizeForLog(repo.getNameWithOwner()),
                level
            );
        }
    }

    /**
     * Handles the {@code removed_from_repository} action by deleting the TeamRepositoryPermission.
     */
    private void handleRemovedFromRepository(GitHubTeamEventDTO event, GitProvider provider) {
        var repoRef = event.repository();
        var teamDto = event.team();

        if (repoRef == null || repoRef.id() == null) {
            log.warn(
                "Skipped removed_from_repository: reason=noRepository, teamName={}",
                sanitizeForLog(teamDto.name())
            );
            return;
        }

        Long providerId = provider.getId();

        var teamOpt = teamRepository.findByNativeIdAndProviderId(teamDto.id(), providerId);
        if (teamOpt.isEmpty()) {
            log.debug(
                "Skipped removed_from_repository: reason=teamNotFound, teamNativeId={}, teamName={}",
                teamDto.id(),
                sanitizeForLog(teamDto.name())
            );
            return;
        }

        var repoOpt = repositoryRepository.findByNativeIdAndProviderId(repoRef.id(), providerId);
        if (repoOpt.isEmpty()) {
            log.debug(
                "Skipped removed_from_repository: reason=repoNotMonitored, repoNativeId={}, repoName={}",
                repoRef.id(),
                sanitizeForLog(repoRef.fullName())
            );
            return;
        }

        Team team = teamOpt.get();
        Repository repo = repoOpt.get();

        permissionRepository
            .findByTeam_IdAndRepository_Id(team.getId(), repo.getId())
            .ifPresentOrElse(
                permission -> {
                    permissionRepository.delete(permission);
                    log.info(
                        "Deleted team repo permission: teamName={}, repoName={}",
                        sanitizeForLog(team.getName()),
                        sanitizeForLog(repo.getNameWithOwner())
                    );
                },
                () ->
                    log.debug(
                        "Skipped removed_from_repository: reason=permissionNotFound, teamName={}, repoName={}",
                        sanitizeForLog(team.getName()),
                        sanitizeForLog(repo.getNameWithOwner())
                    )
            );
    }

    /**
     * Maps GitHub REST API permission strings to our PermissionLevel enum.
     * <p>
     * GitHub webhook payloads use different permission names than the GraphQL API:
     * REST API: "pull", "triage", "push", "maintain", "admin"
     * GraphQL: READ, TRIAGE, WRITE, MAINTAIN, ADMIN
     */
    private static TeamRepositoryPermission.PermissionLevel mapWebhookPermission(String permission) {
        if (permission == null) {
            return TeamRepositoryPermission.PermissionLevel.READ;
        }
        return switch (permission.toLowerCase()) {
            case "admin" -> TeamRepositoryPermission.PermissionLevel.ADMIN;
            case "maintain" -> TeamRepositoryPermission.PermissionLevel.MAINTAIN;
            case "push", "write" -> TeamRepositoryPermission.PermissionLevel.WRITE;
            case "triage" -> TeamRepositoryPermission.PermissionLevel.TRIAGE;
            default -> TeamRepositoryPermission.PermissionLevel.READ;
        };
    }
}
