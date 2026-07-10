package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import java.util.Collection;
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

    Optional<Connection> findFirstByKindAndInstanceKeyAndState(
        IntegrationKind kind,
        String instanceKey,
        IntegrationState state
    );

    List<Connection> findAllByKindAndInstanceKeyInAndState(
        IntegrationKind kind,
        Collection<String> instanceKeys,
        IntegrationState state
    );

    /** Lookup for kinds where there is at most one row per (workspace, kind). */
    Optional<Connection> findFirstByWorkspaceIdAndKindAndStateOrderByCreatedAtDesc(
        long workspaceId,
        IntegrationKind kind,
        IntegrationState state
    );

    List<Connection> findByWorkspaceIdAndState(long workspaceId, IntegrationState state);

    /**
     * The Connection by primary key, scoped to its workspace (the predicate the tenancy inspector
     * requires) and deliberately state-agnostic: deactivation cleanup runs after the row left ACTIVE.
     */
    Optional<Connection> findByIdAndWorkspaceId(Long id, long workspaceId);

    @Query("SELECT c FROM Connection c WHERE c.workspace.id = :workspaceId")
    List<Connection> findByWorkspaceId(@Param("workspaceId") long workspaceId);

    /**
     * Ids of every workspace holding a Connection of the given kind in the given state — the cross-workspace
     * fan-out a periodic sync scheduler enumerates. The emitted SQL selects {@code workspace_id}, so it
     * satisfies the tenancy inspector without a bypass.
     */
    @Query("SELECT DISTINCT c.workspace.id FROM Connection c WHERE c.kind = :kind AND c.state = :state")
    List<Long> findWorkspaceIdsByKindAndState(
        @Param("kind") IntegrationKind kind,
        @Param("state") IntegrationState state
    );
}
