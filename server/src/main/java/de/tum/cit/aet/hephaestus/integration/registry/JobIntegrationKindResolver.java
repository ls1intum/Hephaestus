package de.tum.cit.aet.hephaestus.integration.registry;

import de.tum.cit.aet.hephaestus.gitprovider.common.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.NoSuchElementException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link IntegrationKind} for a piece of work given its current
 * vendor binding plus a workspace fallback. New work (post-#1198) carries the kind
 * directly; legacy paths fall back to mapping {@code workspace.git_provider_mode}
 * into {@link IntegrationKind}.
 *
 * <p>Lives under {@code integration/registry/} (not {@code agent/}) so the legacy
 * {@link GitProviderType} branch stays out of the agent module — per #1198 AC#8
 * which forbids agent-side switching on the legacy enum. The fallback is purely
 * an integration-framework concern and disappears once the connection-cutover
 * changeset drops the legacy column.
 *
 * <p>Accepts primitive {@code workspaceId} (not the AgentJob entity) to avoid a
 * Modulith violation ({@code integration → agent}).
 */
@Component
public class JobIntegrationKindResolver {

    private final WorkspaceRepository workspaceRepository;

    public JobIntegrationKindResolver(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * @param directKind the kind already recorded on the work row, or {@code null}
     *     to force the workspace fallback (used by legacy rows pre-backfill).
     * @param workspaceId workspace id to consult when {@code directKind} is null.
     * @return the {@link IntegrationKind} for this work item.
     * @throws NoSuchElementException if the workspace doesn't exist
     */
    public IntegrationKind resolve(@Nullable IntegrationKind directKind, long workspaceId) {
        if (directKind != null) {
            return directKind;
        }
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new NoSuchElementException("Workspace not found: id=" + workspaceId));
        GitProviderType providerType = workspace.getProviderType();
        return switch (providerType) {
            case GITHUB -> IntegrationKind.GITHUB;
            case GITLAB -> IntegrationKind.GITLAB;
        };
    }
}
