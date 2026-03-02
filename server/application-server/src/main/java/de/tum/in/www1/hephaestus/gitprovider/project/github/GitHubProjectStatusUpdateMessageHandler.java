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
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectStatusUpdateDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectStatusUpdateEventDTO;
import java.time.Instant;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles projects_v2_status_update webhook events.
 * <p>
 * This handler processes status update create, edit, and delete events.
 * Status updates track project health with ON_TRACK, AT_RISK, OFF_TRACK statuses.
 * <p>
 * <h2>Supported Owner Types</h2>
 * <p>
 * Status updates belong to projects which can be owned by organizations, repositories, or users.
 * This handler supports all three owner types:
 * <ul>
 *   <li><b>ORGANIZATION:</b> Most common. Scope resolved via organization login.</li>
 *   <li><b>REPOSITORY:</b> Scope resolved via repository fullName (e.g., "owner/repo").</li>
 *   <li><b>USER:</b> Currently logged and skipped - user-level projects are not associated
 *       with a monitored workspace/scope.</li>
 * </ul>
 */
@Slf4j
@Component
public class GitHubProjectStatusUpdateMessageHandler extends GitHubMessageHandler<GitHubProjectStatusUpdateEventDTO> {

    private final ProjectRepository projectRepository;
    private final GitHubProjectStatusUpdateProcessor statusUpdateProcessor;
    private final ScopeIdResolver scopeIdResolver;
    private final GitProviderRepository gitProviderRepository;

    GitHubProjectStatusUpdateMessageHandler(
        ProjectRepository projectRepository,
        GitHubProjectStatusUpdateProcessor statusUpdateProcessor,
        ScopeIdResolver scopeIdResolver,
        GitProviderRepository gitProviderRepository,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubProjectStatusUpdateEventDTO.class, deserializer, transactionTemplate);
        this.projectRepository = projectRepository;
        this.statusUpdateProcessor = statusUpdateProcessor;
        this.scopeIdResolver = scopeIdResolver;
        this.gitProviderRepository = gitProviderRepository;
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

        // Detect owner type from the webhook payload
        Project.OwnerType ownerType = event.detectOwnerType();
        String ownerIdentifier = event.getOwnerIdentifier();

        log.info(
            "Received projects_v2_status_update event: action={}, nodeId={}, ownerType={}, owner={}",
            event.action(),
            payload.nodeId() != null ? sanitizeForLog(payload.nodeId()) : "unknown",
            ownerType,
            ownerIdentifier != null ? sanitizeForLog(ownerIdentifier) : "unknown"
        );

        // Resolve scope based on owner type
        Long scopeId = resolveScopeId(event, ownerType);
        if (scopeId == null) {
            log.debug(
                "Skipped projects_v2_status_update event: reason=noAssociatedScope, ownerType={}, owner={}",
                ownerType,
                sanitizeForLog(ownerIdentifier)
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

        GitProvider gitHubProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseThrow(() -> new IllegalStateException("GitHub provider not configured"));
        ProcessingContext context = ProcessingContext.forWebhook(scopeId, gitHubProvider, event.action());

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
            payload.bodyHtml(),
            startDate,
            targetDate,
            payload.status(),
            payload.creator(),
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
    private Long resolveScopeId(GitHubProjectStatusUpdateEventDTO event, Project.OwnerType ownerType) {
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
                    "User-owned project status update detected - not currently supported: sender={}",
                    event.sender() != null ? sanitizeForLog(event.sender().login()) : "unknown"
                );
                yield null;
            }
        };
    }
}
