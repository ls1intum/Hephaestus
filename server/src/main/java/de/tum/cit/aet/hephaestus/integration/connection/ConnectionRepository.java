package de.tum.cit.aet.hephaestus.integration.connection;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectionRepository extends JpaRepository<Connection, Long> {
    Optional<Connection> findByWorkspaceIdAndKindAndInstanceKey(
        long workspaceId,
        IntegrationKind kind,
        String instanceKey
    );

    /** Lookup for kinds where there is at most one row per (workspace, kind). */
    Optional<Connection> findFirstByWorkspaceIdAndKindAndStateOrderByCreatedAtDesc(
        long workspaceId,
        IntegrationKind kind,
        IntegrationState state
    );

    List<Connection> findByWorkspaceIdAndState(long workspaceId, IntegrationState state);

    @Query("SELECT c FROM Connection c WHERE c.workspace.id = :workspaceId")
    List<Connection> findByWorkspaceId(@Param("workspaceId") long workspaceId);

    List<Connection> findByKindAndInstanceKey(IntegrationKind kind, String instanceKey);
}
