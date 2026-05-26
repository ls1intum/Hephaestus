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
public interface OutlineDocumentRepository extends JpaRepository<OutlineDocument, Long> {
    Optional<OutlineDocument> findByConnectionIdAndDocumentId(long connectionId, String documentId);

    List<OutlineDocument> findByConnectionIdAndCollectionId(long connectionId, String collectionId);

    /** Workspace-scoped listing for mentor context. */
    @Query("SELECT d FROM OutlineDocument d WHERE d.connection.workspace.id = :workspaceId")
    List<OutlineDocument> findByWorkspaceId(@Param("workspaceId") long workspaceId);

    /**
     * Soft-delete on {@code documents.delete}. Workspace pin in the JPQL prevents a
     * mismatched (connectionId, documentId) tuple from tombstoning the wrong tenant's row.
     */
    @Modifying
    @Transactional
    @Query(
        "UPDATE OutlineDocument d SET d.deletedAt = :at " +
            "WHERE d.connection.workspace.id = :workspaceId " +
            "AND d.connection.id = :connectionId AND d.documentId = :documentId " +
            "AND d.deletedAt IS NULL"
    )
    int softDelete(
        @Param("workspaceId") long workspaceId,
        @Param("connectionId") long connectionId,
        @Param("documentId") String documentId,
        @Param("at") Instant at
    );

    /**
     * Cascade soft-delete on {@code collections.delete}. Same tenant pin as
     * {@link #softDelete}.
     */
    @Modifying
    @Transactional
    @Query(
        "UPDATE OutlineDocument d SET d.deletedAt = :at " +
            "WHERE d.connection.workspace.id = :workspaceId " +
            "AND d.connection.id = :connectionId AND d.collectionId = :collectionId " +
            "AND d.deletedAt IS NULL"
    )
    int softDeleteByCollection(
        @Param("workspaceId") long workspaceId,
        @Param("connectionId") long connectionId,
        @Param("collectionId") String collectionId,
        @Param("at") Instant at
    );
}
