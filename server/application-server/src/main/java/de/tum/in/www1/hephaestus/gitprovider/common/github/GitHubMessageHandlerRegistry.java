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

    public GitHubMessageHandlerRegistry(GitHubMessageHandler<?>[] handlers) {
        for (GitHubMessageHandler<?> handler : handlers) {
            switch (handler.getDomain()) {
                case ORGANIZATION -> organizationHandlerMap.put(handler.getHandlerEvent(), handler);
                case INSTALLATION -> installationHandlerMap.put(handler.getHandlerEvent(), handler);
                case REPOSITORY -> repositoryHandlerMap.put(handler.getHandlerEvent(), handler);
            }
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

    public List<GHEvent> getSupportedRepositoryEvents() {
        return new ArrayList<>(repositoryHandlerMap.keySet());
    }

    public List<GHEvent> getSupportedOrganizationEvents() {
        return new ArrayList<>(organizationHandlerMap.keySet());
    }

    public List<GHEvent> getSupportedInstallationEvents() {
        return new ArrayList<>(installationHandlerMap.keySet());
    }
}
