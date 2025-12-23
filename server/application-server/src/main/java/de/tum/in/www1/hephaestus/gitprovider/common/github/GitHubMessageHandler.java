package de.tum.in.www1.hephaestus.gitprovider.common.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Base class for GitHub webhook message handlers.
 * <p>
 * This class handles NATS message routing and JSON parsing using Jackson.
 * Subclasses just implement {@link #handleEvent(Object)} and specify the event
 * key.
 * <p>
 * All webhook payloads are parsed directly to DTOs using Jackson ObjectMapper.
 *
 * @param <T> The DTO type for the webhook event (e.g., GitHubIssueEventDTO)
 */
@Component
public abstract class GitHubMessageHandler<T> implements MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMessageHandler.class);

    private final Class<T> payloadType;

    @Autowired
    private ObjectMapper objectMapper;

    protected GitHubMessageHandler(Class<T> payloadType) {
        this.payloadType = payloadType;
    }

    @Override
    public void onMessage(Message msg) {
        String eventType = getEventKey();
        String subject = msg.getSubject();
        if (!subject.endsWith(eventType)) {
            logger.error("Received message on unexpected subject: {}, expected to end with {}", subject, eventType);
            return;
        }

        String payload = new String(msg.getData(), StandardCharsets.UTF_8);

        try {
            T eventPayload = parsePayload(payload);
            handleEvent(eventPayload);
        } catch (IOException e) {
            logger.error("Failed to parse payload for subject {}: {}", subject, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error while handling message for subject {}: {}", subject, e.getMessage(), e);
        }
    }

    /**
     * Parse the webhook payload JSON directly to the DTO type using Jackson.
     */
    private T parsePayload(String payload) throws IOException {
        return objectMapper.readValue(payload, payloadType);
    }

    /**
     * Handles the parsed event payload.
     *
     * @param eventPayload The parsed webhook event DTO.
     */
    protected abstract void handleEvent(T eventPayload);

    /**
     * Returns the event routing key for NATS subscription.
     * This is typically the GitHub event name in lowercase (e.g., "issues",
     * "pull_request").
     *
     * @return The event key string.
     */
    protected abstract String getEventKey();

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
