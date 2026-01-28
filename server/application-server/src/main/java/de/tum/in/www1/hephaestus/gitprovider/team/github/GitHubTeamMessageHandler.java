package de.tum.in.www1.hephaestus.gitprovider.team.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.team.github.dto.GitHubTeamEventDTO;
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

    GitHubTeamMessageHandler(
        GitHubTeamProcessor teamProcessor,
        ScopeIdResolver scopeIdResolver,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubTeamEventDTO.class, deserializer, transactionTemplate);
        this.teamProcessor = teamProcessor;
        this.scopeIdResolver = scopeIdResolver;
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
        ProcessingContext context = ProcessingContext.forWebhook(scopeId, null, event.action());

        switch (event.actionType()) {
            case GitHubEventAction.Team.DELETED -> teamProcessor.delete(teamDto.id(), context);
            case GitHubEventAction.Team.CREATED, GitHubEventAction.Team.EDITED -> teamProcessor.process(
                teamDto,
                orgLogin,
                context
            );
            default -> log.debug("Skipped team event: reason=unhandledAction, action={}", event.action());
        }
    }
}
