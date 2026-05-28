package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
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

    /**
     * All Connections of a given {@code kind} in {@code state}, across workspaces. Used by
     * the weekly Slack leaderboard task to fan out across every workspace that has an
     * ACTIVE Slack Connection — replaces the old workspace-slug allow-list. Joined fetch
     * on {@code workspace} avoids the N+1 the task would otherwise trigger.
     *
     * <p>Intentionally cross-tenant: a scheduled task running on the server role iterates
     * every workspace that has opted in to Slack notifications, then re-scopes per row.
     */
    @WorkspaceAgnostic("Scheduled cross-workspace fan-out for weekly leaderboard Slack post")
    @Query("SELECT c FROM Connection c JOIN FETCH c.workspace WHERE c.kind = :kind AND c.state = :state")
    List<Connection> findByKindAndStateWithWorkspace(
        @Param("kind") IntegrationKind kind,
        @Param("state") IntegrationState state
    );

    /**
     * All ACTIVE Connections in a given {@code family}, across workspaces. Used by the
     * core.auth composite ClientRegistrationRepository to materialize workspace-scoped
     * OIDC login providers (family IDENTITY) into Spring ClientRegistrations. Joined fetch
     * on workspace avoids N+1 when building the registration ids.
     *
     * <p>Intentionally cross-tenant: the login picker enumerates providers across the
     * workspace the user is signing in to; per-workspace scoping happens one layer up via
     * the registration-id naming ({@code gl-ws-{connectionId}}).
     */
    @WorkspaceAgnostic("core.auth materializes IDENTITY-family Connections into login ClientRegistrations")
    @Query(
        "SELECT c FROM Connection c JOIN FETCH c.workspace WHERE c.kind IN :kinds AND c.state = :state"
    )
    List<Connection> findByKindInAndStateWithWorkspace(
        @Param("kinds") List<IntegrationKind> kinds,
        @Param("state") IntegrationState state
    );
}
