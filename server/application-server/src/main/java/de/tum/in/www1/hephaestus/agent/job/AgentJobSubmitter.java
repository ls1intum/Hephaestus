package de.tum.in.www1.hephaestus.agent.job;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.api.PublishAck;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes agent jobs to NATS JetStream after the database transaction commits.
 *
 * <p>Uses {@code @TransactionalEventListener(AFTER_COMMIT)} + {@code @Async} to match the
 * existing codebase convention (see {@code BadPracticeEventListener}). The async execution
 * ensures we don't block the originating thread while waiting for the JetStream ack.
 *
 * <p>If NATS publish fails, the zombie sweeper re-publishes stale QUEUED jobs periodically.
 */
@Component
@ConditionalOnBean(name = "agentNatsConnection")
public class AgentJobSubmitter {

    private static final Logger log = LoggerFactory.getLogger(AgentJobSubmitter.class);

    private static final long PUBLISH_TIMEOUT_SECONDS = 5;

    private final JetStream jetStream;
    private final AgentNatsProperties properties;
    private final Counter publishSuccess;
    private final Counter publishFailure;

    public AgentJobSubmitter(
        @Qualifier("agentNatsConnection") Connection connection,
        AgentNatsProperties properties,
        MeterRegistry meterRegistry
    ) throws Exception {
        this.jetStream = connection.jetStream();
        this.properties = properties;
        this.publishSuccess = Counter.builder("agent.job.nats.publish")
            .tag("outcome", "success")
            .register(meterRegistry);
        this.publishFailure = Counter.builder("agent.job.nats.publish")
            .tag("outcome", "failure")
            .register(meterRegistry);
    }

    /**
     * Publish a newly created job to NATS after the transaction commits.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobCreated(AgentJobCreatedEvent event) {
        publish(event.jobId(), event.workspaceId());
    }

    /**
     * Publish (or re-publish) a job to NATS JetStream.
     *
     * <p>Used by both the event listener (new jobs) and the zombie sweeper (stale jobs).
     *
     * @param jobId       the job UUID (message payload)
     * @param workspaceId workspace ID (NATS subject token)
     */
    public void publish(UUID jobId, Long workspaceId) {
        String subject = AgentNatsProperties.SUBJECT_PREFIX + workspaceId;
        byte[] payload = jobId.toString().getBytes(StandardCharsets.UTF_8);

        try {
            PublishAck ack = jetStream.publishAsync(subject, payload).get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            publishSuccess.increment();
            log.debug("Published job to NATS: jobId={}, subject={}, seq={}", jobId, subject, ack.getSeqno());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            publishFailure.increment();
            log.warn(
                "Failed to publish job to NATS (zombie sweeper will recover): jobId={}, subject={}, error={}",
                jobId,
                subject,
                e.getMessage()
            );
        }
    }
}
