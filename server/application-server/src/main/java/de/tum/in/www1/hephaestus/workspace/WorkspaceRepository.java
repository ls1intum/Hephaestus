package de.tum.in.www1.hephaestus.workspace;

import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    Optional<Workspace> findByInstallationId(Long installationId);
    Optional<Workspace> findByRepositoriesToMonitor_NameWithOwner(String nameWithOwner);
    Optional<Workspace> findByOrganization_Login(String login);
    Optional<Workspace> findByAccountLoginIgnoreCase(String login);
    Optional<Workspace> findBySlug(String slug);
    boolean existsBySlug(String slug);

    @NotNull
    List<Workspace> findAll();
}
