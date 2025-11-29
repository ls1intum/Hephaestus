package de.tum.in.www1.hephaestus.gitprovider.common.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    private T parsePayload(String payload) throws IOException {
        if (GHEventPayload.class.isAssignableFrom(payloadType)) {
            try (StringReader reader = new StringReader(payload)) {
                Class<? extends GHEventPayload> ghType = payloadType.asSubclass(GHEventPayload.class);
                GHEventPayload parsed = GitHub.offline().parseEventPayload(reader, ghType);
                return payloadType.cast(parsed);
            }
        }
        return objectMapper.readValue(payload, payloadType);
    }

    /**
     * Handles the parsed event payload.
     *
     * @param eventPayload The parsed GHEventPayload.
     */
    protected abstract void handleEvent(T eventPayload);

    /**
     * Returns the GHEvent that this handler is responsible for.
     *
     * @return The GHEvent.
     */
    protected GHEvent getHandlerEvent() {
        return null;
    }

    /**
     * Event routing key used for NATS subjects. Defaults to the GHEvent name in lower-case.
     */
    protected String getEventKey() {
        GHEvent event = getHandlerEvent();
        if (event == null) {
            throw new IllegalStateException(
                getClass().getSimpleName() + " must override getEventKey() when no GHEvent is provided"
            );
        }
        return event.name().toLowerCase(Locale.ENGLISH);
    }

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
