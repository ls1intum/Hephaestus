package de.tum.in.www1.hephaestus.gitprovider.project.github;

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
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub Projects V2 webhook events.
 * <p>
 * This handler processes project create, edit, close, reopen, and delete events.
 * <p>
 * <h2>Supported Owner Types</h2>
 * <p>
 * GitHub Projects V2 can be owned by organizations, repositories, or users.
 * This handler supports all three:
 * <ul>
 *   <li><b>ORGANIZATION:</b> Most common. Scope resolved via organization login.</li>
 *   <li><b>REPOSITORY:</b> Scope resolved via repository fullName (e.g., "owner/repo").</li>
 *   <li><b>USER:</b> Currently logged and skipped - user-level projects are not associated
 *       with a monitored workspace/scope.</li>
 * </ul>
 */
@Slf4j
@Component
public class GitHubProjectMessageHandler extends GitHubMessageHandler<GitHubProjectEventDTO> {

    private final GitHubProjectProcessor projectProcessor;
    private final ProjectRepository projectRepository;
    private final ScopeIdResolver scopeIdResolver;
    private final GitProviderRepository gitProviderRepository;

    GitHubProjectMessageHandler(
        GitHubProjectProcessor projectProcessor,
        ProjectRepository projectRepository,
        ScopeIdResolver scopeIdResolver,
        GitProviderRepository gitProviderRepository,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubProjectEventDTO.class, deserializer, transactionTemplate);
        this.projectProcessor = projectProcessor;
        this.projectRepository = projectRepository;
        this.scopeIdResolver = scopeIdResolver;
        this.gitProviderRepository = gitProviderRepository;
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

        // Detect owner type from the webhook payload
        Project.OwnerType ownerType = event.detectOwnerType();
        String ownerIdentifier = event.getOwnerIdentifier();
        Long ownerId = event.getOwnerId();

        log.info(
            "Received projects_v2 event: action={}, projectNumber={}, ownerType={}, owner={}",
            event.action(),
            projectDto.number(),
            ownerType,
            ownerIdentifier != null ? sanitizeForLog(ownerIdentifier) : "unknown"
        );

        // Resolve scope based on owner type
        Long scopeId = resolveScopeId(event, ownerType);
        if (scopeId == null) {
            log.debug(
                "Skipped projects_v2 event: reason=noAssociatedScope, ownerType={}, owner={}",
                ownerType,
                sanitizeForLog(ownerIdentifier)
            );
            return;
        }

        // Validate owner ID is available
        if (ownerId == null) {
            log.warn(
                "Skipped projects_v2 event: reason=missingOwnerId, ownerType={}, owner={}",
                ownerType,
                sanitizeForLog(ownerIdentifier)
            );
            return;
        }

        GitProvider gitHubProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseThrow(() -> new IllegalStateException("GitHub provider not configured"));
        ProcessingContext context = ProcessingContext.forWebhook(scopeId, gitHubProvider, event.action());

        // Extract the actor (sender) ID from the webhook event
        Long actorId = event.sender() != null ? event.sender().getDatabaseId() : null;

        GitHubEventAction.ProjectV2 actionType = GitHubEventAction.ProjectV2.fromString(event.action());
        switch (actionType) {
            case DELETED -> {
                // Resolve the synthetic PK via nodeId since getDatabaseId() returns the native ID
                String nodeId = projectDto.nodeId();
                if (nodeId != null) {
                    projectRepository
                        .findByNodeId(nodeId)
                        .ifPresent(project -> projectProcessor.delete(project.getId(), context));
                }
            }
            case CLOSED -> projectProcessor.processClosed(projectDto, ownerType, ownerId, context, actorId);
            case REOPENED -> projectProcessor.processReopened(projectDto, ownerType, ownerId, context, actorId);
            case CREATED, EDITED -> projectProcessor.process(projectDto, ownerType, ownerId, context, actorId);
            default -> log.debug("Skipped projects_v2 event: reason=unhandledAction, action={}", event.action());
        }
    }

    /**
     * Resolves the scope ID based on the owner type.
     * <p>
     * Resolution strategies:
     * <ul>
     *   <li>ORGANIZATION: Look up by organization login</li>
     *   <li>REPOSITORY: Look up by repository fullName</li>
     *   <li>USER: Not supported - returns null (user projects aren't associated with workspaces)</li>
     * </ul>
     *
     * @param event     the webhook event
     * @param ownerType the detected owner type
     * @return the scope ID, or null if not found or not supported
     */
    private Long resolveScopeId(GitHubProjectEventDTO event, Project.OwnerType ownerType) {
        return switch (ownerType) {
            case ORGANIZATION -> {
                String orgLogin = event.organization() != null ? event.organization().login() : null;
                if (orgLogin == null) {
                    yield null;
                }
                yield scopeIdResolver.findScopeIdByOrgLogin(orgLogin).orElse(null);
            }
            case REPOSITORY -> {
                String repoFullName = event.repository() != null ? event.repository().fullName() : null;
                if (repoFullName == null) {
                    yield null;
                }
                yield scopeIdResolver.findScopeIdByRepositoryName(repoFullName).orElse(null);
            }
            case USER -> {
                // User-level projects are not associated with a monitored workspace
                // Log at info level since this is a known limitation
                log.info(
                    "User-owned project detected - not currently supported: sender={}",
                    event.sender() != null ? sanitizeForLog(event.sender().login()) : "unknown"
                );
                yield null;
            }
        };
    }
}
