package de.tum.cit.aet.hephaestus.gitprovider.common.github;

import de.tum.cit.aet.hephaestus.gitprovider.sync.SyncSchedulerProperties;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GitHubMessageHandlerRegistry {

    /** Webhook events skipped when {@code hephaestus.sync.projects.enabled=false}. */
    private static final Set<GitHubEventType> PROJECT_EVENTS = EnumSet.of(
        GitHubEventType.PROJECTS_V2,
        GitHubEventType.PROJECTS_V2_ITEM,
        GitHubEventType.PROJECTS_V2_STATUS_UPDATE
    );

    /** Webhook events skipped when {@code hephaestus.sync.discussions.enabled=false}. */
    private static final Set<GitHubEventType> DISCUSSION_EVENTS = EnumSet.of(
        GitHubEventType.DISCUSSION,
        GitHubEventType.DISCUSSION_COMMENT
    );

    private final Map<String, GitHubMessageHandler<?>> repositoryHandlerMap = new HashMap<>();
    private final Map<String, GitHubMessageHandler<?>> organizationHandlerMap = new HashMap<>();
    private final Map<String, GitHubMessageHandler<?>> installationHandlerMap = new HashMap<>();
    private final SyncSchedulerProperties syncSchedulerProperties;

    public GitHubMessageHandlerRegistry(
        GitHubMessageHandler<?>[] handlers,
        SyncSchedulerProperties syncSchedulerProperties
    ) {
        this.syncSchedulerProperties = syncSchedulerProperties;
        for (GitHubMessageHandler<?> handler : handlers) {
            String eventKey = normalize(handler.getEventType().getValue());
            registerHandler(handler.getDomain(), eventKey, handler);
            handler.getAdditionalDomains().forEach(domain -> registerHandler(domain, eventKey, handler));
        }
    }

    private void registerHandler(
        GitHubMessageHandler.GitHubMessageDomain domain,
        String eventKey,
        GitHubMessageHandler<?> handler
    ) {
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalStateException(handler.getClass().getSimpleName() + " must declare an event key");
        }
        switch (domain) {
            case ORGANIZATION -> organizationHandlerMap.put(eventKey, handler);
            case INSTALLATION -> installationHandlerMap.put(eventKey, handler);
            case REPOSITORY -> repositoryHandlerMap.put(eventKey, handler);
        }
    }

    public GitHubMessageHandler<?> getHandler(String eventKey) {
        String normalized = normalize(eventKey);
        var handler = repositoryHandlerMap.get(normalized);
        if (handler == null) {
            handler = organizationHandlerMap.get(normalized);
        }
        if (handler == null) {
            handler = installationHandlerMap.get(normalized);
        }
        if (handler != null && isFeatureDisabled(handler.getEventType())) {
            // Project/discussion sync is off — treat as no handler so NatsConsumerService
            // acks and skips the event (same as any unhandled webhook type).
            return null;
        }
        return handler;
    }

    private boolean isFeatureDisabled(GitHubEventType eventType) {
        if (PROJECT_EVENTS.contains(eventType)) {
            return !syncSchedulerProperties.projects().enabled();
        }
        if (DISCUSSION_EVENTS.contains(eventType)) {
            return !syncSchedulerProperties.discussions().enabled();
        }
        return false;
    }

    public List<String> getSupportedRepositoryEvents() {
        return new ArrayList<>(repositoryHandlerMap.keySet());
    }

    public List<String> getSupportedOrganizationEvents() {
        return new ArrayList<>(organizationHandlerMap.keySet());
    }

    public List<String> getSupportedInstallationEvents() {
        return new ArrayList<>(installationHandlerMap.keySet());
    }

    private String normalize(String eventKey) {
        return eventKey == null ? null : eventKey.trim().toLowerCase(Locale.ENGLISH);
    }
}
