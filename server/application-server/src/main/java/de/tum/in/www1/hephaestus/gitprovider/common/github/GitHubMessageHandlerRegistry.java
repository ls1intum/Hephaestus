package de.tum.in.www1.hephaestus.gitprovider.common.github;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kohsuke.github.GHEvent;
import org.springframework.stereotype.Component;

@Component
public class GitHubMessageHandlerRegistry {

    private final Map<GHEvent, GitHubMessageHandler<?>> handlerMap = new HashMap<>();

    public GitHubMessageHandlerRegistry(GitHubMessageHandler<?>[] handlers) {
        for (GitHubMessageHandler<?> handler : handlers) {
            handlerMap.put(handler.getHandlerEvent(), handler);
        }
    }

    public GitHubMessageHandler<?> getHandler(GHEvent eventType) {
        return handlerMap.get(eventType);
    }

    public List<GHEvent> getSupportedEvents() {
        return new ArrayList<>(handlerMap.keySet());
    }
}