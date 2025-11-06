package de.tum.in.www1.hephaestus.workspace;

import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    Optional<Workspace> findByInstallationId(Long installationId);

    @Query(
        """
        SELECT DISTINCT w
        FROM Workspace w
        JOIN w.repositoriesToMonitor rtm
        WHERE LOWER(rtm.nameWithOwner) = LOWER(:nameWithOwner)
            AND rtm.active = true
        """
    )
    Optional<Workspace> findActiveByRepositoryNameWithOwner(@Param("nameWithOwner") String nameWithOwner);

    Optional<Workspace> findByOrganization_Login(String login);
    Optional<Workspace> findByAccountLoginIgnoreCase(String login);

    @NotNull
    List<Workspace> findAll();
}
