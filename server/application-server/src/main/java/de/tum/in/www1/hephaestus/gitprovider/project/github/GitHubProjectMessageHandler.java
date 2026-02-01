package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub Projects V2 webhook events.
 * <p>
 * This handler processes project create, edit, close, reopen, and delete events.
 * Projects V2 are organization-level events.
 */
@Component
public class GitHubProjectMessageHandler extends GitHubMessageHandler<GitHubProjectEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubProjectMessageHandler.class);

    private final GitHubProjectProcessor projectProcessor;
    private final ScopeIdResolver scopeIdResolver;

    GitHubProjectMessageHandler(
        GitHubProjectProcessor projectProcessor,
        ScopeIdResolver scopeIdResolver,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubProjectEventDTO.class, deserializer, transactionTemplate);
        this.projectProcessor = projectProcessor;
        this.scopeIdResolver = scopeIdResolver;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.PROJECTS_V2;
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.ORGANIZATION;
    }

    @Override
    protected void handleEvent(GitHubProjectEventDTO event) {
        var projectDto = event.project();

        if (projectDto == null) {
            log.warn("Received projects_v2 event with missing data: action={}", event.action());
            return;
        }

        String orgLogin = event.organization() != null ? event.organization().login() : null;

        log.info(
            "Received projects_v2 event: action={}, projectNumber={}, orgLogin={}",
            event.action(),
            projectDto.number(),
            orgLogin != null ? sanitizeForLog(orgLogin) : "unknown"
        );

        // Resolve scope from organization login
        Long scopeId = orgLogin != null ? scopeIdResolver.findScopeIdByOrgLogin(orgLogin).orElse(null) : null;
        if (scopeId == null) {
            log.debug("Skipped projects_v2 event: reason=noAssociatedScope, orgLogin={}", sanitizeForLog(orgLogin));
            return;
        }

        // Resolve organization ID for the project owner
        Long organizationId = event.organization() != null ? event.organization().id() : null;
        if (organizationId == null) {
            log.warn("Skipped projects_v2 event: reason=missingOrganizationId");
            return;
        }

        ProcessingContext context = ProcessingContext.forWebhook(scopeId, null, event.action());

        GitHubEventAction.ProjectV2 actionType = GitHubEventAction.ProjectV2.fromString(event.action());
        switch (actionType) {
            case DELETED -> {
                Long projectId = projectDto.getDatabaseId();
                if (projectId != null) {
                    projectProcessor.delete(projectId, context);
                }
            }
            case CLOSED -> projectProcessor.processClosed(
                projectDto,
                Project.OwnerType.ORGANIZATION,
                organizationId,
                context
            );
            case REOPENED -> projectProcessor.processReopened(
                projectDto,
                Project.OwnerType.ORGANIZATION,
                organizationId,
                context
            );
            case CREATED, EDITED -> projectProcessor.process(
                projectDto,
                Project.OwnerType.ORGANIZATION,
                organizationId,
                context
            );
            default -> log.debug("Skipped projects_v2 event: reason=unhandledAction, action={}", event.action());
        }
    }
}
