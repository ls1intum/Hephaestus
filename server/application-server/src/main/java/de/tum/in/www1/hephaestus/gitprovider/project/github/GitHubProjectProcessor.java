package de.tum.in.www1.hephaestus.gitprovider.project.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.github.BaseGitHubProcessor;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import java.time.Instant;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub Projects V2.
 * <p>
 * This service handles the conversion of GitHubProjectDTO to Project entities,
 * persists them, and publishes appropriate domain events.
 */
@Slf4j
@Service
public class GitHubProjectProcessor extends BaseGitHubProcessor {

    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubProjectProcessor(
        ProjectRepository projectRepository,
        UserRepository userRepository,
        LabelRepository labelRepository,
        MilestoneRepository milestoneRepository,
        GitHubUserProcessor gitHubUserProcessor,
        ApplicationEventPublisher eventPublisher
    ) {
        super(userRepository, labelRepository, milestoneRepository, gitHubUserProcessor);
        this.projectRepository = projectRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub project DTO and persist it as a Project entity.
     *
     * @param dto the GitHub project DTO
     * @param ownerType the type of owner (ORGANIZATION, USER, REPOSITORY)
     * @param ownerId the ID of the owner entity
     * @param context processing context with scope information
     * @return the persisted Project entity
     */
    @Transactional
    public Project process(GitHubProjectDTO dto, Project.OwnerType ownerType, Long ownerId, ProcessingContext context) {
        return process(dto, ownerType, ownerId, context, null);
    }

    /**
     * Process a GitHub project DTO and persist it as a Project entity.
     *
     * @param dto the GitHub project DTO
     * @param ownerType the type of owner (ORGANIZATION, USER, REPOSITORY)
     * @param ownerId the ID of the owner entity
     * @param context processing context with scope information
     * @param actorId the ID of the user who performed the action (from webhook sender), or null for sync
     * @return the persisted Project entity
     */
    @Transactional
    public Project process(
        GitHubProjectDTO dto,
        Project.OwnerType ownerType,
        Long ownerId,
        ProcessingContext context,
        Long actorId
    ) {
        UpsertResult result = upsertProject(dto, ownerType, ownerId);
        if (result == null) {
            return null;
        }

        // Publish domain events with actor information
        EventPayload.ProjectData projectData = EventPayload.ProjectData.from(result.project(), actorId);
        EventContext eventContext = EventContext.from(context);

        if (result.isNew()) {
            eventPublisher.publishEvent(new DomainEvent.ProjectCreated(projectData, eventContext));
            log.debug(
                "Created project: projectId={}, projectNumber={}",
                result.project().getId(),
                result.project().getNumber()
            );
        } else {
            eventPublisher.publishEvent(new DomainEvent.ProjectUpdated(projectData, Set.of(), eventContext));
            log.debug(
                "Updated project: projectId={}, projectNumber={}",
                result.project().getId(),
                result.project().getNumber()
            );
        }

        return result.project();
    }

    /**
     * Internal record to hold upsert result with new/existing flag.
     */
    private record UpsertResult(Project project, boolean isNew) {}

    /**
     * Internal method to upsert a project without publishing events.
     * Used by process(), processClosed(), and processReopened() to avoid double event publishing.
     *
     * @param dto the GitHub project DTO
     * @param ownerType the type of owner
     * @param ownerId the ID of the owner entity
     * @return UpsertResult containing the project and whether it was new, or null if processing was skipped
     */
    private UpsertResult upsertProject(GitHubProjectDTO dto, Project.OwnerType ownerType, Long ownerId) {
        if (dto == null) {
            log.warn("Skipped project processing: reason=nullDto, ownerId={}", ownerId);
            return null;
        }

        Long dbId = dto.getDatabaseId();
        if (dbId == null) {
            log.warn("Skipped project processing: reason=missingDatabaseId, projectNumber={}", dto.number());
            return null;
        }

        // Check if project already exists
        boolean isNew = !projectRepository.existsByOwnerTypeAndOwnerIdAndNumber(ownerType, ownerId, dto.number());

        // Find or create creator
        User creator = dto.creator() != null ? findOrCreateUser(dto.creator()) : null;

        // Perform atomic upsert
        projectRepository.upsertCore(
            dbId,
            dto.nodeId(),
            ownerType.name(),
            ownerId,
            dto.number(),
            sanitize(dto.title()),
            sanitize(dto.shortDescription()),
            sanitize(dto.readme()),
            dto.template(),
            dto.url(),
            dto.isClosed(),
            dto.closedAt(),
            dto.isPublic(),
            creator != null ? creator.getId() : null,
            Instant.now(),
            dto.createdAt(),
            dto.updatedAt()
        );

        // Fetch the entity to return a managed instance
        Project project = projectRepository
            .findByOwnerTypeAndOwnerIdAndNumber(ownerType, ownerId, dto.number())
            .orElseThrow(() ->
                new IllegalStateException(
                    "Project not found after upsert: ownerType=" +
                        ownerType +
                        ", ownerId=" +
                        ownerId +
                        ", number=" +
                        dto.number()
                )
            );

        return new UpsertResult(project, isNew);
    }

    /**
     * Process a project close event.
     *
     * @param dto the GitHub project DTO
     * @param ownerType the type of owner
     * @param ownerId the ID of the owner entity
     * @param context processing context
     * @return the updated Project entity
     */
    @Transactional
    public Project processClosed(
        GitHubProjectDTO dto,
        Project.OwnerType ownerType,
        Long ownerId,
        ProcessingContext context
    ) {
        return processClosed(dto, ownerType, ownerId, context, null);
    }

    /**
     * Process a project close event.
     *
     * @param dto the GitHub project DTO
     * @param ownerType the type of owner
     * @param ownerId the ID of the owner entity
     * @param context processing context
     * @param actorId the ID of the user who closed the project (from webhook sender), or null for sync
     * @return the updated Project entity
     */
    @Transactional
    public Project processClosed(
        GitHubProjectDTO dto,
        Project.OwnerType ownerType,
        Long ownerId,
        ProcessingContext context,
        Long actorId
    ) {
        UpsertResult result = upsertProject(dto, ownerType, ownerId);
        if (result == null) {
            return null;
        }

        Project project = result.project();
        EventPayload.ProjectData projectData = EventPayload.ProjectData.from(project, actorId);
        eventPublisher.publishEvent(new DomainEvent.ProjectClosed(projectData, EventContext.from(context)));
        log.info("Project closed: projectId={}, projectNumber={}", project.getId(), project.getNumber());
        return project;
    }

    /**
     * Process a project reopen event.
     *
     * @param dto the GitHub project DTO
     * @param ownerType the type of owner
     * @param ownerId the ID of the owner entity
     * @param context processing context
     * @return the updated Project entity
     */
    @Transactional
    public Project processReopened(
        GitHubProjectDTO dto,
        Project.OwnerType ownerType,
        Long ownerId,
        ProcessingContext context
    ) {
        return processReopened(dto, ownerType, ownerId, context, null);
    }

    /**
     * Process a project reopen event.
     *
     * @param dto the GitHub project DTO
     * @param ownerType the type of owner
     * @param ownerId the ID of the owner entity
     * @param context processing context
     * @param actorId the ID of the user who reopened the project (from webhook sender), or null for sync
     * @return the updated Project entity
     */
    @Transactional
    public Project processReopened(
        GitHubProjectDTO dto,
        Project.OwnerType ownerType,
        Long ownerId,
        ProcessingContext context,
        Long actorId
    ) {
        UpsertResult result = upsertProject(dto, ownerType, ownerId);
        if (result == null) {
            return null;
        }

        Project project = result.project();
        EventPayload.ProjectData projectData = EventPayload.ProjectData.from(project, actorId);
        eventPublisher.publishEvent(new DomainEvent.ProjectReopened(projectData, EventContext.from(context)));
        log.info("Project reopened: projectId={}, projectNumber={}", project.getId(), project.getNumber());
        return project;
    }

    /**
     * Delete a project by its ID.
     *
     * @param projectId the project database ID
     * @param context processing context
     */
    @Transactional
    public void delete(Long projectId, ProcessingContext context) {
        if (projectId == null) {
            return;
        }

        projectRepository
            .findById(projectId)
            .ifPresent(project -> {
                String title = project.getTitle();
                projectRepository.delete(project);
                eventPublisher.publishEvent(
                    new DomainEvent.ProjectDeleted(projectId, title, EventContext.from(context))
                );
                log.info("Deleted project: projectId={}, projectNumber={}", projectId, project.getNumber());
            });
    }
}
