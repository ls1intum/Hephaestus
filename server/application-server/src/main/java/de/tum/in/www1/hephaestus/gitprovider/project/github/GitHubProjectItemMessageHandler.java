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
 * Projects V2 items are organization-level events.
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

        String orgLogin = event.organization() != null ? event.organization().login() : null;

        log.info(
            "Received projects_v2_item event: action={}, itemNodeId={}, orgLogin={}",
            event.action(),
            itemDto.nodeId() != null ? sanitizeForLog(itemDto.nodeId()) : "unknown",
            orgLogin != null ? sanitizeForLog(orgLogin) : "unknown"
        );

        // Resolve scope from organization login
        Long scopeId = orgLogin != null ? scopeIdResolver.findScopeIdByOrgLogin(orgLogin).orElse(null) : null;
        if (scopeId == null) {
            log.debug(
                "Skipped projects_v2_item event: reason=noAssociatedScope, orgLogin={}",
                sanitizeForLog(orgLogin)
            );
            return;
        }

        ProcessingContext context = ProcessingContext.forWebhook(scopeId, null, event.action());

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
                    itemProcessor.processArchived(itemDto, project, context);
                } else {
                    log.debug("Skipped projects_v2_item archived event: reason=projectNotFound");
                }
            }
            case RESTORED -> {
                Project project = findProjectForItem(event);
                if (project != null) {
                    itemProcessor.processRestored(itemDto, project, context);
                } else {
                    log.debug("Skipped projects_v2_item restored event: reason=projectNotFound");
                }
            }
            case CREATED, EDITED, CONVERTED, REORDERED -> {
                Project project = findProjectForItem(event);
                if (project != null) {
                    itemProcessor.process(itemDto, project, context);
                } else {
                    log.debug("Skipped projects_v2_item event: reason=projectNotFound, action={}", event.action());
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
}
