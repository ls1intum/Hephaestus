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

    /** Live (non-tombstoned) mirrored documents in the workspace — the admin status figure. */
    long countByWorkspaceIdAndDeletedAtIsNull(Long workspaceId);

    /** One collection's live (non-tombstoned) document count — the single-row admin DTO figure. */
    long countByWorkspaceIdAndConnectionIdAndCollectionIdAndDeletedAtIsNull(
        Long workspaceId,
        Long connectionId,
        String collectionId
    );

    /**
     * Live (non-tombstoned) document counts grouped by collection — one query for the whole admin
     * collection list instead of a count per row. Each element is {@code [collectionId, count]}.
     */
    @Query(
        "SELECT d.collectionId, COUNT(d) FROM OutlineDocument d WHERE d.workspaceId = :workspaceId " +
            "AND d.connectionId = :connectionId AND d.deletedAt IS NULL GROUP BY d.collectionId"
    )
    List<Object[]> countLiveByCollection(
        @Param("workspaceId") long workspaceId,
        @Param("connectionId") long connectionId
    );

    /**
     * Hard-deletes one collection's mirrored rows — the erase behind removing a collection from the
     * mirror; the bodies leave the database, they are not tombstoned.
     */
    long deleteByWorkspaceIdAndConnectionIdAndCollectionId(Long workspaceId, Long connectionId, String collectionId);

    /**
     * The workspace's mirrored documents for one Outline install, as full entities — <strong>bodies
     * included</strong>. Not for the reconcile: a workspace mirror is capped at hundreds of megabytes of
     * Markdown, and this materializes all of it. The sync path diffs against
     * {@link #findSnapshotsByWorkspaceIdAndConnectionId} instead and re-reads only the rows it writes.
     */
    List<OutlineDocument> findByWorkspaceIdAndConnectionId(Long workspaceId, Long connectionId);

    /** One mirrored document by its Outline id — the row a write transaction re-reads before mutating it. */
    Optional<OutlineDocument> findByWorkspaceIdAndConnectionIdAndDocumentId(
        Long workspaceId,
        Long connectionId,
        String documentId
    );

    /**
     * The body-free diff set the reconcile runs against: one {@link OutlineDocumentSnapshot} per mirrored
     * row of this install. {@code LENGTH(body_markdown)} is evaluated in Postgres, so no Markdown body ever
     * crosses the wire or lands on the heap — see {@link OutlineDocumentSnapshot} for why that matters.
     */
    @Query(
        "SELECT new de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentSnapshot(" +
            "d.id, d.documentId, d.collectionId, d.outlineUpdatedAt, d.contentHash, d.deletedAt, " +
            "LENGTH(d.bodyMarkdown), d.version) FROM OutlineDocument d " +
            "WHERE d.workspaceId = :workspaceId AND d.connectionId = :connectionId"
    )
    List<OutlineDocumentSnapshot> findSnapshotsByWorkspaceIdAndConnectionId(
        @Param("workspaceId") long workspaceId,
        @Param("connectionId") long connectionId
    );

    /** One collection's mirrored rows, body-free — the scope of a collection-delete tombstone sweep. */
    @Query(
        "SELECT new de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentSnapshot(" +
            "d.id, d.documentId, d.collectionId, d.outlineUpdatedAt, d.contentHash, d.deletedAt, " +
            "LENGTH(d.bodyMarkdown), d.version) FROM OutlineDocument d " +
            "WHERE d.workspaceId = :workspaceId AND d.connectionId = :connectionId " +
            "AND d.collectionId = :collectionId"
    )
    List<OutlineDocumentSnapshot> findSnapshotsByCollectionId(
        @Param("workspaceId") long workspaceId,
        @Param("connectionId") long connectionId,
        @Param("collectionId") String collectionId
    );

    /** One mirrored document's snapshot — the webhook targeted-refresh routing lookup (no body loaded). */
    @Query(
        "SELECT new de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentSnapshot(" +
            "d.id, d.documentId, d.collectionId, d.outlineUpdatedAt, d.contentHash, d.deletedAt, " +
            "LENGTH(d.bodyMarkdown), d.version) FROM OutlineDocument d " +
            "WHERE d.workspaceId = :workspaceId AND d.connectionId = :connectionId " +
            "AND d.documentId = :documentId"
    )
    Optional<OutlineDocumentSnapshot> findSnapshotByDocumentId(
        @Param("workspaceId") long workspaceId,
        @Param("connectionId") long connectionId,
        @Param("documentId") String documentId
    );

    /**
     * The agent-facing projection breadth: a bounded page of the workspace's mirrored documents, live rows first
     * (tombstoned last) and most-recently-updated first within each group; the {@link Pageable} caps the result.
     */
    @Query(
        "SELECT d FROM OutlineDocument d WHERE d.workspaceId = :workspaceId " +
            "ORDER BY d.deletedAt ASC NULLS FIRST, d.outlineUpdatedAt DESC NULLS LAST, d.id ASC"
    )
    List<OutlineDocument> findForProjection(@Param("workspaceId") long workspaceId, Pageable pageable);

    /**
     * The workspace's mirrored documents matching a set of reference tokens (Outline document ids and/or slugs) —
     * the linked-document lookup for the review path. Matches on either the document id or the slug so a raw id or
     * a URL's trailing segment resolves.
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
     * The character bound the searchable tsvector is built over. Postgres hard-refuses a tsvector larger
     * than 1 MB ({@code string is too long for tsvector}), and {@code body_markdown} is unbounded — without
     * this truncation ONE oversized mirrored document makes the query <em>ERROR</em>, permanently breaking
     * Outline retrieval for the whole workspace rather than just ranking that document poorly. 900 000
     * characters leaves headroom under the 1 MB (1 048 576 byte) ceiling for multi-byte content and the
     * title prefix.
     *
     * <p>This constant is documentation only: the value is inlined in {@link #searchByRelevance}'s SQL,
     * which must stay byte-for-byte equivalent to the {@code ix_outline_document_fts} GIN index expression
     * in the Liquibase changelog — a mismatch silently drops the index and reintroduces the per-row
     * tsvector rebuild.
     */
    int FTS_BODY_CHAR_LIMIT = 900_000;

    /**
     * Live documents ranked by full-text relevance to {@code query} (websearch syntax) — the retrieval path
     * behind {@code OutlineDocumentSelector}. The {@code simple} FTS config keeps matching language-neutral
     * (no stemming, no stopword list) for mixed-language wikis. Tombstoned and body-evicted rows are excluded:
     * there is no body to rank against.
     *
     * <p>The {@code to_tsvector(...)} expression is repeated verbatim in the WHERE clause, in {@code ts_rank},
     * and in the {@code ix_outline_document_fts} GIN index — all three must match exactly or Postgres falls
     * back to rebuilding a tsvector per row per call. See {@link #FTS_BODY_CHAR_LIMIT} for why the
     * {@code left(...)} bound is a correctness guard and not an optimisation.
     */
    @Query(
        value = "SELECT * FROM outline_document WHERE workspace_id = :workspaceId " +
            "AND deleted_at IS NULL AND body_markdown IS NOT NULL " +
            "AND to_tsvector('simple', coalesce(title, '') || ' ' || left(coalesce(body_markdown, ''), 900000)) " +
            "@@ websearch_to_tsquery('simple', :query) " +
            "ORDER BY ts_rank(to_tsvector('simple', coalesce(title, '') || ' ' || " +
            "left(coalesce(body_markdown, ''), 900000)), " +
            "websearch_to_tsquery('simple', :query)) DESC, outline_updated_at DESC NULLS LAST, id ASC " +
            "LIMIT :limit",
        nativeQuery = true
    )
    List<OutlineDocument> searchByRelevance(
        @Param("workspaceId") long workspaceId,
        @Param("query") String query,
        @Param("limit") int limit
    );

    /**
     * Staleness drop: delete every tombstoned row whose {@code deleted_at} is older than {@code cutoff} for
     * one workspace — the hard ceiling on how long a vanished document lingers as a marker. Idempotent.
     */
    long deleteByWorkspaceIdAndDeletedAtBefore(Long workspaceId, Instant cutoff);

    /**
     * Total size (in characters) of the workspace's mirrored Markdown bodies — the figure the size cap is
     * enforced against. Native for the {@code SUM(LENGTH(...))} aggregate; coalesces an all-null/empty
     * workspace to 0.
     */
    @Query(
        value = "SELECT COALESCE(SUM(LENGTH(body_markdown)), 0) FROM outline_document WHERE workspace_id = :workspaceId",
        nativeQuery = true
    )
    long sumBodySizeByWorkspaceId(@Param("workspaceId") long workspaceId);

    /**
     * Eviction candidates: {@code [id, body length]} for the {@code limit} rows that still hold a body and
     * were exported longest ago (a never-exported row evicts before a recently-refreshed one). The service
     * walks this page, nulling bodies until the workspace is back under the cap, and re-queries for the next
     * page if it is not — an evicted row drops out of this result set, so the pages advance on their own.
     *
     * <p>The {@code LIMIT} is load-bearing: {@link #evictBodies} binds one parameter per id, and Postgres
     * caps a statement at 65 535 bind parameters (with the planner degrading long before that). An unbounded
     * candidate list on a large over-cap mirror produced exactly that statement. Native for the
     * {@code LENGTH(...)} projection.
     */
    @Query(
        value = "SELECT id, LENGTH(body_markdown) FROM outline_document " +
            "WHERE workspace_id = :workspaceId AND body_markdown IS NOT NULL " +
            "ORDER BY last_materialized_at ASC NULLS FIRST, id ASC LIMIT :limit",
        nativeQuery = true
    )
    List<Object[]> findEvictionCandidates(@Param("workspaceId") long workspaceId, @Param("limit") int limit);

    /**
     * Frees mirrored bodies for the size cap: null {@code body_markdown} and stamp
     * {@code body_evicted_at} for the given rows. {@code content_hash} is deliberately KEPT so the
     * unchanged-check still holds — an evicted-but-unmodified document is not re-exported (and
     * re-evicted) on every pass; the body comes back only when upstream changes or a targeted refresh
     * asks for it. Returns rows affected.
     *
     * <p>Callers must chunk {@code ids} (the sync service does, at
     * {@code OutlineDocumentSyncService#EVICTION_BATCH_SIZE}): every id is one bind parameter in the
     * {@code IN} list.
     */
    @Modifying
    @Transactional
    @Query(
        value = "UPDATE outline_document SET body_markdown = NULL, body_evicted_at = now() " +
            "WHERE workspace_id = :workspaceId AND id IN (:ids)",
        nativeQuery = true
    )
    int evictBodies(@Param("workspaceId") long workspaceId, @Param("ids") List<Long> ids);
}
