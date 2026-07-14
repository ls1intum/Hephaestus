package de.tum.cit.aet.hephaestus.integration.outline.sync;

import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Adapts a core {@link SyncJobHandle} to the module-local {@link OutlineSyncProgressListener}, so
 * {@link OutlineDocumentSyncService} itself never depends on the core sync-job types (kept swappable /
 * unit-testable without the job machinery). Shared by {@link OutlineDocumentSyncScheduler}'s scheduled
 * passes and the manual-trigger {@code OutlineIntegrationSyncRunner}.
 */
public final class OutlineSyncProgress {

    private OutlineSyncProgress() {}

    public static OutlineSyncProgressListener adapt(SyncJobHandle handle) {
        return new OutlineSyncProgressListener() {
            @Override
            public boolean isCancelled() {
                return handle.isCancellationRequested();
            }

            @Override
            public void onCollectionDone(int done, int total, @Nullable String collectionName) {
                handle.progress(
                    done,
                    total,
                    collectionName == null ? Map.of() : Map.of("currentCollection", collectionName)
                );
            }
        };
    }
}
