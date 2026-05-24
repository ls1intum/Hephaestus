package de.tum.cit.aet.hephaestus.integration.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.spi.SyncMessage;
import de.tum.cit.aet.hephaestus.integration.spi.SyncSource;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Per-Connection sync runner.
 *
 * <p>Built by {@code SyncRuntimeBootstrap} on the sync runtime role (not a Spring
 * bean directly — separate ownership). Iterates {@link SyncSource#read} streams of
 * {@link SyncMessage}s and commits records + state atomically.
 *
 * <p>One virtual thread per {@code (Connection, stream)}; this class is the
 * coordinator. Per agent B2: state-as-message-in-stream → no separate cursor
 * methods; failure expressed as {@link SyncMessage.Park} not exceptions.
 */
public class SyncOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SyncOrchestrator.class);

    private final ConnectionRepository connectionRepository;
    private final SyncStateRepository syncStateRepository;
    private final Map<String, SyncMessageHandler> handlersByStream;
    private final Map<IntegrationRef, SyncSource> sourcesByRef = new ConcurrentHashMap<>();
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    public SyncOrchestrator(
        ConnectionRepository connectionRepository,
        SyncStateRepository syncStateRepository,
        List<SyncMessageHandler> handlers,
        TransactionTemplate tx,
        ObjectMapper objectMapper
    ) {
        this.connectionRepository = connectionRepository;
        this.syncStateRepository = syncStateRepository;
        this.handlersByStream = new HashMap<>();
        this.tx = tx;
        this.objectMapper = objectMapper;
        for (SyncMessageHandler h : handlers) {
            SyncMessageHandler prev = handlersByStream.putIfAbsent(h.streamName(), h);
            if (prev != null) {
                throw new IllegalStateException(
                    "Duplicate SyncMessageHandler for stream='" + h.streamName() + "': "
                        + prev.getClass() + " vs " + h.getClass()
                );
            }
        }
    }

    /** Register the source for a given Connection. Called by {@code SyncRuntimeBootstrap}. */
    public void registerSource(IntegrationRef ref, SyncSource source) {
        sourcesByRef.put(ref, source);
    }

    /**
     * Read the catalog and process the given streams to completion (or first Park).
     * Returns the number of records persisted across all requested streams.
     */
    public long runOnce(IntegrationRef ref, List<String> streamNames) {
        SyncSource source = sourcesByRef.get(ref);
        if (source == null) {
            throw new IllegalStateException("No SyncSource registered for " + ref);
        }
        Connection connection = connectionRepository
            .findByWorkspaceIdAndKindAndInstanceKey(ref.workspaceId(), ref.kind(), ref.instanceKey())
            .orElseThrow(() -> new IllegalStateException("No Connection for " + ref));

        Map<String, SyncMessage.Cursor> startingStates = new HashMap<>();
        for (String stream : streamNames) {
            loadCursor(connection.getId(), stream).ifPresent(c -> startingStates.put(stream, c));
        }

        SyncSource.ReadRequest request = new SyncSource.ReadRequest(ref, streamNames, startingStates, null);
        long total = 0;
        try (Stream<SyncMessage> messages = source.read(request)) {
            var iterator = messages.iterator();
            while (iterator.hasNext()) {
                SyncMessage msg = iterator.next();
                Long countDelta = handle(connection, msg);
                if (countDelta != null) total += countDelta;
            }
        }
        return total;
    }

    private Long handle(Connection connection, SyncMessage msg) {
        return tx.execute(status -> switch (msg) {
            case SyncMessage.Record r -> {
                SyncMessageHandler handler = handlersByStream.get(r.stream());
                if (handler == null) {
                    log.warn("No SyncMessageHandler for stream={} — dropping record", r.stream());
                    yield 0L;
                }
                handler.handleRecord(connection.toRef(), r);
                yield 1L;
            }
            case SyncMessage.State s -> {
                persistCursor(connection, s.stream(), s.cursor());
                yield 0L;
            }
            case SyncMessage.StreamComplete c -> {
                SyncMessageHandler handler = handlersByStream.get(c.stream());
                if (handler != null) handler.onStreamComplete(connection.toRef(), c);
                updateLastSyncedAt(connection, c.stream(), c.completedAt());
                yield 0L;
            }
            case SyncMessage.Log l -> {
                log.atLevel(toSlf4j(l.level())).log("[sync stream={}] {}", l.stream(), l.message());
                yield 0L;
            }
            case SyncMessage.Progress p -> {
                log.debug("Progress stream={} emitted={} expected={}", p.stream(), p.recordsEmitted(), p.expectedTotal());
                yield 0L;
            }
            case SyncMessage.Heartbeat h -> 0L;
            case SyncMessage.Park p -> {
                log.info("Stream={} parked: {} (retry in {})", p.stream(), p.reason(), p.retryAfter());
                yield 0L;
            }
        });
    }

    private Optional<SyncMessage.Cursor> loadCursor(long connectionId, String streamName) {
        return syncStateRepository.findByConnectionIdAndStreamName(connectionId, streamName)
            .map(SyncState::getCursor)
            .filter(json -> !json.isBlank() && !json.equals("{}"))
            .flatMap(this::deserializeCursor);
    }

    private Optional<SyncMessage.Cursor> deserializeCursor(String json) {
        try {
            return Optional.ofNullable(objectMapper.readValue(json, SyncMessage.Cursor.class));
        } catch (IOException e) {
            log.warn("Failed to deserialize sync cursor JSON='{}': {}", json, e.toString());
            return Optional.empty();
        }
    }

    private void persistCursor(Connection connection, String streamName, SyncMessage.Cursor cursor) {
        SyncState state = syncStateRepository
            .findByConnectionIdAndStreamName(connection.getId(), streamName)
            .orElseGet(() -> new SyncState(connection, streamName));
        try {
            state.setCursor(objectMapper.writeValueAsString(cursor));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize cursor for stream " + streamName, e);
        }
        syncStateRepository.save(state);
    }

    private void updateLastSyncedAt(Connection connection, String streamName, Instant at) {
        SyncState state = syncStateRepository
            .findByConnectionIdAndStreamName(connection.getId(), streamName)
            .orElseGet(() -> new SyncState(connection, streamName));
        state.setLastSyncedAt(at);
        syncStateRepository.save(state);
    }

    private static org.slf4j.event.Level toSlf4j(SyncMessage.Log.Level level) {
        return switch (level) {
            case TRACE -> org.slf4j.event.Level.TRACE;
            case DEBUG -> org.slf4j.event.Level.DEBUG;
            case INFO -> org.slf4j.event.Level.INFO;
            case WARN -> org.slf4j.event.Level.WARN;
            case ERROR -> org.slf4j.event.Level.ERROR;
        };
    }
}
