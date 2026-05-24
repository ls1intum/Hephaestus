package de.tum.cit.aet.hephaestus.integration.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.lang.Nullable;

/**
 * Universal connector SPI — Airbyte quartet ({@code spec}, {@code check},
 * {@code discover}, {@code read}).
 *
 * <p>One {@code SyncSource} per kind that declares {@link Capability#BACKFILL_SYNC}.
 * Slack does NOT implement (Salesforce ToS prohibits message persistence).
 *
 * <p>Replaces the prior ~30-method {@code BackfillStateProvider} + scattered cursor
 * columns with one stream-shaped contract.
 */
public interface SyncSource {

    IntegrationKind kind();

    /** Manifest of streams this source can produce. Empty list = no backfill. */
    SourceSpec spec();

    /** Probe credentials + reachability without producing records. */
    CheckResult check(IntegrationRef ref);

    /** Optional schema discovery — returns up-to-date stream catalog at runtime. */
    Catalog discover(IntegrationRef ref);

    /**
     * Open a stream of {@link SyncMessage}s for the requested streams. Caller
     * iterates and commits records + state atomically. Failure should be expressed
     * via {@link SyncMessage.Log} + {@link SyncMessage.Park}, not exceptions.
     */
    Stream<SyncMessage> read(ReadRequest request);

    record SourceSpec(List<SyncStream> streams) {
    }

    sealed interface CheckResult permits CheckResult.Ok, CheckResult.Failed {
        record Ok() implements CheckResult {}
        record Failed(String reason) implements CheckResult {}
    }

    record Catalog(List<SyncStream> streams) {
    }

    record ReadRequest(
        IntegrationRef ref,
        List<String> streamNames,
        Map<String, SyncMessage.Cursor> startingStates,
        @Nullable JsonNode overrides
    ) {
        /** Convenience constructor for callers that just want every active stream from scratch. */
        public static ReadRequest fresh(IntegrationRef ref, List<String> streamNames) {
            return new ReadRequest(ref, streamNames, Map.of(), null);
        }

        public Optional<SyncMessage.Cursor> startingState(String stream) {
            return Optional.ofNullable(startingStates.get(stream));
        }
    }
}
