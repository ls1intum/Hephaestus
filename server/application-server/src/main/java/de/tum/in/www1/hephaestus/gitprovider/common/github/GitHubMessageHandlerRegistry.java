package de.tum.in.www1.hephaestus.gitprovider.common.github;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.kohsuke.github.GHEvent;
import org.springframework.stereotype.Component;

@Component
public class GitHubMessageHandlerRegistry {

    private final Map<GHEvent, GitHubMessageHandler<?>> repositoryHandlerMap = new HashMap<>();
    private final Map<GHEvent, GitHubMessageHandler<?>> organizationHandlerMap = new HashMap<>();
    private final Map<GHEvent, GitHubMessageHandler<?>> installationHandlerMap = new HashMap<>();
    private final Map<String, GitHubMessageHandler<?>> customRepositoryHandlerMap = new HashMap<>();
    private final Map<String, GitHubMessageHandler<?>> customOrganizationHandlerMap = new HashMap<>();
    private final Map<String, GitHubMessageHandler<?>> customInstallationHandlerMap = new HashMap<>();

    public GitHubMessageHandlerRegistry(GitHubMessageHandler<?>[] handlers) {
        for (GitHubMessageHandler<?> handler : handlers) {
            // register primary domain
            switch (handler.getDomain()) {
                case ORGANIZATION -> {
                    organizationHandlerMap.put(handler.getHandlerEvent(), handler);
                    registerCustomEvent(handler, handler.getCustomEventName(), customOrganizationHandlerMap);
                }
                case INSTALLATION -> {
                    installationHandlerMap.put(handler.getHandlerEvent(), handler);
                    registerCustomEvent(handler, handler.getCustomEventName(), customInstallationHandlerMap);
                }
                case REPOSITORY -> {
                    repositoryHandlerMap.put(handler.getHandlerEvent(), handler);
                    registerCustomEvent(handler, handler.getCustomEventName(), customRepositoryHandlerMap);
                }
            }
            // register any additional domains for the same event
            for (var domain : handler.getAdditionalDomains()) {
                switch (domain) {
                    case ORGANIZATION -> {
                        organizationHandlerMap.put(handler.getHandlerEvent(), handler);
                        registerCustomEvent(handler, handler.getCustomEventName(), customOrganizationHandlerMap);
                    }
                    case INSTALLATION -> {
                        installationHandlerMap.put(handler.getHandlerEvent(), handler);
                        registerCustomEvent(handler, handler.getCustomEventName(), customInstallationHandlerMap);
                    }
                    case REPOSITORY -> {
                        repositoryHandlerMap.put(handler.getHandlerEvent(), handler);
                        registerCustomEvent(handler, handler.getCustomEventName(), customRepositoryHandlerMap);
                    }
                }
            }
        }
    }

    private void registerCustomEvent(
        GitHubMessageHandler<?> handler,
        String eventName,
        Map<String, GitHubMessageHandler<?>> targetMap
    ) {
        if (eventName == null || eventName.isBlank()) {
            return;
        }
        targetMap.put(normalize(eventName), handler);
    }

    private String normalize(String eventName) {
        return eventName.toLowerCase(Locale.ROOT);
    }

    public GitHubMessageHandler<?> getHandler(GHEvent eventType) {
        var handler = repositoryHandlerMap.get(eventType);
        if (handler != null) {
            return handler;
        }
        handler = organizationHandlerMap.get(eventType);
        if (handler != null) {
            return handler;
        }
        return installationHandlerMap.get(eventType);
    }

    public GitHubMessageHandler<?> getCustomHandler(String rawEvent) {
        if (rawEvent == null) {
            return null;
        }
        String key = normalize(rawEvent);
        GitHubMessageHandler<?> handler = customRepositoryHandlerMap.get(key);
        if (handler != null) {
            return handler;
        }
        handler = customOrganizationHandlerMap.get(key);
        if (handler != null) {
            return handler;
        }
        return customInstallationHandlerMap.get(key);
    }

    public List<GHEvent> getSupportedRepositoryEvents() {
        return new ArrayList<>(repositoryHandlerMap.keySet());
    }

    public List<String> getSupportedCustomRepositoryEvents() {
        return new ArrayList<>(customRepositoryHandlerMap.keySet());
    }

    public List<GHEvent> getSupportedOrganizationEvents() {
        return new ArrayList<>(organizationHandlerMap.keySet());
    }

    public List<GHEvent> getSupportedInstallationEvents() {
        return new ArrayList<>(installationHandlerMap.keySet());
    }
}
