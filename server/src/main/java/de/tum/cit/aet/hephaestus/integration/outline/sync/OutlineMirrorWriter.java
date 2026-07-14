package de.tum.cit.aet.hephaestus.integration.outline.sync;

import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentSnapshot;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Retry-once façade over {@link OutlineMirrorTransactions} — the seam {@link OutlineDocumentSyncService}
 * writes the mirror through.
 *
 * <p>Both Outline mirror rows carry a {@code @Version} column because they have genuinely concurrent
 * writers that are NOT serialized per workspace: a webhook-triggered {@code refreshDocument} versus a
 * mid-flight full reconcile for the same document, and the admin PATCH versus a request-thread sync kick
 * for the same collection. Without the version column both writers do a full-column save and the loser's
 * commit silently clobbers the winner's — a lost update.
 *
 * <p>The retry deliberately re-runs the <em>whole</em> unit of work in a <strong>fresh</strong>
 * transaction rather than re-saving inside the failed one. An optimistic-lock failure at flush marks its
 * transaction rollback-only (JPA spec: {@code OptimisticLockException} ⇒ rollback), so a same-transaction
 * retry could never commit — it would look like it worked and quietly lose the write. Re-running instead
 * re-reads the row at its current version and re-applies the caller's mutation to it, which is exactly
 * what "merge my change onto whatever the winner wrote" means. The mutations are pure functions of the
 * upstream payload the caller already fetched, so a replay costs no extra Outline call.
 *
 * <p>Losing twice is logged and skipped: the field mutations are dropped, and the next reconcile re-diffs
 * and converges on its own.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineMirrorWriter {

    private static final Logger log = LoggerFactory.getLogger(OutlineMirrorWriter.class);

    private final OutlineMirrorTransactions transactions;

    public OutlineMirrorWriter(OutlineMirrorTransactions transactions) {
        this.transactions = transactions;
    }

    /**
     * Upsert one mirrored document. See {@link OutlineMirrorTransactions#upsertDocument}.
     *
     * @return the snapshot of the written row, or {@code null} when the write lost the optimistic-lock
     *         race twice (skipped; the next reconcile converges)
     */
    public @Nullable OutlineDocumentSnapshot upsertDocument(
        long workspaceId,
        long connectionId,
        String documentId,
        Consumer<OutlineDocument> mutator
    ) {
        return retryOnce(
            () -> transactions.upsertDocument(workspaceId, connectionId, documentId, mutator),
            "document",
            documentId
        );
    }

    /**
     * Update an existing mirrored document (tombstone / archive stamp). See
     * {@link OutlineMirrorTransactions#updateDocument}.
     *
     * @return the snapshot of the written row, or {@code null} when the row was already gone or the write
     *         lost the optimistic-lock race twice
     */
    public @Nullable OutlineDocumentSnapshot updateDocument(
        long workspaceId,
        long connectionId,
        String documentId,
        Consumer<OutlineDocument> mutator
    ) {
        return retryOnce(
            () -> transactions.updateDocument(workspaceId, connectionId, documentId, mutator),
            "document",
            documentId
        );
    }

    /** Update a registered collection's bookkeeping. See {@link OutlineMirrorTransactions#updateCollection}. */
    public void updateCollection(
        long workspaceId,
        long connectionId,
        String collectionId,
        Consumer<OutlineCollection> mutator
    ) {
        retryOnce(
            () -> {
                transactions.updateCollection(workspaceId, connectionId, collectionId, mutator);
                return null;
            },
            "collection",
            collectionId
        );
    }

    /** The staleness drop. See {@link OutlineMirrorTransactions#dropStaleTombstones}. */
    public long dropStaleTombstones(long workspaceId, Instant cutoff) {
        return transactions.dropStaleTombstones(workspaceId, cutoff);
    }

    /**
     * Runs {@code write} — one whole {@code REQUIRES_NEW} transaction — retrying it exactly once in a new
     * transaction when a concurrent writer won the optimistic-lock race. A second conflict is logged and
     * skipped rather than propagated: one contended row must not abort the pass around it.
     */
    private <T> @Nullable T retryOnce(Supplier<T> write, String kind, String identifier) {
        try {
            return write.get();
        } catch (ObjectOptimisticLockingFailureException conflict) {
            log.debug("outline.sync: concurrent update racing {} {} — retrying once", kind, identifier);
        }
        try {
            return write.get();
        } catch (ObjectOptimisticLockingFailureException stillContended) {
            log.warn(
                "outline.sync: {} {} lost the optimistic-lock retry too — skipping, the next reconcile fixes it",
                kind,
                identifier
            );
            return null;
        }
    }
}
