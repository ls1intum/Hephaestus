package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.workspace.dto.UpdateWorkspaceFeaturesRequestDTO;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for workspace configuration and settings management.
 *
 * <p>Handles:
 * <ul>
 *   <li>Review cycle schedule configuration</li>
 *   <li>Token/credential management</li>
 *   <li>Visibility settings</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WorkspaceSettingsService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSettingsService.class);

    private final WorkspaceRepository workspaceRepository;
    private final ConnectionService connectionService;

    /**
     * Update the weekly practice review cycle schedule for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param day the day of week (1=Monday, 7=Sunday)
     * @param time the time in HH:mm format
     * @return the updated workspace
     */
    @Transactional
    public Workspace updateReviewCycle(Long workspaceId, Integer day, String time) {
        Workspace workspace = requireWorkspace(workspaceId);

        if (day != null) {
            if (day < 1 || day > 7) {
                throw new IllegalArgumentException("Day must be between 1 (Monday) and 7 (Sunday)");
            }
            workspace.setReviewCycleDay(day);
        }

        if (time != null) {
            validateTimeFormat(time);
            workspace.setReviewCycleTime(time);
        }

        Workspace saved = workspaceRepository.save(workspace);
        log.info("Updated workspace review cycle: workspaceId={}, day={}, time={}", workspaceId, day, time);
        return saved;
    }

    /**
     * Update the personal access token for a workspace. Rotates the bearer credential on
     * whichever SCM Connection (GitHub PAT or GitLab) is currently active — the caller
     * controls which workspace this hits via {@code workspaceId}, the kind is resolved
     * from the active Connection.
     */
    @Transactional
    public Workspace updateToken(Long workspaceId, String token) {
        Workspace workspace = requireWorkspace(workspaceId);
        IntegrationKind kind = connectionService
            .findActiveProviderKind(workspaceId)
            .filter(k -> k == IntegrationKind.GITHUB || k == IntegrationKind.GITLAB)
            .orElseThrow(() ->
                new IllegalStateException(
                    "Cannot rotate PAT for workspace " +
                        workspaceId +
                        ": no active GitHub or GitLab Connection. Bind a provider first."
                )
            );
        connectionService.rotateBearerToken(workspaceId, kind, new BearerToken(token, null));
        log.info("Updated workspace PAT: workspaceId={}, kind={}", workspaceId, kind);
        return workspace;
    }

    /**
     * Update public visibility for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param isPubliclyViewable whether the workspace is publicly viewable
     * @return the updated workspace
     */
    @Transactional
    public Workspace updatePublicVisibility(Long workspaceId, Boolean isPubliclyViewable) {
        Workspace workspace = requireWorkspace(workspaceId);
        workspace.setIsPubliclyViewable(isPubliclyViewable);
        log.info("Updated workspace visibility: workspaceId={}, isPublic={}", workspaceId, isPubliclyViewable);
        return workspaceRepository.save(workspace);
    }

    /**
     * Update workspace feature flags.
     * Null fields in the request DTO are ignored (PATCH semantics).
     *
     * @param workspaceId the workspace ID
     * @param request the feature flags to update (null fields are left unchanged)
     * @return the updated workspace
     */
    @Transactional
    public Workspace updateFeatures(Long workspaceId, UpdateWorkspaceFeaturesRequestDTO request) {
        Workspace workspace = requireWorkspace(workspaceId);
        workspace.getFeatures().applyPatch(request);

        log.info(
            "Updated workspace features: workspaceId={}, practices={}, achievements={}",
            workspaceId,
            request.practicesEnabled(),
            request.achievementsEnabled()
        );
        return workspaceRepository.save(workspace);
    }

    private Workspace requireWorkspace(Long workspaceId) {
        return workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));
    }

    private void validateTimeFormat(String time) {
        try {
            // Intentional: parsing validates the format; the parsed value itself is discarded.
            LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time format. Expected HH:mm", e);
        }
    }
}
