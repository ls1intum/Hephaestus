package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only query service for workspace lookups.
 * <p>
 * This service provides query methods for finding and listing workspaces
 * without modifying state. All methods are read-only transactions.
 */
@Service
@Transactional(readOnly = true)
public class WorkspaceQueryService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final UserRepository userRepository;

    public WorkspaceQueryService(
        WorkspaceRepository workspaceRepository,
        WorkspaceMembershipRepository workspaceMembershipRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        UserRepository userRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.userRepository = userRepository;
    }

    /**
     * Find a workspace by account login (case-insensitive).
     *
     * @param accountLogin the account login to search for
     * @return the workspace if found
     */
    public Optional<Workspace> findByAccountLogin(String accountLogin) {
        return workspaceRepository.findByAccountLoginIgnoreCase(accountLogin);
    }

    /**
     * List all non-purged workspaces.
     *
     * @return list of workspaces that are not purged
     */
    public List<Workspace> findAll() {
        return workspaceRepository.findByStatusNot(Workspace.WorkspaceStatus.PURGED);
    }

    /**
     * Returns workspaces the current user can see: memberships + publicly viewable workspaces.
     * If no user is authenticated, only publicly viewable workspaces are returned.
     *
     * @return list of accessible workspaces for the current user
     */
    public List<Workspace> findAccessibleWorkspaces() {
        return findAccessibleWorkspaces(userRepository.getCurrentUser());
    }

    /**
     * Returns workspaces a specific user can see: memberships + publicly viewable workspaces.
     * If the user is empty, only publicly viewable workspaces are returned.
     *
     * @param currentUser the user to check accessibility for
     * @return list of accessible workspaces
     */
    List<Workspace> findAccessibleWorkspaces(Optional<User> currentUser) {
        // Always include public, non-purged workspaces
        List<Workspace> publicWorkspaces = workspaceRepository.findByStatusNotAndIsPubliclyViewableTrue(
            Workspace.WorkspaceStatus.PURGED
        );

        if (currentUser.isEmpty()) {
            return publicWorkspaces;
        }

        // Fetch memberships for the current user and load workspaces by ID
        var memberships = workspaceMembershipRepository.findByUser_Id(currentUser.get().getId());
        var workspaceIds = memberships.stream().map(WorkspaceMembership::getWorkspace).map(Workspace::getId).toList();

        List<Workspace> memberWorkspaces = workspaceIds.isEmpty()
            ? List.of()
            : workspaceRepository.findAllById(workspaceIds);

        // Merge and de-duplicate by ID to avoid duplicate entities with different instances
        return Stream.concat(publicWorkspaces.stream(), memberWorkspaces.stream())
            .collect(
                Collectors.toMap(Workspace::getId, w -> w, (existing, replacement) -> existing, LinkedHashMap::new)
            )
            .values()
            .stream()
            .toList();
    }

    /**
     * Find a workspace by GitHub App installation ID.
     *
     * @param installationId the GitHub App installation ID
     * @return the workspace if found
     */
    public Optional<Workspace> findByGitHubInstallationId(Long installationId) {
        return workspaceRepository.findByInstallationId(installationId);
    }

    /**
     * Find a workspace by its slug.
     *
     * @param slug the workspace slug
     * @return the workspace if found
     */
    public Optional<Workspace> findBySlug(String slug) {
        return workspaceRepository.findByWorkspaceSlug(slug);
    }

    /**
     * Resolve the workspace slug responsible for a given repository.
     * <p>
     * Priority:
     * <ol>
     *   <li>Explicit repository monitor (authoritative)</li>
     *   <li>Workspace account login matching repository owner (one-to-one enforced by business model)</li>
     * </ol>
     *
     * @param repository the repository to resolve the workspace slug for
     * @return the workspace slug if a unique mapping can be established, empty otherwise
     */
    public Optional<String> resolveWorkspaceSlug(Repository repository) {
        if (repository == null || isBlank(repository.getNameWithOwner())) {
            return Optional.empty();
        }

        var nameWithOwner = repository.getNameWithOwner();
        var monitor = repositoryToMonitorRepository.findByNameWithOwner(nameWithOwner);
        if (monitor.isPresent()) {
            Workspace workspace = monitor.get().getWorkspace();
            return workspace != null ? Optional.ofNullable(workspace.getWorkspaceSlug()) : Optional.empty();
        }

        // Fallback: org owner lookup (accountLogin is unique)
        String owner = nameWithOwner.contains("/") ? nameWithOwner.substring(0, nameWithOwner.indexOf("/")) : null;
        if (owner != null) {
            return workspaceRepository.findByAccountLoginIgnoreCase(owner).map(Workspace::getWorkspaceSlug);
        }

        return Optional.empty();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
