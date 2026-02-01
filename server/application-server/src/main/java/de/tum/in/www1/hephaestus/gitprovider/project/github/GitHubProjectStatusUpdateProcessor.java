package de.tum.in.www1.hephaestus.gitprovider.project.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectStatusUpdate;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectStatusUpdateRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectStatusUpdateDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub Projects V2 status updates.
 */
@Service
public class GitHubProjectStatusUpdateProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubProjectStatusUpdateProcessor.class);

    private final ProjectStatusUpdateRepository statusUpdateRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubProjectStatusUpdateProcessor(
        ProjectStatusUpdateRepository statusUpdateRepository,
        UserRepository userRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.statusUpdateRepository = statusUpdateRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProjectStatusUpdate process(GitHubProjectStatusUpdateDTO dto, Project project, ProcessingContext context) {
        if (dto == null) {
            log.warn(
                "Skipped status update processing: reason=nullDto, projectId={}",
                project != null ? project.getId() : null
            );
            return null;
        }

        if (dto.nodeId() == null || dto.nodeId().isBlank()) {
            log.warn(
                "Skipped status update processing: reason=missingNodeId, projectId={}",
                project != null ? project.getId() : null
            );
            return null;
        }

        Long dbId = dto.getDatabaseId();
        if (dbId == null) {
            log.warn("Skipped status update processing: reason=missingDatabaseId, nodeId={}", dto.nodeId());
            return null;
        }

        boolean isNew = !statusUpdateRepository.existsByNodeId(dto.nodeId());

        // Resolve creator
        Long creatorId = null;
        if (dto.creator() != null && dto.creator().getDatabaseId() != null) {
            if (userRepository.existsById(dto.creator().getDatabaseId())) {
                creatorId = dto.creator().getDatabaseId();
            }
        }

        ProjectStatusUpdate.Status status = dto.getStatusEnum();

        // Atomic upsert
        statusUpdateRepository.upsertCore(
            dbId,
            dto.nodeId(),
            project.getId(),
            sanitize(dto.body()),
            dto.startDate(),
            dto.targetDate(),
            status != null ? status.name() : null,
            creatorId,
            dto.createdAt() != null ? dto.createdAt() : Instant.now(),
            dto.updatedAt() != null ? dto.updatedAt() : Instant.now()
        );

        // Fetch the entity
        ProjectStatusUpdate statusUpdate = statusUpdateRepository
            .findByNodeId(dto.nodeId())
            .orElseThrow(() ->
                new IllegalStateException("ProjectStatusUpdate not found after upsert: nodeId=" + dto.nodeId())
            );

        // Publish events
        EventContext eventContext = EventContext.from(context);
        EventPayload.ProjectStatusUpdateData data = EventPayload.ProjectStatusUpdateData.from(statusUpdate);

        if (isNew) {
            eventPublisher.publishEvent(
                new DomainEvent.ProjectStatusUpdateCreated(data, project.getId(), eventContext)
            );
            log.debug(
                "Created project status update: id={}, nodeId={}",
                statusUpdate.getId(),
                statusUpdate.getNodeId()
            );
        } else {
            eventPublisher.publishEvent(
                new DomainEvent.ProjectStatusUpdateUpdated(data, project.getId(), eventContext)
            );
            log.debug(
                "Updated project status update: id={}, nodeId={}",
                statusUpdate.getId(),
                statusUpdate.getNodeId()
            );
        }

        return statusUpdate;
    }

    @Transactional
    public void delete(String nodeId, Long projectId, ProcessingContext context) {
        if (nodeId == null) {
            return;
        }

        statusUpdateRepository
            .findByNodeId(nodeId)
            .ifPresent(statusUpdate -> {
                Long id = statusUpdate.getId();
                statusUpdateRepository.delete(statusUpdate);
                eventPublisher.publishEvent(
                    new DomainEvent.ProjectStatusUpdateDeleted(id, projectId, EventContext.from(context))
                );
                log.info("Deleted project status update: id={}, nodeId={}", id, nodeId);
            });
    }

    @Transactional
    public int removeStaleStatusUpdates(Long projectId, List<String> syncedNodeIds, ProcessingContext context) {
        if (projectId == null || syncedNodeIds == null || syncedNodeIds.isEmpty()) {
            return 0;
        }

        int removed = statusUpdateRepository.deleteByProjectIdAndNodeIdNotIn(projectId, syncedNodeIds);
        if (removed > 0) {
            log.info("Removed stale status updates: projectId={}, count={}", projectId, removed);
        }
        return removed;
    }

    private String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("\u0000", "");
    }
}
