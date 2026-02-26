package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.exception.PayloadParsingException;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base class for GitLab webhook message handlers.
 * <p>
 * This class handles NATS message routing and JSON parsing using the centralized
 * {@link NatsMessageDeserializer}. Subclasses just implement {@link #handleEvent(Object)}
 * and specify the event type.
 * <p>
 * All webhook payloads are parsed directly to DTOs using Jackson ObjectMapper.
 * <p>
 * Unlike {@link de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler},
 * this class does not use domain-based routing (REPOSITORY/ORGANIZATION/INSTALLATION).
 * GitLab uses PAT-based auth rather than app installations, so all events are routed
 * via a flat event-type mapping in {@link GitLabMessageHandlerRegistry}.
 *
 * @param <T> The DTO type for the webhook event
 */
@Component
public abstract class GitLabMessageHandler<T> implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(GitLabMessageHandler.class);

    private final Class<T> payloadType;
    private final NatsMessageDeserializer deserializer;
    private final TransactionTemplate transactionTemplate;

    /**
     * Constructor for message handlers.
     * <p>
     * IMPORTANT: The TransactionTemplate is required because Spring AOP proxy-based
     * {@code @Transactional} DOES NOT WORK for internal method calls (self-invocation).
     * When onMessage() calls handleEvent(), it bypasses the proxy entirely.
     * Using TransactionTemplate ensures the transaction boundary is correctly applied.
     */
    protected GitLabMessageHandler(
        Class<T> payloadType,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        this.payloadType = payloadType;
        this.deserializer = deserializer;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void onMessage(Message msg) {
        GitLabEventType eventType = getEventType();
        String eventKey = eventType.getValue();
        String subject = msg.getSubject();
        String safeSubject = sanitizeForLog(subject);
        if (!subject.endsWith(eventKey)) {
            log.error(
                "Rejected message: reason=unexpectedSubject, subject={}, expectedSuffix={}",
                safeSubject,
                eventKey
            );
            return;
        }

        try {
            T eventPayload = deserializer.deserialize(msg, payloadType);
            // CRITICAL: Use TransactionTemplate to wrap handleEvent() in a transaction.
            // Spring AOP @Transactional does NOT work for self-invocation (this.handleEvent()).
            // Without this, all @Modifying JPA queries will fail with TransactionRequiredException.
            transactionTemplate.executeWithoutResult(status -> handleEvent(eventPayload));
        } catch (IOException e) {
            log.error("Failed to parse payload: subject={}", safeSubject, e);
            throw new PayloadParsingException("Payload parsing failed for subject: " + safeSubject, e);
        }
        // Note: Other exceptions are NOT logged here to avoid duplicate logging.
        // NatsConsumerService.handleMessage() will catch and log the error.
    }

    /**
     * Handles the parsed event payload.
     *
     * @param eventPayload The parsed webhook event DTO.
     */
    protected abstract void handleEvent(T eventPayload);

    /**
     * Returns the event type for this handler.
     * Used for NATS subscription routing.
     *
     * @return The GitLab event type enum value.
     */
    public abstract GitLabEventType getEventType();
}
