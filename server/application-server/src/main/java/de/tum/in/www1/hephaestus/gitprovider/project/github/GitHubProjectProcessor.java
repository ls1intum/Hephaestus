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
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub Projects V2.
 * <p>
 * This service handles the conversion of GitHubProjectDTO to Project entities,
 * persists them, and publishes appropriate domain events.
 */
@Service
public class GitHubProjectProcessor extends BaseGitHubProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubProjectProcessor.class);

    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubProjectProcessor(
        ProjectRepository projectRepository,
        UserRepository userRepository,
        LabelRepository labelRepository,
        MilestoneRepository milestoneRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        super(userRepository, labelRepository, milestoneRepository);
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

        // Publish domain events
        EventPayload.ProjectData projectData = EventPayload.ProjectData.from(project);
        EventContext eventContext = EventContext.from(context);

        if (isNew) {
            eventPublisher.publishEvent(new DomainEvent.ProjectCreated(projectData, eventContext));
            log.debug("Created project: projectId={}, projectNumber={}", project.getId(), project.getNumber());
        } else {
            eventPublisher.publishEvent(new DomainEvent.ProjectUpdated(projectData, Set.of(), eventContext));
            log.debug("Updated project: projectId={}, projectNumber={}", project.getId(), project.getNumber());
        }

        return project;
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
        Project project = process(dto, ownerType, ownerId, context);
        if (project != null) {
            EventPayload.ProjectData projectData = EventPayload.ProjectData.from(project);
            eventPublisher.publishEvent(new DomainEvent.ProjectClosed(projectData, EventContext.from(context)));
            log.info("Project closed: projectId={}, projectNumber={}", project.getId(), project.getNumber());
        }
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
        Project project = process(dto, ownerType, ownerId, context);
        if (project != null) {
            EventPayload.ProjectData projectData = EventPayload.ProjectData.from(project);
            eventPublisher.publishEvent(new DomainEvent.ProjectReopened(projectData, EventContext.from(context)));
            log.info("Project reopened: projectId={}, projectNumber={}", project.getId(), project.getNumber());
        }
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
