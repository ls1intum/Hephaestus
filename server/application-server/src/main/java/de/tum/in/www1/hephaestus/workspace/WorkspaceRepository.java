package de.tum.in.www1.hephaestus.workspace;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Workspace} entities.
 * Provides methods for finding workspaces by various identifiers.
 */
@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    Optional<Workspace> findByInstallationId(Long installationId);
    Optional<Workspace> findByRepositoriesToMonitor_NameWithOwner(String nameWithOwner);
    Optional<Workspace> findByOrganization_Login(String login);
    Optional<Workspace> findByAccountLoginIgnoreCase(String login);
    Optional<Workspace> findByWorkspaceSlug(String workspaceSlug);
    boolean existsByWorkspaceSlug(String workspaceSlug);

    @NotNull
    List<Workspace> findAll();

    List<Workspace> findByStatusNot(Workspace.WorkspaceStatus status);
}
