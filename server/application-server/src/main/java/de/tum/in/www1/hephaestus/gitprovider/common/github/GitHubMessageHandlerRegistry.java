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

    public GitHubMessageHandlerRegistry(GitHubMessageHandler<?>[] handlers) {
        for (GitHubMessageHandler<?> handler : handlers) {
            if (handler.isOrganizationEvent()) {
                organizationHandlerMap.put(handler.getHandlerEvent(), handler);
            } else {
                repositoryHandlerMap.put(handler.getHandlerEvent(), handler);
            }
        }
    }

    public GitHubMessageHandler<?> getHandler(GHEvent eventType) {
        return repositoryHandlerMap.getOrDefault(eventType, organizationHandlerMap.get(eventType));
    }

    public List<GHEvent> getSupportedRepositoryEvents() {
        return new ArrayList<>(repositoryHandlerMap.keySet());
    }

    public List<GHEvent> getSupportedOrganizationEvents() {
        return new ArrayList<>(organizationHandlerMap.keySet());
    }
}
