package de.tum.in.www1.hephaestus.gitprovider.common.github;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kohsuke.github.GHEvent;
import org.springframework.stereotype.Component;

@Component
public class GitHubMessageHandlerRegistry {

    private final Map<GHEvent, GitHubMessageHandler<?>> repositoryHandlerMap = new HashMap<>();
    private final Map<GHEvent, GitHubMessageHandler<?>> organizationHandlerMap = new HashMap<>();
    private final Map<GHEvent, GitHubMessageHandler<?>> installationHandlerMap = new HashMap<>();
    private final Map<String, GitHubMessageHandler<?>> repositoryCustomHandlerMap = new HashMap<>();
    private final Map<String, GitHubMessageHandler<?>> organizationCustomHandlerMap = new HashMap<>();
    private final Map<String, GitHubMessageHandler<?>> installationCustomHandlerMap = new HashMap<>();

    public GitHubMessageHandlerRegistry(GitHubMessageHandler<?>[] handlers) {
        for (GitHubMessageHandler<?> handler : handlers) {
            registerHandlerForDomain(handler.getDomain(), handler);
            handler.getAdditionalDomains().forEach(domain -> registerHandlerForDomain(domain, handler));
        }
    }

    private void registerHandlerForDomain(
        GitHubMessageHandler.GitHubMessageDomain domain,
        GitHubMessageHandler<?> handler
    ) {
        switch (domain) {
            case ORGANIZATION -> registerHandler(handler, organizationHandlerMap, organizationCustomHandlerMap);
            case INSTALLATION -> registerHandler(handler, installationHandlerMap, installationCustomHandlerMap);
            case REPOSITORY -> registerHandler(handler, repositoryHandlerMap, repositoryCustomHandlerMap);
        }
    }

    private void registerHandler(
        GitHubMessageHandler<?> handler,
        Map<GHEvent, GitHubMessageHandler<?>> eventMap,
        Map<String, GitHubMessageHandler<?>> customEventMap
    ) {
        if (handler.getCustomEventType() != null) {
            customEventMap.put(handler.getCustomEventType().toLowerCase(), handler);
        }

        var event = handler.getHandlerEvent();
        if (event != GHEvent.ALL) {
            eventMap.put(event, handler);
        }
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

    public GitHubMessageHandler<?> getCustomHandler(String eventType) {
        var normalized = eventType.toLowerCase();
        var handler = repositoryCustomHandlerMap.get(normalized);
        if (handler != null) {
            return handler;
        }
        handler = organizationCustomHandlerMap.get(normalized);
        if (handler != null) {
            return handler;
        }
        return installationCustomHandlerMap.get(normalized);
    }

    public List<GHEvent> getSupportedRepositoryEvents() {
        return repositoryHandlerMap.keySet().stream().filter(event -> event != GHEvent.ALL).toList();
    }

    public List<GHEvent> getSupportedOrganizationEvents() {
        return organizationHandlerMap.keySet().stream().filter(event -> event != GHEvent.ALL).toList();
    }

    public List<GHEvent> getSupportedInstallationEvents() {
        return installationHandlerMap.keySet().stream().filter(event -> event != GHEvent.ALL).toList();
    }

    public List<String> getSupportedRepositoryCustomEvents() {
        return new ArrayList<>(repositoryCustomHandlerMap.keySet());
    }

    public List<String> getSupportedOrganizationCustomEvents() {
        return new ArrayList<>(organizationCustomHandlerMap.keySet());
    }

    public List<String> getSupportedInstallationCustomEvents() {
        return new ArrayList<>(installationCustomHandlerMap.keySet());
    }
}
