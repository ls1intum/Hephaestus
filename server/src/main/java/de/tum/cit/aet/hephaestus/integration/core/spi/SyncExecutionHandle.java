package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Progress and cooperative-cancellation port exposed to integration sync runners. */
public interface SyncExecutionHandle {
    boolean isCancellationRequested();

    void reportCancelled();

    void reportWarnings();

    void progress(
        @Nullable Integer itemsProcessed,
        @Nullable Integer itemsTotal,
        @Nullable Map<String, Object> progressDetail
    );
}
