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
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectStatusUpdateDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectStatusUpdateEventDTO;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles projects_v2_status_update webhook events.
 * <p>
 * This handler processes status update create, edit, and delete events.
 * Status updates track project health with ON_TRACK, AT_RISK, OFF_TRACK statuses.
 */
@Component
public class GitHubProjectStatusUpdateMessageHandler extends GitHubMessageHandler<GitHubProjectStatusUpdateEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubProjectStatusUpdateMessageHandler.class);

    private final ProjectRepository projectRepository;
    private final GitHubProjectStatusUpdateProcessor statusUpdateProcessor;
    private final ScopeIdResolver scopeIdResolver;

    GitHubProjectStatusUpdateMessageHandler(
        ProjectRepository projectRepository,
        GitHubProjectStatusUpdateProcessor statusUpdateProcessor,
        ScopeIdResolver scopeIdResolver,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubProjectStatusUpdateEventDTO.class, deserializer, transactionTemplate);
        this.projectRepository = projectRepository;
        this.statusUpdateProcessor = statusUpdateProcessor;
        this.scopeIdResolver = scopeIdResolver;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.PROJECTS_V2_STATUS_UPDATE;
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.ORGANIZATION;
    }

    @Override
    protected void handleEvent(GitHubProjectStatusUpdateEventDTO event) {
        GitHubEventAction.ProjectV2StatusUpdate action = GitHubEventAction.ProjectV2StatusUpdate.fromString(
            event.action()
        );

        var payload = event.statusUpdate();
        if (payload == null) {
            log.warn("Skipped projects_v2_status_update event: reason=nullPayload, action={}", event.action());
            return;
        }

        String projectNodeId = payload.projectNodeId();
        if (projectNodeId == null) {
            log.warn("Skipped projects_v2_status_update event: reason=missingProjectNodeId, action={}", event.action());
            return;
        }

        String orgLogin = event.organization() != null ? event.organization().login() : null;

        log.info(
            "Received projects_v2_status_update event: action={}, nodeId={}, orgLogin={}",
            event.action(),
            payload.nodeId() != null ? sanitizeForLog(payload.nodeId()) : "unknown",
            orgLogin != null ? sanitizeForLog(orgLogin) : "unknown"
        );

        // Resolve scope from organization login
        Long scopeId = orgLogin != null ? scopeIdResolver.findScopeIdByOrgLogin(orgLogin).orElse(null) : null;
        if (scopeId == null) {
            log.debug(
                "Skipped projects_v2_status_update event: reason=noAssociatedScope, orgLogin={}",
                sanitizeForLog(orgLogin)
            );
            return;
        }

        Project project = projectRepository.findByNodeId(projectNodeId).orElse(null);
        if (project == null) {
            log.warn(
                "Skipped projects_v2_status_update event: reason=projectNotFound, projectNodeId={}, action={}",
                sanitizeForLog(projectNodeId),
                event.action()
            );
            return;
        }

        ProcessingContext context = ProcessingContext.forWebhook(scopeId, null, event.action());

        switch (action) {
            case CREATED, EDITED -> {
                GitHubProjectStatusUpdateDTO dto = convertToDto(payload);
                statusUpdateProcessor.process(dto, project, context);
            }
            case DELETED -> statusUpdateProcessor.delete(payload.nodeId(), project.getId(), context);
            case UNKNOWN -> log.debug("Ignored unknown projects_v2_status_update action: {}", event.action());
        }
    }

    /**
     * Converts the webhook payload to a GitHubProjectStatusUpdateDTO.
     * <p>
     * The webhook payload uses different field names and formats than the DTO,
     * so we need to transform the data appropriately.
     */
    private GitHubProjectStatusUpdateDTO convertToDto(GitHubProjectStatusUpdateEventDTO.StatusUpdatePayload payload) {
        LocalDate startDate = parseDate(payload.startDate());
        LocalDate targetDate = parseDate(payload.targetDate());
        Instant createdAt = parseInstant(payload.createdAt());
        Instant updatedAt = parseInstant(payload.updatedAt());

        return new GitHubProjectStatusUpdateDTO(
            payload.id(),
            payload.id(), // databaseId same as id for webhooks
            payload.nodeId(),
            payload.body(),
            startDate,
            targetDate,
            payload.status(),
            null, // creator not provided in webhook, only creatorId
            createdAt,
            updatedAt
        );
    }

    /**
     * Parses a date string in ISO format (YYYY-MM-DD).
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.debug("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    /**
     * Parses an instant string in ISO format.
     */
    private Instant parseInstant(String instantStr) {
        if (instantStr == null || instantStr.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(instantStr);
        } catch (Exception e) {
            log.debug("Failed to parse instant: {}", instantStr);
            return null;
        }
    }
}
