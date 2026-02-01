package de.tum.in.www1.hephaestus.gitprovider.project.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItem;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItemRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectItemDTO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub Projects V2 items.
 * <p>
 * This service handles the conversion of GitHubProjectItemDTO to ProjectItem entities,
 * persists them, and publishes appropriate domain events.
 */
@Service
public class GitHubProjectItemProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubProjectItemProcessor.class);

    private final ProjectItemRepository projectItemRepository;
    private final IssueRepository issueRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubProjectItemProcessor(
        ProjectItemRepository projectItemRepository,
        IssueRepository issueRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.projectItemRepository = projectItemRepository;
        this.issueRepository = issueRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub project item DTO and persist it as a ProjectItem entity.
     *
     * @param dto the GitHub project item DTO
     * @param project the project this item belongs to
     * @param context processing context with scope information
     * @return the persisted ProjectItem entity
     */
    @Transactional
    public ProjectItem process(GitHubProjectItemDTO dto, Project project, ProcessingContext context) {
        if (dto == null) {
            log.warn(
                "Skipped project item processing: reason=nullDto, projectId={}",
                project != null ? project.getId() : null
            );
            return null;
        }

        if (dto.nodeId() == null || dto.nodeId().isBlank()) {
            log.warn(
                "Skipped project item processing: reason=missingNodeId, projectId={}",
                project != null ? project.getId() : null
            );
            return null;
        }

        Long dbId = dto.getDatabaseId();
        if (dbId == null) {
            log.warn("Skipped project item processing: reason=missingDatabaseId, nodeId={}", dto.nodeId());
            return null;
        }

        ProjectItem.ContentType contentType = dto.getContentTypeEnum();
        if (contentType == null) {
            log.warn("Skipped project item processing: reason=invalidContentType, contentType={}", dto.contentType());
            return null;
        }

        // Check if item already exists
        boolean isNew = !projectItemRepository.existsByProjectIdAndNodeId(project.getId(), dto.nodeId());

        // Resolve linked issue if applicable
        Long issueId = null;
        if (contentType == ProjectItem.ContentType.ISSUE || contentType == ProjectItem.ContentType.PULL_REQUEST) {
            issueId = resolveIssueId(dto);
        }

        // Perform atomic upsert
        projectItemRepository.upsertCore(
            dbId,
            dto.nodeId(),
            project.getId(),
            contentType.name(),
            issueId,
            sanitize(dto.draftTitle()),
            sanitize(dto.draftBody()),
            dto.archived(),
            dto.createdAt(),
            dto.updatedAt()
        );

        // Fetch the entity to return a managed instance
        ProjectItem item = projectItemRepository
            .findByProjectIdAndNodeId(project.getId(), dto.nodeId())
            .orElseThrow(() ->
                new IllegalStateException(
                    "ProjectItem not found after upsert: projectId=" + project.getId() + ", nodeId=" + dto.nodeId()
                )
            );

        // Publish domain events
        EventPayload.ProjectItemData itemData = EventPayload.ProjectItemData.from(item);
        EventContext eventContext = EventContext.from(context);

        if (isNew) {
            eventPublisher.publishEvent(new DomainEvent.ProjectItemCreated(itemData, project.getId(), eventContext));
            log.debug("Created project item: itemId={}, nodeId={}", item.getId(), item.getNodeId());
        } else {
            eventPublisher.publishEvent(
                new DomainEvent.ProjectItemUpdated(itemData, project.getId(), Set.of(), eventContext)
            );
            log.debug("Updated project item: itemId={}, nodeId={}", item.getId(), item.getNodeId());
        }

        return item;
    }

    /**
     * Process an item archived event.
     *
     * @param dto the GitHub project item DTO
     * @param project the project this item belongs to
     * @param context processing context
     * @return the updated ProjectItem entity
     */
    @Transactional
    public ProjectItem processArchived(GitHubProjectItemDTO dto, Project project, ProcessingContext context) {
        ProjectItem item = process(dto, project, context);
        if (item != null) {
            EventPayload.ProjectItemData itemData = EventPayload.ProjectItemData.from(item);
            eventPublisher.publishEvent(
                new DomainEvent.ProjectItemArchived(itemData, project.getId(), EventContext.from(context))
            );
            log.info("Project item archived: itemId={}, nodeId={}", item.getId(), item.getNodeId());
        }
        return item;
    }

    /**
     * Process an item restored (unarchived) event.
     *
     * @param dto the GitHub project item DTO
     * @param project the project this item belongs to
     * @param context processing context
     * @return the updated ProjectItem entity
     */
    @Transactional
    public ProjectItem processRestored(GitHubProjectItemDTO dto, Project project, ProcessingContext context) {
        ProjectItem item = process(dto, project, context);
        if (item != null) {
            EventPayload.ProjectItemData itemData = EventPayload.ProjectItemData.from(item);
            eventPublisher.publishEvent(
                new DomainEvent.ProjectItemRestored(itemData, project.getId(), EventContext.from(context))
            );
            log.info("Project item restored: itemId={}, nodeId={}", item.getId(), item.getNodeId());
        }
        return item;
    }

    /**
     * Delete a project item by its ID.
     *
     * @param itemId the item database ID
     * @param projectId the project ID
     * @param context processing context
     */
    @Transactional
    public void delete(Long itemId, Long projectId, ProcessingContext context) {
        if (itemId == null) {
            return;
        }

        projectItemRepository
            .findById(itemId)
            .ifPresent(item -> {
                projectItemRepository.delete(item);
                eventPublisher.publishEvent(
                    new DomainEvent.ProjectItemDeleted(itemId, projectId, EventContext.from(context))
                );
                log.info("Deleted project item: itemId={}, nodeId={}", itemId, item.getNodeId());
            });
    }

    /**
     * Removes items from a project that are no longer present in the synced list.
     * <p>
     * This should only be called after a complete sync to avoid deleting items
     * that simply weren't fetched due to pagination limits or rate limiting.
     *
     * @param projectId the project ID
     * @param syncedNodeIds list of item node IDs that were synced
     * @param context processing context
     * @return number of items removed
     */
    @Transactional
    public int removeStaleItems(Long projectId, List<String> syncedNodeIds, ProcessingContext context) {
        if (projectId == null || syncedNodeIds == null || syncedNodeIds.isEmpty()) {
            return 0;
        }

        int removed = projectItemRepository.deleteByProjectIdAndNodeIdNotIn(projectId, syncedNodeIds);
        if (removed > 0) {
            log.info("Removed stale project items: projectId={}, count={}", projectId, removed);
        }
        return removed;
    }

    /**
     * Resolve the issue ID from the DTO, verifying the issue exists locally.
     * <p>
     * GitHub Projects V2 are organization-scoped and can contain items from ANY
     * repository in the organization. If an item references an issue from a repository
     * that is not being synced by this Hephaestus instance, we gracefully skip the
     * issue link rather than failing with a FK constraint violation.
     *
     * @param dto the project item DTO containing the GitHub issue database ID
     * @return the issue ID if it exists locally, null otherwise
     */
    private Long resolveIssueId(GitHubProjectItemDTO dto) {
        if (dto.issueId() == null) {
            return null;
        }

        // Verify the issue exists locally before returning the ID.
        // This prevents FK constraint violations for items referencing issues
        // from repositories that are not being synced.
        if (issueRepository.existsById(dto.issueId())) {
            return dto.issueId();
        }

        // Issue doesn't exist locally - this is expected for cross-repository items
        // in projects that span multiple repositories (some of which may not be monitored).
        log.debug(
            "Project item references issue not synced locally: issueId={}, itemNodeId={}",
            dto.issueId(),
            dto.nodeId()
        );
        return null;
    }

    /**
     * Sanitize string for PostgreSQL storage (removes null characters).
     */
    private String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("\u0000", "");
    }
}
