package de.tum.cit.aet.hephaestus.integration.outline.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for the mirrored {@link OutlineDocument} rows.
 *
 * <p>The mirror is the only place the agent read path resolves Outline content from; nothing here
 * reaches the Outline API. {@link #deleteByWorkspaceId(Long)} is the bulk-erase used by workspace
 * teardown so a purge drops this module's rows before the Connection is torn down. Every finder carries
 * the {@code workspace_id} predicate the tenancy {@code StatementInspector} requires.
 */
public interface OutlineDocumentRepository extends JpaRepository<OutlineDocument, Long> {
    long deleteByWorkspaceId(Long workspaceId);

    long countByWorkspaceId(Long workspaceId);

    /** The workspace's mirrored documents for one Outline install; the reconcile diffs against this set. */
    List<OutlineDocument> findByWorkspaceIdAndConnectionId(Long workspaceId, Long connectionId);

    /** One collection's mirrored documents — the scope of a collection-delete tombstone sweep. */
    List<OutlineDocument> findByWorkspaceIdAndConnectionIdAndCollectionId(
        Long workspaceId,
        Long connectionId,
        String collectionId
    );

    /** One mirrored document by its Outline id — the webhook targeted-refresh lookup. */
    Optional<OutlineDocument> findByWorkspaceIdAndConnectionIdAndDocumentId(
        Long workspaceId,
        Long connectionId,
        String documentId
    );

    /**
     * The agent-facing projection breadth: a bounded page of the workspace's mirrored documents, live rows first
     * (tombstoned last) and most-recently-updated first within each group. Carries the {@code workspace_id}
     * predicate the tenancy {@code StatementInspector} requires; the {@link Pageable} caps the result.
     */
    @Query(
        "SELECT d FROM OutlineDocument d WHERE d.workspaceId = :workspaceId " +
            "ORDER BY d.deletedAt ASC NULLS FIRST, d.outlineUpdatedAt DESC NULLS LAST, d.id ASC"
    )
    List<OutlineDocument> findForProjection(@Param("workspaceId") long workspaceId, Pageable pageable);

    /**
     * The workspace's mirrored documents matching a set of reference tokens (Outline document ids and/or slugs) —
     * the linked-document lookup for the review path. Matches on either the document id or the slug so a raw id or
     * a URL's trailing segment resolves. Carries the {@code workspace_id} predicate.
     */
    @Query(
        "SELECT d FROM OutlineDocument d WHERE d.workspaceId = :workspaceId " +
            "AND (d.documentId IN :refs OR d.slug IN :refs)"
    )
    List<OutlineDocument> findByWorkspaceIdAndReferenceIn(
        @Param("workspaceId") long workspaceId,
        @Param("refs") Collection<String> refs
    );

    /**
     * Staleness drop: delete every tombstoned row whose {@code deleted_at} is older than {@code cutoff} for
     * one workspace — the hard ceiling on how long a vanished document lingers as a marker. Derived DELETE
     * carrying the {@code workspace_id} predicate; idempotent (0 when nothing is stale).
     */
    long deleteByWorkspaceIdAndDeletedAtBefore(Long workspaceId, Instant cutoff);

    /**
     * Total size (in characters) of the workspace's mirrored Markdown bodies — the figure the size cap is
     * enforced against. Native for the {@code SUM(LENGTH(...))} aggregate; carries the {@code workspace_id}
     * predicate and coalesces an all-null/empty workspace to 0.
     */
    @Query(
        value = "SELECT COALESCE(SUM(LENGTH(body_markdown)), 0) FROM outline_document WHERE workspace_id = :workspaceId",
        nativeQuery = true
    )
    long sumBodySizeByWorkspaceId(@Param("workspaceId") long workspaceId);

    /**
     * Eviction candidates: {@code [id, body length]} for every row that still holds a body, ordered
     * least-recently-exported first (a never-exported row evicts before a recently-refreshed one). The
     * service walks this list, nulling bodies until the workspace is back under the cap. Native for the
     * {@code LENGTH(...)} projection; carries the {@code workspace_id} predicate.
     */
    @Query(
        value = "SELECT id, LENGTH(body_markdown) FROM outline_document " +
            "WHERE workspace_id = :workspaceId AND body_markdown IS NOT NULL " +
            "ORDER BY last_materialized_at ASC NULLS FIRST, id ASC",
        nativeQuery = true
    )
    List<Object[]> findEvictionCandidates(@Param("workspaceId") long workspaceId);

    /**
     * Frees mirrored bodies for the size cap: null {@code body_markdown} (and its hash, so the body
     * re-exports on next access) for the given rows. Native bulk UPDATE carrying the {@code workspace_id}
     * predicate. Returns rows affected.
     */
    @Modifying
    @Transactional
    @Query(
        value = "UPDATE outline_document SET body_markdown = NULL, content_hash = NULL " +
            "WHERE workspace_id = :workspaceId AND id IN (:ids)",
        nativeQuery = true
    )
    int evictBodies(@Param("workspaceId") long workspaceId, @Param("ids") List<Long> ids);
}
