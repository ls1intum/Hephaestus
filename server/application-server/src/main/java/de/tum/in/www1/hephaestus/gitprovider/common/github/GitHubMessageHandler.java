package de.tum.in.www1.hephaestus.gitprovider.common.github;

import io.nats.client.Message;
import io.nats.client.MessageHandler;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public abstract class GitHubMessageHandler<T extends GHEventPayload> implements MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMessageHandler.class);

    private final Class<T> payloadType;

    protected GitHubMessageHandler(Class<T> payloadType) {
        this.payloadType = payloadType;
    }

    @Override
    public void onMessage(Message msg) {
        String eventType = getHandlerEvent().name().toLowerCase();
        String subject = msg.getSubject();
        if (!subject.endsWith(eventType)) {
            logger.error("Received message on unexpected subject: {}, expected to end with {}", subject, eventType);
            return;
        }

        String payload = new String(msg.getData(), StandardCharsets.UTF_8);

        try (StringReader reader = new StringReader(payload)) {
            T eventPayload = GitHub.offline().parseEventPayload(reader, payloadType);
            handleEvent(eventPayload);
        } catch (IOException e) {
            logger.error("Failed to parse payload for subject {}: {}", subject, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error while handling message for subject {}: {}", subject, e.getMessage(), e);
        }
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
    protected abstract GHEvent getHandlerEvent();

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

    /**
     * Custom event name for payloads not covered by {@link GHEvent}.
     * <p>
     * Return {@code null} to rely on the enum-based routing.
     */
    public String getCustomEventName() {
        return null;
    }

    public enum GitHubMessageDomain {
        REPOSITORY,
        ORGANIZATION,
        INSTALLATION,
    }
}
