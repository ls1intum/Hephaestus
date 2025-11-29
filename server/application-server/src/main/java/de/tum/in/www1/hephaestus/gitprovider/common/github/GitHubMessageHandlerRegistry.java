package de.tum.in.www1.hephaestus.gitprovider.common.github;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GitHubMessageHandlerRegistry {

    private final Map<String, GitHubMessageHandler<?>> repositoryHandlerMap = new HashMap<>();
    private final Map<String, GitHubMessageHandler<?>> organizationHandlerMap = new HashMap<>();
    private final Map<String, GitHubMessageHandler<?>> installationHandlerMap = new HashMap<>();

    public GitHubMessageHandlerRegistry(GitHubMessageHandler<?>[] handlers) {
        for (GitHubMessageHandler<?> handler : handlers) {
            String eventKey = normalize(handler.getEventKey());
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
        if (handler != null) {
            return handler;
        }
        handler = organizationHandlerMap.get(normalized);
        if (handler != null) {
            return handler;
        }
        return installationHandlerMap.get(normalized);
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
