package de.tum.cit.aet.hephaestus.integration.outline.sync;

import org.jspecify.annotations.Nullable;

/**
 * Optional per-collection progress + cooperative-cancellation hook for
 * {@link OutlineDocumentSyncService#syncWorkspace(long, OutlineSyncProgressListener)}. Kept local to this
 * module (rather than taking a {@code core.sync.SyncJobHandle} directly) so the sync service itself does not
 * depend on the core sync-job types — {@link OutlineSyncProgress} adapts a real handle to this shape for the
 * scheduler / runner callers that have one.
 */
public interface OutlineSyncProgressListener {
    /** Checked between collections; a full workspace pass exits early (best-effort) once this is true. */
    boolean isCancelled();

    /** Called once a collection's sync attempt (success or recorded error) has finished. */
    void onCollectionDone(int done, int total, @Nullable String collectionName);

    /** Marks the enclosing job as partially successful without coupling this module to job types. */
    default void onWarning() {}
}
