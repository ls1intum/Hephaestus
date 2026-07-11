package de.tum.cit.aet.hephaestus.integration.outline.sync;

import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import jakarta.persistence.EntityManager;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Retry-once optimistic save shared by {@link OutlineDocumentSyncService}'s document and collection
 * upserts. Lives outside the {@code *Service} class deliberately: it is pure persistence plumbing with
 * no business decision of its own, so folding it into the service would only inflate that class's
 * method count without moving any real logic there.
 *
 * <p>Both Outline mirror rows carry a {@code @Version} column because they have genuinely concurrent
 * writers that are NOT serialized per workspace: a webhook-triggered {@code refreshDocument} versus a
 * mid-flight full reconcile for the document, and the admin PATCH versus a request-thread sync kick for
 * the collection. Each runs in its own {@code REQUIRES_NEW} transaction, so a full-column save from the
 * loser would silently clobber the winner — a lost update. {@code saveAndFlush} forces the version check
 * NOW (inside this method) rather than deferring it to the enclosing transaction's commit, so a lost
 * race is isolated to the single row instead of aborting the whole pass with a raw
 * {@code ObjectOptimisticLockingFailureException}.
 */
final class OutlineOptimisticSave {

    private static final Logger log = LoggerFactory.getLogger(OutlineOptimisticSave.class);

    private OutlineOptimisticSave() {}

    /** Retry-once save of a mirrored document. See {@link #saveWithRetry}. */
    static void saveDocument(OutlineDocumentRepository repository, EntityManager entityManager, OutlineDocument doc) {
        saveWithRetry(
            repository,
            entityManager,
            doc,
            doc.getId(),
            OutlineDocument::getVersion,
            OutlineDocument::setVersion,
            "document",
            doc.getDocumentId()
        );
    }

    /** Retry-once save of a mirrored collection's bookkeeping. See {@link #saveWithRetry}. */
    static void saveCollection(
        OutlineCollectionRepository repository,
        EntityManager entityManager,
        OutlineCollection collection
    ) {
        saveWithRetry(
            repository,
            entityManager,
            collection,
            collection.getId(),
            OutlineCollection::getVersion,
            OutlineCollection::setVersion,
            "collection",
            collection.getCollectionId()
        );
    }

    /**
     * Saves {@code entity}, retrying exactly once against the row's current version on a genuine
     * concurrent write. On conflict the stale managed copy is detached so the retry's re-read is a real
     * {@code SELECT} rather than this transaction's own identity-mapped copy, its version is adopted, and
     * the write is retried once. A second conflict — or a row that vanished (e.g. its collection was
     * removed from the mirror between the conflict and the retry) — is logged and skipped; the field
     * mutations already applied to {@code entity} stay visible on that reference, only the DB write is
     * dropped, and the next reconcile re-diffs and converges on its own.
     */
    private static <T> void saveWithRetry(
        JpaRepository<T, Long> repository,
        EntityManager entityManager,
        T entity,
        @Nullable Long id,
        Function<T, Long> versionGetter,
        BiConsumer<T, Long> versionSetter,
        String kind,
        @Nullable String identifier
    ) {
        try {
            repository.saveAndFlush(entity);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("outline.sync: concurrent update racing {} {} — retrying once", kind, identifier);
            entityManager.detach(entity);
            var current = repository.findById(id);
            if (current.isEmpty()) {
                log.warn(
                    "outline.sync: {} {} vanished during the optimistic-lock retry — skipping, the next reconcile fixes it",
                    kind,
                    identifier
                );
                return;
            }
            versionSetter.accept(entity, versionGetter.apply(current.get()));
            try {
                repository.saveAndFlush(entity);
            } catch (ObjectOptimisticLockingFailureException stillStale) {
                log.warn(
                    "outline.sync: {} {} lost the optimistic-lock retry too — skipping, the next reconcile fixes it",
                    kind,
                    identifier
                );
            }
        }
    }
}
