package de.tum.in.www1.hephaestus.gitprovider.common.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Base class for GitHub webhook message handlers.
 * <p>
 * This class handles NATS message routing and JSON parsing using the centralized
 * {@link NatsMessageDeserializer}. Subclasses just implement {@link #handleEvent(Object)}
 * and specify the event key.
 * <p>
 * All webhook payloads are parsed directly to DTOs using Jackson ObjectMapper.
 *
 * @param <T> The DTO type for the webhook event (e.g., GitHubIssueEventDTO)
 */
@Component
public abstract class GitHubMessageHandler<T> implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(GitHubMessageHandler.class);

    private final Class<T> payloadType;
    private final NatsMessageDeserializer deserializer;

    protected GitHubMessageHandler(Class<T> payloadType, NatsMessageDeserializer deserializer) {
        this.payloadType = payloadType;
        this.deserializer = deserializer;
    }

    @Override
    public void onMessage(Message msg) {
        GitHubEventType eventType = getEventType();
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
            handleEvent(eventPayload);
        } catch (IOException e) {
            log.error("Failed to parse payload: subject={}", safeSubject, e);
        } catch (Exception e) {
            log.error("Failed to handle message: subject={}", safeSubject, e);
        }
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
     * @return The GitHub event type enum value.
     */
    public abstract GitHubEventType getEventType();

    /**
     * Domain classification for handler routing. Defaults to REPOSITORY.
     */
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.REPOSITORY;
    }

    /**
     * Optionally declare additional domains this handler should subscribe to.
     * Default: none.
     */
    public List<GitHubMessageDomain> getAdditionalDomains() {
        return List.of();
    }

    public enum GitHubMessageDomain {
        REPOSITORY,
        ORGANIZATION,
        INSTALLATION,
    }
}
