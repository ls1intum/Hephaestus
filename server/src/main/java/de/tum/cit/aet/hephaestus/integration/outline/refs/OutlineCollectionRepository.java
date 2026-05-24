package de.tum.cit.aet.hephaestus.integration.outline.refs;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OutlineCollectionRepository extends JpaRepository<OutlineCollection, Long> {

    Optional<OutlineCollection> findByConnectionIdAndCollectionId(long connectionId, String collectionId);

    List<OutlineCollection> findByConnectionId(long connectionId);

    /** Workspace-scoped listing. */
    @Query("SELECT c FROM OutlineCollection c WHERE c.connection.workspace.id = :workspaceId")
    List<OutlineCollection> findByWorkspaceId(@Param("workspaceId") long workspaceId);

    /**
     * Cascade soft-delete invoked from the {@code collections.delete} lifecycle handler.
     * Tenant pin on {@code connection.workspace.id} prevents accidental cross-workspace
     * tombstoning if the (connectionId, collectionId) tuple is mismatched.
     */
    @Modifying
    @Transactional
    @Query(
        "UPDATE OutlineCollection c SET c.deletedAt = :at "
            + "WHERE c.connection.workspace.id = :workspaceId "
            + "AND c.connection.id = :connectionId "
            + "AND c.collectionId IN :collectionIds "
            + "AND c.deletedAt IS NULL"
    )
    int softDeleteByConnectionIdAndCollectionIdIn(
        @Param("workspaceId") long workspaceId,
        @Param("connectionId") long connectionId,
        @Param("collectionIds") List<String> collectionIds,
        @Param("at") Instant at
    );
}
