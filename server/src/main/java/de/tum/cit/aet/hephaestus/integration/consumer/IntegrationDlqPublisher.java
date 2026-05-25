package de.tum.cit.aet.hephaestus.integration.consumer;

import de.tum.cit.aet.hephaestus.gitprovider.webhook.StreamBootstrap;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PublishOptions;
import io.nats.client.impl.Headers;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Publishes poison messages to the {@code INTEGRATION_DLQ} stream before they are
 * ACKed off the original consumer. Pairs with {@link IntegrationPoisonHandler}: the
 * DLQ publish happens FIRST; if it succeeds, the poison message is ACKed; if it fails,
 * the handler NAKs so the message stays in flight for another attempt.
 *
 * <p>Without this, the {@code IntegrationPoisonHandler.ackPoison} path silently drops
 * the message body — operators saw only a counter + WARN log, with no replay path.
 * Per NATS Docs "Consumer Details" the canonical DLQ pattern is a dedicated stream
 * with {@code WorkQueue} retention; an op replays a specific message by republishing
 * its body onto the original stream.
 *
 * <p>Subject pattern: {@code integration.dlq.<kind>.<original-subject-tokens>}. The
 * full original subject is preserved in the {@code X-Original-Subject} header so the
 * raw subject is visible without parsing the body.
 */
@Component
public class IntegrationDlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(IntegrationDlqPublisher.class);

    static final String DLQ_COUNTER = "integration.consumer.dlq";

    static final String HEADER_ORIGINAL_SUBJECT = "X-Original-Subject";
    static final String HEADER_FAILURE_REASON = "X-Failure-Reason";
    static final String HEADER_DELIVERED_COUNT = "X-Delivered-Count";
    static final String HEADER_STREAM_SEQUENCE = "X-Stream-Sequence";

    @Nullable
    private final JetStream jetStream;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /**
     * @param jetStream optional — null on pods where the integration consumer is
     *     disabled (worker / webhook-only runtime roles). The poison handler treats
     *     a missing publisher as "no DLQ; ACK and rely on the counter alone".
     */
    public IntegrationDlqPublisher(@Nullable JetStream jetStream, MeterRegistry meterRegistry) {
        this.jetStream = jetStream;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Publishes {@code msg} to the DLQ stream. Returns true on success, false on any
     * failure — the caller (poison handler) MUST NAK on false so the message stays in
     * flight for retry. Synchronous to keep the ack/dlq ordering strict.
     */
    public boolean publish(Message msg, String kindTag, String reason, long deliveredCount, long streamSeq) {
        if (jetStream == null) {
            log.debug("No JetStream publisher wired — skipping DLQ for subject={}", msg.getSubject());
            return false;
        }
        try {
            String subject = StreamBootstrap.DLQ_SUBJECT_PREFIX + "." + kindTag + "." + msg.getSubject();
            Headers headers = new Headers()
                .add(HEADER_ORIGINAL_SUBJECT, msg.getSubject())
                .add(HEADER_FAILURE_REASON, reason)
                .add(HEADER_DELIVERED_COUNT, Long.toString(deliveredCount))
                .add(HEADER_STREAM_SEQUENCE, Long.toString(streamSeq));
            PublishOptions opts = PublishOptions.builder()
                .expectedStream(StreamBootstrap.DLQ_STREAM)
                .build();
            jetStream.publish(subject, headers, msg.getData(), opts);
            counter(kindTag).increment();
            log.warn(
                "Published poison message to DLQ: dlqSubject={} originalSubject={} reason={}",
                subject, msg.getSubject(), reason);
            return true;
        } catch (Exception e) {
            log.error(
                "Failed to publish to DLQ — poison message will NOT be ACKed (will retry): subject={} error={}",
                msg.getSubject(), e.toString());
            return false;
        }
    }

    private Counter counter(String kindTag) {
        return counters.computeIfAbsent(kindTag.toLowerCase(Locale.ROOT),
            k -> Counter.builder(DLQ_COUNTER).tag("kind", k).register(meterRegistry));
    }
}
