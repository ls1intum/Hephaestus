package de.tum.cit.aet.hephaestus.integration.outline.domain;

import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.MirrorState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.SyncStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for the mirrored-collection registry ({@link OutlineCollection}).
 *
 * <p>Every finder carries the {@code workspace_id} predicate the tenancy
 * {@code StatementInspector} requires. {@link #deleteByWorkspaceId(Long)} is the bulk-erase
 * used by workspace teardown; {@link #findDistinctWorkspaceIdsWithPendingSync()} is the
 * unscoped fan-out enumeration the catch-up tick runs (native, workspace-agnostic caller).
 */
public interface OutlineCollectionRepository extends JpaRepository<OutlineCollection, Long> {
    long deleteByWorkspaceId(Long workspaceId);

    List<OutlineCollection> findByWorkspaceIdOrderByCreatedAtAsc(Long workspaceId);

    Optional<OutlineCollection> findByWorkspaceIdAndConnectionIdAndCollectionId(
        Long workspaceId,
        Long connectionId,
        String collectionId
    );

    /**
     * The collections one sync pass walks: ENABLED rows for the workspace's install, stalest
     * first ({@code NULLS FIRST} puts never-synced collections at the front).
     */
    @Query(
        "SELECT c FROM OutlineCollection c WHERE c.workspaceId = :workspaceId AND c.connectionId = :connectionId " +
            "AND c.state = :state ORDER BY c.documentsSyncedAt ASC NULLS FIRST, c.id ASC"
    )
    List<OutlineCollection> findForSync(
        @Param("workspaceId") long workspaceId,
        @Param("connectionId") long connectionId,
        @Param("state") MirrorState state
    );

    /** ENABLED rows still awaiting their first clean pass — the catch-up tick's per-workspace worklist. */
    List<OutlineCollection> findByWorkspaceIdAndStateAndSyncStatus(
        Long workspaceId,
        MirrorState state,
        SyncStatus syncStatus
    );

    /**
     * Workspaces with at least one ENABLED collection still PENDING — the catch-up tick's fan-out.
     * Native + unscoped: runs inside a {@code @WorkspaceAgnostic} scheduler before any tenant is bound.
     */
    @Query(
        value = "SELECT DISTINCT workspace_id FROM outline_collection " +
            "WHERE state = 'ENABLED' AND sync_status = 'PENDING'",
        nativeQuery = true
    )
    List<Long> findDistinctWorkspaceIdsWithPendingSync();
}
