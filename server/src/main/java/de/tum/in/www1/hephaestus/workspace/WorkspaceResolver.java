package de.tum.in.www1.hephaestus.workspace;

import java.util.Optional;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a {@link Workspace} from repository identifiers using direct repository access.
 * <p>
 * Two-step resolution strategy:
 * <ol>
 *   <li><strong>Authoritative</strong>: looks up the explicit monitor configuration
 *       via {@link RepositoryToMonitorRepository}</li>
 *   <li><strong>Heuristic</strong>: infers the workspace from the repository owner login
 *       via {@link WorkspaceRepository}</li>
 * </ol>
 * <p>
 * <strong>Important:</strong> This service must depend ONLY on repositories,
 * not on other {@code @Service} beans, to avoid circular dependencies with
 * {@link WorkspaceService} and scheduling components.
 */
@Service
public class WorkspaceResolver {

    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final WorkspaceRepository workspaceRepository;

    public WorkspaceResolver(
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        WorkspaceRepository workspaceRepository
    ) {
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Resolves the workspace for a repository by monitor mapping or account login fallback.
     *
     * @param nameWithOwner the full repository name (e.g., "ls1intum/Hephaestus"), may be null
     * @return the workspace if found, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<Workspace> resolveForRepository(@Nullable String nameWithOwner) {
        if (nameWithOwner == null) {
            return Optional.empty();
        }

        // Step 1: Authoritative — explicit monitor configuration
        var monitor = repositoryToMonitorRepository.findByNameWithOwner(nameWithOwner);
        if (monitor.isPresent()) {
            return Optional.ofNullable(monitor.get().getWorkspace());
        }

        // Step 2: Heuristic — infer from repository owner login
        String owner = nameWithOwner.contains("/") ? nameWithOwner.substring(0, nameWithOwner.indexOf("/")) : null;
        if (owner != null && !owner.isEmpty()) {
            return workspaceRepository.findByAccountLoginIgnoreCase(owner);
        }

        return Optional.empty();
    }
}
