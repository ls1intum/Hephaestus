package de.tum.cit.aet.hephaestus.integration.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.springframework.lang.Nullable;

/**
 * Stream declaration returned by {@link SyncSource#discover()}.
 *
 * <p>One stream per resource type per vendor (e.g. {@code github.issues},
 * {@code github.pullrequests}). Per-Connection enabled-stream selection lives in
 * {@code Connection.config.enabledStreams}.
 */
public record SyncStream(
    String name,
    @Nullable JsonNode schema,
    SyncMode defaultSyncMode,
    @Nullable String defaultCursorField,
    @Nullable Duration reconciliationWindow,
    Stability stability,
    boolean required
) {
    public enum SyncMode {
        FULL_REFRESH,
        INCREMENTAL,
        CHANGE_DATA_CAPTURE,
        DISABLED
    }

    /**
     * Per-stream stability — connector and stream can have different levels
     * (a STABLE connector may ship one EXPERIMENTAL stream).
     */
    public enum Stability {
        EXPERIMENTAL,
        BETA,
        STABLE
    }
}
