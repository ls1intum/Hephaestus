package de.tum.cit.aet.hephaestus.integration.outline.sync;

import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentSnapshot;
import java.time.Instant;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Outline mirror's <em>only</em> write transactions. Every method here is a short
 * {@code REQUIRES_NEW} unit of work that touches nothing but the database: read the row, apply the
 * caller's mutation, flush, commit.
 *
 * <p>This class exists so {@link OutlineDocumentSyncService} can run its enumeration and its (up to
 * ~1500 blocking, 10-second-timeout) Outline HTTP calls with <strong>no</strong> transaction and no
 * pooled JDBC connection in hand. Holding a connection across the wire is what turns one slow-but-alive
 * Outline instance into a HikariCP starvation event and a Postgres backend sitting {@code idle in
 * transaction} for a quarter of an hour, blocking vacuum on the mirror table. Same reasoning
 * {@code OutlineCollectionAdminService} already states for the admin surface.
 *
 * <p>Splitting these methods out of {@link OutlineMirrorWriter} is not cosmetic: Spring's
 * {@code @Transactional} only takes effect across a proxy hop, and the retry that {@code OutlineMirrorWriter}
 * layers on top must run <em>outside</em> any transaction — a failed optimistic-lock flush marks its
 * transaction rollback-only (JPA spec), so retrying inside it could never commit.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineMirrorTransactions {

    private static final Logger log = LoggerFactory.getLogger(OutlineMirrorTransactions.class);

    private final OutlineDocumentRepository documentRepository;
    private final OutlineCollectionRepository collectionRepository;

    public OutlineMirrorTransactions(
        OutlineDocumentRepository documentRepository,
        OutlineCollectionRepository collectionRepository
    ) {
        this.documentRepository = documentRepository;
        this.collectionRepository = collectionRepository;
    }

    /**
     * Upsert one mirrored document: read (or create) the row, apply {@code mutator}, flush. The row is
     * re-read <em>inside</em> this transaction rather than carried in from the caller's diff, so the write
     * is applied to current state and the {@code @Version} check has the narrowest possible window.
     *
     * @return the body-free snapshot of the row as written, so the caller's diff map stays current
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutlineDocumentSnapshot upsertDocument(
        long workspaceId,
        long connectionId,
        String documentId,
        Consumer<OutlineDocument> mutator
    ) {
        OutlineDocument doc = documentRepository
            .findByWorkspaceIdAndConnectionIdAndDocumentId(workspaceId, connectionId, documentId)
            .orElseGet(() -> {
                OutlineDocument fresh = new OutlineDocument();
                fresh.setWorkspaceId(workspaceId);
                fresh.setConnectionId(connectionId);
                fresh.setDocumentId(documentId);
                return fresh;
            });
        mutator.accept(doc);
        return OutlineDocumentSnapshot.of(documentRepository.saveAndFlush(doc));
    }

    /**
     * Update an <em>existing</em> mirrored document — the tombstone and archive-stamp paths, which must
     * never conjure a row for a document the mirror does not have.
     *
     * @return the snapshot as written, or {@code null} when the row is gone (hard-deleted by a concurrent
     *         collection erase); the caller treats that as "nothing left to do"
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public @Nullable OutlineDocumentSnapshot updateDocument(
        long workspaceId,
        long connectionId,
        String documentId,
        Consumer<OutlineDocument> mutator
    ) {
        return documentRepository
            .findByWorkspaceIdAndConnectionIdAndDocumentId(workspaceId, connectionId, documentId)
            .map(doc -> {
                mutator.accept(doc);
                return OutlineDocumentSnapshot.of(documentRepository.saveAndFlush(doc));
            })
            .orElse(null);
    }

    /**
     * Update a registered collection's bookkeeping (catalog fields, watermarks, sync status, last error).
     * A row that vanished mid-pass (the admin removed the collection from the mirror while the reconcile
     * was walking it) is skipped: there is nothing left to record progress on.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCollection(
        long workspaceId,
        long connectionId,
        String collectionId,
        Consumer<OutlineCollection> mutator
    ) {
        collectionRepository
            .findByWorkspaceIdAndConnectionIdAndCollectionId(workspaceId, connectionId, collectionId)
            .ifPresentOrElse(
                row -> {
                    mutator.accept(row);
                    collectionRepository.saveAndFlush(row);
                },
                () ->
                    log.debug(
                        "outline.sync: collection {} vanished from the registry mid-pass (workspaceId={}) — skipping its bookkeeping write",
                        collectionId,
                        workspaceId
                    )
            );
    }

    /**
     * The staleness drop: hard-delete tombstoned rows older than {@code cutoff}. Its own read-write
     * transaction — a derived delete inherits Spring Data's {@code readOnly = true} default otherwise and
     * would silently never flush.
     *
     * @return rows deleted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long dropStaleTombstones(long workspaceId, Instant cutoff) {
        return documentRepository.deleteByWorkspaceIdAndDeletedAtBefore(workspaceId, cutoff);
    }
}
