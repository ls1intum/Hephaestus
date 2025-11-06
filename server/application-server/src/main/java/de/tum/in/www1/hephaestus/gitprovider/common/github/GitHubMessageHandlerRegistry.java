package de.tum.in.www1.hephaestus.gitprovider.common.github;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.kohsuke.github.GHEvent;
import org.springframework.stereotype.Component;

@Component
public class GitHubMessageHandlerRegistry {

    private final Map<GHEvent, GitHubMessageHandler<?>> repositoryHandlerMap = new EnumMap<>(GHEvent.class);
    private final Map<GHEvent, GitHubMessageHandler<?>> organizationHandlerMap = new EnumMap<>(GHEvent.class);
    private final Map<GHEvent, GitHubMessageHandler<?>> installationHandlerMap = new EnumMap<>(GHEvent.class);
    private final Map<String, GitHubMessageHandler<?>> customSubjectHandlerMap = new HashMap<>();
    private final Map<GitHubMessageHandler.GitHubMessageDomain, List<String>> domainSubjectOverrides = new EnumMap<>(
        GitHubMessageHandler.GitHubMessageDomain.class
    );

    public GitHubMessageHandlerRegistry(GitHubMessageHandler<?>[] handlers) {
        for (GitHubMessageHandler<?> handler : handlers) {
            registerHandler(handler, handler.getDomain());
            handler.getAdditionalDomains().forEach(domain -> registerHandler(handler, domain));
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

    public GitHubMessageHandler<?> getHandlerBySubject(String subjectSuffix) {
        if (subjectSuffix == null) {
            return null;
        }
        String normalized = subjectSuffix.toLowerCase();
        var customHandler = customSubjectHandlerMap.get(normalized);
        if (customHandler != null) {
            return customHandler;
        }
        try {
            GHEvent event = GHEvent.valueOf(normalized.toUpperCase());
            return getHandler(event);
        } catch (IllegalArgumentException ex) {
            return null;
        }
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

    public List<String> getCustomRepositorySubjects() {
        return getCustomSubjectsForDomain(GitHubMessageHandler.GitHubMessageDomain.REPOSITORY);
    }

    public List<String> getCustomOrganizationSubjects() {
        return getCustomSubjectsForDomain(GitHubMessageHandler.GitHubMessageDomain.ORGANIZATION);
    }

    public List<String> getCustomInstallationSubjects() {
        return getCustomSubjectsForDomain(GitHubMessageHandler.GitHubMessageDomain.INSTALLATION);
    }

    private List<String> getCustomSubjectsForDomain(GitHubMessageHandler.GitHubMessageDomain domain) {
        var subjects = domainSubjectOverrides.get(domain);
        if (subjects == null) {
            return List.of();
        }
        return List.copyOf(subjects);
    }

    private void registerHandler(GitHubMessageHandler<?> handler, GitHubMessageHandler.GitHubMessageDomain domain) {
        GHEvent event = handler.getHandlerEvent();
        if (event != null && event != GHEvent.UNKNOWN) {
            switch (domain) {
                case ORGANIZATION -> organizationHandlerMap.put(event, handler);
                case INSTALLATION -> installationHandlerMap.put(event, handler);
                case REPOSITORY -> repositoryHandlerMap.put(event, handler);
            }
        }
        registerSubjectOverride(handler, domain);
    }

    private void registerSubjectOverride(
        GitHubMessageHandler<?> handler,
        GitHubMessageHandler.GitHubMessageDomain domain
    ) {
        String subjectSuffix = handler.getSubjectSuffix();
        if (subjectSuffix == null || subjectSuffix.isBlank()) {
            return;
        }
        subjectSuffix = subjectSuffix.toLowerCase();

        String defaultSuffix = handler.getHandlerEvent() != null
            ? handler.getHandlerEvent().name().toLowerCase()
            : null;
        if (!Objects.equals(subjectSuffix, defaultSuffix)) {
            customSubjectHandlerMap.put(subjectSuffix, handler);
            List<String> overrides = domainSubjectOverrides.computeIfAbsent(domain, key -> new ArrayList<>());
            if (!overrides.contains(subjectSuffix)) {
                overrides.add(subjectSuffix);
            }
        }
    }
}
