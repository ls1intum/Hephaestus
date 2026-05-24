package de.tum.cit.aet.hephaestus.integration.spi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Discriminated stream-message produced by a {@link SyncSource}.
 *
 * <p>Inspired by the Airbyte / Singer "state-as-message-in-stream" pattern: connectors
 * emit interleaved {@link Record} and {@link State} messages. The orchestrator commits
 * records + state in one transaction, enabling resumable mid-stream crashes.
 */
public sealed interface SyncMessage
    permits SyncMessage.Record,
            SyncMessage.State,
            SyncMessage.Log,
            SyncMessage.Progress,
            SyncMessage.StreamComplete,
            SyncMessage.Heartbeat,
            SyncMessage.Park {

    String stream();

    /** Entity record (vendor-normalized JSON) for upsert into the target store. */
    record Record(
        String stream,
        Instant emittedAt,
        JsonNode data,
        @Nullable JsonNode rawSource
    ) implements SyncMessage {
    }

    /** Cursor checkpoint — persisted before the next batch fetches. */
    record State(String stream, Cursor cursor) implements SyncMessage {
    }

    /** Diagnostic line, level included for filter / dashboard routing. */
    record Log(String stream, Level level, String message) implements SyncMessage {
        public enum Level { TRACE, DEBUG, INFO, WARN, ERROR }
    }

    /** Coarse counter — emitted every N records for ETA / observability. */
    record Progress(String stream, long recordsEmitted, @Nullable Long expectedTotal) implements SyncMessage {
    }

    /** End-of-stream marker — triggers reconciliation accounting. */
    record StreamComplete(String stream, Instant completedAt, long totalRecords) implements SyncMessage {
    }

    /** Keep-alive for long-running streams; resets ack timers without producing records. */
    record Heartbeat(String stream, Instant at) implements SyncMessage {
    }

    /**
     * Clean rate-limit pause. Orchestrator parks the stream until {@code retryAfter}
     * elapses; resumes from the last persisted {@link State}.
     */
    record Park(String stream, Duration retryAfter, String reason) implements SyncMessage {
    }

    /**
     * Cursor sealed adds {@link Watermark} for timestamp-based vendors (Slack) and
     * {@code validUntil} on {@link Opaque} to express server-side expiration.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Cursor.Opaque.class, name = "Opaque"),
        @JsonSubTypes.Type(value = Cursor.Numbered.class, name = "Numbered"),
        @JsonSubTypes.Type(value = Cursor.Watermark.class, name = "Watermark")
    })
    sealed interface Cursor permits Cursor.Opaque, Cursor.Numbered, Cursor.Watermark {

        record Opaque(String value, @Nullable Instant validUntil) implements Cursor {
            public Opaque(String value) {
                this(value, null);
            }
        }

        record Numbered(long highWatermark, long checkpoint) implements Cursor {
        }

        /** Slack-style timestamp watermark (ts string). */
        record Watermark(String stamp) implements Cursor {
        }
    }
}
