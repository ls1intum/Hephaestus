package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectItemEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub Projects V2 Item webhook events.
 * <p>
 * This handler processes item create, edit, delete, archive, and restore events.
 * <p>
 * <h2>Supported Owner Types</h2>
 * <p>
 * Project items inherit the owner type from their parent project.
 * This handler supports all three owner types:
 * <ul>
 *   <li><b>ORGANIZATION:</b> Most common. Scope resolved via organization login.</li>
 *   <li><b>REPOSITORY:</b> Scope resolved via repository fullName (e.g., "owner/repo").</li>
 *   <li><b>USER:</b> Currently logged and skipped - user-level projects are not associated
 *       with a monitored workspace/scope.</li>
 * </ul>
 */
@Component
public class GitHubProjectItemMessageHandler extends GitHubMessageHandler<GitHubProjectItemEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubProjectItemMessageHandler.class);

    private final GitHubProjectItemProcessor itemProcessor;
    private final ProjectRepository projectRepository;
    private final ScopeIdResolver scopeIdResolver;

    GitHubProjectItemMessageHandler(
        GitHubProjectItemProcessor itemProcessor,
        ProjectRepository projectRepository,
        ScopeIdResolver scopeIdResolver,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubProjectItemEventDTO.class, deserializer, transactionTemplate);
        this.itemProcessor = itemProcessor;
        this.projectRepository = projectRepository;
        this.scopeIdResolver = scopeIdResolver;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.PROJECTS_V2_ITEM;
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.ORGANIZATION;
    }

    @Override
    protected void handleEvent(GitHubProjectItemEventDTO event) {
        var itemDto = event.item();

        if (itemDto == null) {
            log.warn("Received projects_v2_item event with missing data: action={}", event.action());
            return;
        }

        // Detect owner type from the webhook payload
        Project.OwnerType ownerType = event.detectOwnerType();
        String ownerIdentifier = event.getOwnerIdentifier();

        log.info(
            "Received projects_v2_item event: action={}, itemNodeId={}, ownerType={}, owner={}",
            event.action(),
            itemDto.nodeId() != null ? sanitizeForLog(itemDto.nodeId()) : "unknown",
            ownerType,
            ownerIdentifier != null ? sanitizeForLog(ownerIdentifier) : "unknown"
        );

        // Resolve scope based on owner type
        Long scopeId = resolveScopeId(event, ownerType);
        if (scopeId == null) {
            log.debug(
                "Skipped projects_v2_item event: reason=noAssociatedScope, ownerType={}, owner={}",
                ownerType,
                sanitizeForLog(ownerIdentifier)
            );
            return;
        }

        ProcessingContext context = ProcessingContext.forWebhook(scopeId, null, event.action());

        // Extract the actor (sender) ID from the webhook event
        Long actorId = event.sender() != null ? event.sender().getDatabaseId() : null;

        // For item events, we need to look up the project by node ID
        // The webhook payload doesn't include the project ID directly for all actions
        // We'll need to handle this based on the specific action
        GitHubEventAction.ProjectV2Item actionType = GitHubEventAction.ProjectV2Item.fromString(event.action());

        switch (actionType) {
            case DELETED -> {
                Long itemId = itemDto.getDatabaseId();
                // We don't have project ID in delete events, but we can try to find the item
                if (itemId != null) {
                    // The item might still exist if we haven't processed the delete yet
                    // Try to find the associated project before deletion
                    itemProcessor.delete(itemId, null, context);
                }
            }
            case ARCHIVED -> {
                Project project = findProjectForItem(event);
                if (project != null) {
                    itemProcessor.processArchived(itemDto, project, context, actorId);
                } else {
                    log.debug("Skipped projects_v2_item archived event: reason=projectNotFound");
                }
            }
            case RESTORED -> {
                Project project = findProjectForItem(event);
                if (project != null) {
                    itemProcessor.processRestored(itemDto, project, context, actorId);
                } else {
                    log.debug("Skipped projects_v2_item restored event: reason=projectNotFound");
                }
            }
            case CREATED, EDITED -> {
                Project project = findProjectForItem(event);
                if (project != null) {
                    itemProcessor.process(itemDto, project, context, actorId);
                } else {
                    log.debug("Skipped projects_v2_item event: reason=projectNotFound, action={}", event.action());
                }
            }
            case CONVERTED -> {
                Project project = findProjectForItem(event);
                if (project != null) {
                    itemProcessor.processConverted(itemDto, project, context, actorId);
                } else {
                    log.debug("Skipped projects_v2_item converted event: reason=projectNotFound");
                }
            }
            case REORDERED -> {
                Project project = findProjectForItem(event);
                if (project != null) {
                    itemProcessor.processReordered(itemDto, project, context, actorId);
                } else {
                    log.debug("Skipped projects_v2_item reordered event: reason=projectNotFound");
                }
            }
            default -> log.debug("Skipped projects_v2_item event: reason=unhandledAction, action={}", event.action());
        }
    }

    /**
     * Finds the project associated with an item event.
     * <p>
     * The webhook payload includes project_node_id which we use to look up the project.
     */
    private Project findProjectForItem(GitHubProjectItemEventDTO event) {
        var itemDto = event.item();
        if (itemDto == null) {
            return null;
        }

        // Extract project_node_id from the webhook payload
        String projectNodeId = itemDto.projectNodeId();
        if (projectNodeId == null || projectNodeId.isBlank()) {
            log.debug(
                "Cannot find project for item: reason=missingProjectNodeId, itemNodeId={}",
                sanitizeForLog(itemDto.nodeId())
            );
            return null;
        }

        // Look up the project by its GraphQL node ID
        return projectRepository.findByNodeId(projectNodeId).orElse(null);
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
    private Long resolveScopeId(GitHubProjectItemEventDTO event, Project.OwnerType ownerType) {
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
                    "User-owned project item detected - not currently supported: sender={}",
                    event.sender() != null ? sanitizeForLog(event.sender().login()) : "unknown"
                );
                yield null;
            }
        };
    }
}
