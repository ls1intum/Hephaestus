package de.tum.in.www1.hephaestus.gitprovider.project.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.github.BaseGitHubProcessor;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItem;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItemRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectItemDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import java.util.List;
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
 * <p>
 * Extends {@link BaseGitHubProcessor} for consistency with other GitHub entity processors
 * and to reuse common functionality like the {@code sanitize()} method.
 */
@Service
public class GitHubProjectItemProcessor extends BaseGitHubProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubProjectItemProcessor.class);

    private final ProjectItemRepository projectItemRepository;
    private final IssueRepository issueRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubProjectItemProcessor(
        ProjectItemRepository projectItemRepository,
        IssueRepository issueRepository,
        UserRepository userRepository,
        LabelRepository labelRepository,
        MilestoneRepository milestoneRepository,
        GitHubUserProcessor gitHubUserProcessor,
        ApplicationEventPublisher eventPublisher
    ) {
        super(userRepository, labelRepository, milestoneRepository, gitHubUserProcessor);
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
        return process(dto, project, context, null);
    }

    /**
     * Process a GitHub project item DTO and persist it as a ProjectItem entity.
     *
     * @param dto the GitHub project item DTO
     * @param project the project this item belongs to
     * @param context processing context with scope information
     * @param actorId the ID of the user who performed the action (from webhook sender), or null for sync
     * @return the persisted ProjectItem entity
     */
    @Transactional
    public ProjectItem process(GitHubProjectItemDTO dto, Project project, ProcessingContext context, Long actorId) {
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
        Long contentDatabaseId = null;
        if (contentType == ProjectItem.ContentType.ISSUE || contentType == ProjectItem.ContentType.PULL_REQUEST) {
            issueId = resolveIssueId(dto);
            // Always store the content database ID so orphaned items can be relinked
            // after the issue sync completes (even if the issue doesn't exist locally yet)
            contentDatabaseId = dto.issueId();
        }

        // Resolve creator if applicable
        Long creatorId = resolveCreatorId(dto);

        // Perform atomic upsert
        projectItemRepository.upsertCore(
            dbId,
            dto.nodeId(),
            project.getId(),
            contentType.name(),
            issueId,
            contentDatabaseId,
            sanitize(dto.draftTitle()),
            sanitize(dto.draftBody()),
            dto.archived(),
            creatorId,
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

        // Publish domain events with actor information
        EventPayload.ProjectItemData itemData = EventPayload.ProjectItemData.from(item, actorId);
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
        return processArchived(dto, project, context, null);
    }

    /**
     * Process an item archived event.
     *
     * @param dto the GitHub project item DTO
     * @param project the project this item belongs to
     * @param context processing context
     * @param actorId the ID of the user who archived the item (from webhook sender), or null for sync
     * @return the updated ProjectItem entity
     */
    @Transactional
    public ProjectItem processArchived(
        GitHubProjectItemDTO dto,
        Project project,
        ProcessingContext context,
        Long actorId
    ) {
        ProjectItem item = process(dto, project, context, actorId);
        if (item != null) {
            EventPayload.ProjectItemData itemData = EventPayload.ProjectItemData.from(item, actorId);
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
        return processRestored(dto, project, context, null);
    }

    /**
     * Process an item restored (unarchived) event.
     *
     * @param dto the GitHub project item DTO
     * @param project the project this item belongs to
     * @param context processing context
     * @param actorId the ID of the user who restored the item (from webhook sender), or null for sync
     * @return the updated ProjectItem entity
     */
    @Transactional
    public ProjectItem processRestored(
        GitHubProjectItemDTO dto,
        Project project,
        ProcessingContext context,
        Long actorId
    ) {
        ProjectItem item = process(dto, project, context, actorId);
        if (item != null) {
            EventPayload.ProjectItemData itemData = EventPayload.ProjectItemData.from(item, actorId);
            eventPublisher.publishEvent(
                new DomainEvent.ProjectItemRestored(itemData, project.getId(), EventContext.from(context))
            );
            log.info("Project item restored: itemId={}, nodeId={}", item.getId(), item.getNodeId());
        }
        return item;
    }

    /**
     * Process an item converted event (draft issue converted to real issue).
     *
     * @param dto the GitHub project item DTO
     * @param project the project this item belongs to
     * @param context processing context
     * @return the updated ProjectItem entity
     */
    @Transactional
    public ProjectItem processConverted(GitHubProjectItemDTO dto, Project project, ProcessingContext context) {
        return processConverted(dto, project, context, null);
    }

    /**
     * Process an item converted event (draft issue converted to real issue).
     *
     * @param dto the GitHub project item DTO
     * @param project the project this item belongs to
     * @param context processing context
     * @param actorId the ID of the user who converted the item (from webhook sender), or null for sync
     * @return the updated ProjectItem entity
     */
    @Transactional
    public ProjectItem processConverted(
        GitHubProjectItemDTO dto,
        Project project,
        ProcessingContext context,
        Long actorId
    ) {
        ProjectItem item = process(dto, project, context, actorId);
        if (item != null) {
            EventPayload.ProjectItemData itemData = EventPayload.ProjectItemData.from(item, actorId);
            eventPublisher.publishEvent(
                new DomainEvent.ProjectItemConverted(itemData, project.getId(), EventContext.from(context))
            );
            log.info("Project item converted: itemId={}, nodeId={}", item.getId(), item.getNodeId());
        }
        return item;
    }

    /**
     * Process an item reordered event (position changed in project view).
     *
     * @param dto the GitHub project item DTO
     * @param project the project this item belongs to
     * @param context processing context
     * @return the updated ProjectItem entity
     */
    @Transactional
    public ProjectItem processReordered(GitHubProjectItemDTO dto, Project project, ProcessingContext context) {
        return processReordered(dto, project, context, null);
    }

    /**
     * Process an item reordered event (position changed in project view).
     *
     * @param dto the GitHub project item DTO
     * @param project the project this item belongs to
     * @param context processing context
     * @param actorId the ID of the user who reordered the item (from webhook sender), or null for sync
     * @return the updated ProjectItem entity
     */
    @Transactional
    public ProjectItem processReordered(
        GitHubProjectItemDTO dto,
        Project project,
        ProcessingContext context,
        Long actorId
    ) {
        ProjectItem item = process(dto, project, context, actorId);
        if (item != null) {
            EventPayload.ProjectItemData itemData = EventPayload.ProjectItemData.from(item, actorId);
            eventPublisher.publishEvent(
                new DomainEvent.ProjectItemReordered(itemData, project.getId(), EventContext.from(context))
            );
            log.debug("Project item reordered: itemId={}, nodeId={}", item.getId(), item.getNodeId());
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
     * Removes stale Draft Issues that no longer exist in the project.
     * <p>
     * This method is used by the project-side sync which only handles Draft Issues.
     * Issue and PR items are synced from the issue/PR side and should not be removed here.
     *
     * @param projectId the project ID
     * @param syncedDraftIssueNodeIds list of Draft Issue node IDs that were synced
     * @param context processing context
     * @return number of Draft Issues removed
     */
    @Transactional
    public int removeStaleDraftIssues(Long projectId, List<String> syncedDraftIssueNodeIds, ProcessingContext context) {
        if (projectId == null) {
            return 0;
        }

        int removed;
        if (syncedDraftIssueNodeIds == null || syncedDraftIssueNodeIds.isEmpty()) {
            // No Draft Issues were synced - remove all Draft Issues for this project
            removed = projectItemRepository.deleteByProjectIdAndContentType(
                projectId,
                ProjectItem.ContentType.DRAFT_ISSUE
            );
        } else {
            // Remove only Draft Issues not in the synced list
            removed = projectItemRepository.deleteByProjectIdAndContentTypeAndNodeIdNotIn(
                projectId,
                ProjectItem.ContentType.DRAFT_ISSUE,
                syncedDraftIssueNodeIds
            );
        }

        if (removed > 0) {
            log.info("Removed stale Draft Issues: projectId={}, count={}", projectId, removed);
        }
        return removed;
    }

    /**
     * Removes stale Issue/PR items that no longer exist in the project.
     * <p>
     * This method is called after a full project-side sync that iterated through ALL items.
     * The project-side sync sees Issue/PR items (but doesn't process them) and tracks their
     * node IDs. Items not in the tracked list were removed from the project on GitHub.
     * <p>
     * <b>Archived items are preserved:</b> GitHub's {@code ProjectV2.items} connection excludes
     * archived items, so they would always appear "stale" even though they still exist on GitHub.
     * Archived items are synced from the issue/PR-side embedded {@code projectItems} connection
     * (which supports {@code includeArchived: true}) and via {@code projects_v2_item} webhooks.
     *
     * @param projectId the project ID
     * @param syncedIssuePrNodeIds list of Issue/PR node IDs seen during full project sync
     * @param context processing context
     * @return number of Issue/PR items removed
     */
    @Transactional
    public int removeStaleIssuePrItems(Long projectId, List<String> syncedIssuePrNodeIds, ProcessingContext context) {
        if (projectId == null) {
            return 0;
        }

        List<ProjectItem.ContentType> issuePrTypes = List.of(
            ProjectItem.ContentType.ISSUE,
            ProjectItem.ContentType.PULL_REQUEST
        );

        int removed;
        if (syncedIssuePrNodeIds == null || syncedIssuePrNodeIds.isEmpty()) {
            // No Issue/PR items were seen - remove all Issue/PR items for this project
            removed = projectItemRepository.deleteByProjectIdAndContentTypeIn(projectId, issuePrTypes);
        } else {
            // Remove only Issue/PR items not in the synced list
            removed = projectItemRepository.deleteByProjectIdAndContentTypeInAndNodeIdNotIn(
                projectId,
                issuePrTypes,
                syncedIssuePrNodeIds
            );
        }

        if (removed > 0) {
            log.info("Removed stale Issue/PR items: projectId={}, count={}", projectId, removed);
        }
        return removed;
    }

    /**
     * Relinks orphaned project items to their issues after issue sync completes.
     * <p>
     * Project items for ISSUE/PULL_REQUEST content may have NULL issue_id when the
     * referenced issue hasn't been synced locally yet. This uses the stored
     * content_database_id to fill in the FK once the issue exists.
     *
     * @return number of items relinked
     */
    @Transactional
    public int relinkOrphanedItems() {
        int relinked = projectItemRepository.relinkOrphanedItems();
        if (relinked > 0) {
            log.info("Relinked orphaned project items to issues: count={}", relinked);
        }
        return relinked;
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
     * Resolve the creator ID from the DTO, verifying the user exists locally.
     * <p>
     * The creator is the user who added the item to the project. We only link
     * to users that exist in our database to avoid FK constraint violations.
     *
     * @param dto the project item DTO containing creator information
     * @return the creator's user ID if they exist locally, null otherwise
     */
    private Long resolveCreatorId(GitHubProjectItemDTO dto) {
        if (dto.creator() == null || dto.creator().getDatabaseId() == null) {
            return null;
        }

        Long creatorId = dto.creator().getDatabaseId();
        if (userRepository.existsById(creatorId)) {
            return creatorId;
        }

        log.debug("Project item creator not synced locally: creatorId={}, itemNodeId={}", creatorId, dto.nodeId());
        return null;
    }
}
