package de.tum.cit.aet.hephaestus.gitprovider.webhook;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.DiscardPolicy;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Idempotently creates the {@code gitlab} and {@code github} JetStream streams at startup,
 * mirroring the prior Node service's stream config exactly.
 *
 * <p>Behaviour intentionally constrained:
 * <ul>
 *   <li>If the stream does not exist (404): {@code addStream} with our defaults.</li>
 *   <li>If the stream exists: log INFO; for any config drift compared to our defaults, log WARN
 *       — but NEVER call {@code updateStream}. Operators handle drift explicitly via
 *       {@code nats stream edit}.</li>
 * </ul>
 *
 * <p>This conservative posture mirrors {@code webhook-ingest/src/nats/client.ts:104-126}; the
 * webhook-server taking over stream ownership should not silently clobber retention or storage
 * settings established by the prior Node service.
 */
public class StreamBootstrap {

    private static final Logger log = LoggerFactory.getLogger(StreamBootstrap.class);
    private static final String[] STREAMS = { "gitlab", "github" };

    private final JetStreamManagement jsm;
    private final WebhookProperties properties;

    StreamBootstrap(JetStreamManagement jsm, WebhookProperties properties) {
        this.jsm = jsm;
        this.properties = properties;
    }

    @PostConstruct
    void bootstrap() {
        for (String name : STREAMS) {
            ensureStream(name);
        }
    }

    private void ensureStream(String name) {
        try {
            StreamInfo info = jsm.getStreamInfo(name);
            warnOnDrift(name, info);
            log.info("JetStream stream already exists: name={}", name);
        } catch (JetStreamApiException e) {
            if (e.getErrorCode() == 404) {
                createStream(name);
                return;
            }
            // Any other API failure leaves the receiver in a state where publishes will be
            // rejected by `expectedStream`. Fail bean initialisation so deploys don't claim
            // healthy while silently dropping every webhook.
            throw new IllegalStateException(
                "Failed to inspect JetStream stream: " + name + " (code=" + e.getErrorCode() + ")",
                e
            );
        } catch (IOException e) {
            throw new IllegalStateException("I/O error inspecting JetStream stream: " + name, e);
        }
    }

    private void createStream(String name) {
        WebhookProperties.Stream s = properties.stream();
        StreamConfiguration config = StreamConfiguration.builder()
            .name(name)
            .subjects(name + ".>")
            .retentionPolicy(RetentionPolicy.Limits)
            .discardPolicy(DiscardPolicy.Old)
            .storageType(StorageType.File)
            .duplicateWindow(s.duplicateWindow())
            .maxAge(s.maxAge())
            .maxMessages(s.maxMessages())
            .build();
        try {
            jsm.addStream(config);
            log.info(
                "Created JetStream stream: name={} dedupWindow={} maxAge={} maxMessages={}",
                name,
                s.duplicateWindow(),
                s.maxAge(),
                s.maxMessages()
            );
        } catch (JetStreamApiException | IOException ex) {
            // Fail-fast: a deploy where the stream can't be created must NOT report healthy.
            // Every subsequent publish would fail with `expectedStream` rejection and providers
            // would drop the events after their own retry windows.
            throw new IllegalStateException("Failed to create JetStream stream: " + name, ex);
        }
    }

    private void warnOnDrift(String name, StreamInfo info) {
        WebhookProperties.Stream s = properties.stream();
        var live = info.getConfiguration();
        warnIfDiffers(name, "duplicateWindow", live.getDuplicateWindow(), s.duplicateWindow());
        warnIfDiffers(name, "maxAge", live.getMaxAge(), s.maxAge());
        warnIfDiffersLong(name, "maxMessages", live.getMaxMsgs(), s.maxMessages());
        if (live.getStorageType() != StorageType.File) {
            log.warn(
                "Stream {} live storageType={} differs from expected={} — left unchanged",
                name,
                live.getStorageType(),
                StorageType.File
            );
        }
        if (live.getRetentionPolicy() != RetentionPolicy.Limits) {
            log.warn(
                "Stream {} live retentionPolicy={} differs from expected={} — left unchanged",
                name,
                live.getRetentionPolicy(),
                RetentionPolicy.Limits
            );
        }
        if (live.getDiscardPolicy() != DiscardPolicy.Old) {
            log.warn(
                "Stream {} live discardPolicy={} differs from expected={} — left unchanged",
                name,
                live.getDiscardPolicy(),
                DiscardPolicy.Old
            );
        }
    }

    private static void warnIfDiffers(String stream, String field, Duration live, Duration expected) {
        if (live != null && !live.equals(expected)) {
            log.warn("Stream {} live {}={} differs from expected={} — left unchanged", stream, field, live, expected);
        }
    }

    private static void warnIfDiffersLong(String stream, String field, long live, long expected) {
        if (live > 0 && live != expected) {
            log.warn("Stream {} live {}={} differs from expected={} — left unchanged", stream, field, live, expected);
        }
    }
}
