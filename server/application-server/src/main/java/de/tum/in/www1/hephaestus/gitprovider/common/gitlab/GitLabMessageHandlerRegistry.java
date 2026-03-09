package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry that auto-discovers GitLab message handlers via constructor injection.
 * <p>
 * Unlike the GitHub registry which uses domain-based routing (REPOSITORY, ORGANIZATION,
 * INSTALLATION), the GitLab registry uses a flat event-type-to-handler mapping.
 * GitLab does not have app installation events (uses PAT-based auth), and the
 * namespace/project hierarchy is handled at the NATS subject level.
 */
@Component
public class GitLabMessageHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(GitLabMessageHandlerRegistry.class);

    private final Map<String, GitLabMessageHandler<?>> handlerMap;

    public GitLabMessageHandlerRegistry(GitLabMessageHandler<?>[] handlers) {
        Map<String, GitLabMessageHandler<?>> map = new HashMap<>();
        for (GitLabMessageHandler<?> handler : handlers) {
            String eventKey = normalize(handler.getEventType().getValue());
            if (eventKey == null || eventKey.isBlank()) {
                throw new IllegalStateException(handler.getClass().getSimpleName() + " must declare an event key");
            }
            if (map.containsKey(eventKey)) {
                throw new IllegalStateException(
                    "Duplicate GitLab message handler for event key '" +
                        eventKey +
                        "': " +
                        handler.getClass().getSimpleName() +
                        " conflicts with " +
                        map.get(eventKey).getClass().getSimpleName()
                );
            }
            map.put(eventKey, handler);
        }
        this.handlerMap = Collections.unmodifiableMap(map);
        log.info("Registered {} GitLab message handler(s)", handlers.length);
    }

    /**
     * Returns the handler for the given event key, or null if no handler is registered.
     *
     * @param eventKey the event type string (e.g., "merge_request", "issue")
     * @return the handler, or null
     */
    public GitLabMessageHandler<?> getHandler(String eventKey) {
        return handlerMap.get(normalize(eventKey));
    }

    /**
     * Returns the list of supported event types.
     */
    public List<String> getSupportedEvents() {
        return new ArrayList<>(handlerMap.keySet());
    }

    private String normalize(String eventKey) {
        return eventKey == null ? null : eventKey.trim().toLowerCase(Locale.ENGLISH);
    }
}
