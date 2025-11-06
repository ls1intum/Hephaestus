package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.installation.Installation;
import de.tum.in.www1.hephaestus.gitprovider.installationtarget.InstallationTarget;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHPermissionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubInstallationConverter extends BaseGitServiceEntityConverter<GHAppInstallation, Installation> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationConverter.class);

    @Override
    public Installation convert(@NonNull GHAppInstallation source) {
        return update(source, new Installation());
    }

    @Override
    public Installation update(@NonNull GHAppInstallation source, @NonNull Installation target) {
        convertBaseFields(source, target);

        target.setAppId(source.getAppId());
        target.setAccessTokensUrl(source.getAccessTokenUrl());
        target.setRepositoriesUrl(source.getRepositoriesUrl());
        target.setHtmlUrl(source.getHtmlUrl() != null ? source.getHtmlUrl().toString() : null);
        target.setTargetGithubId(source.getTargetId());
        target.setTargetType(resolveTargetType(source));
        target.setRepositorySelection(Installation.RepositorySelection.fromSymbol(resolveRepositorySelection(source)));
        target.setSingleFileName(source.getSingleFileName());
        target.setSuspendedAt(source.getSuspendedAt());

        var events = ensureSet(target.getSubscribedEvents());
        events.clear();
        if (source.getEvents() != null) {
            source.getEvents().forEach(event -> events.add(event.name().toLowerCase()));
        }
        target.setSubscribedEvents(events);

        var permissions = ensureMap(target.getPermissions());
        permissions.clear();
        if (source.getPermissions() != null) {
            source.getPermissions().forEach((name, permission) -> permissions.put(name, mapPermission(permission)));
        }
        target.setPermissions(permissions);

        return target;
    }

    private InstallationTarget.TargetType resolveTargetType(GHAppInstallation source) {
        try {
            var targetType = source.getTargetType();
            return targetType != null
                ? InstallationTarget.TargetType.fromValue(targetType.name())
                : InstallationTarget.TargetType.UNKNOWN;
        } catch (Exception e) {
            logger.error("Failed to read installation target type for {}: {}", source.getId(), e.getMessage());
            return InstallationTarget.TargetType.UNKNOWN;
        }
    }

    private String resolveRepositorySelection(GHAppInstallation source) {
        try {
            var selection = source.getRepositorySelection();
            return selection != null ? selection.name().toLowerCase() : null;
        } catch (Exception e) {
            logger.error("Failed to read repository selection for {}: {}", source.getId(), e.getMessage());
            return null;
        }
    }

    private Installation.PermissionLevel mapPermission(GHPermissionType permission) {
        if (permission == null) {
            return Installation.PermissionLevel.UNKNOWN;
        }
        return switch (permission) {
            case ADMIN -> Installation.PermissionLevel.ADMIN;
            case WRITE -> Installation.PermissionLevel.WRITE;
            case READ -> Installation.PermissionLevel.READ;
            case NONE -> Installation.PermissionLevel.NONE;
            default -> Installation.PermissionLevel.UNKNOWN;
        };
    }

    private Set<String> ensureSet(Set<String> value) {
        return value != null ? value : new HashSet<>();
    }

    private Map<String, Installation.PermissionLevel> ensureMap(Map<String, Installation.PermissionLevel> value) {
        return value != null ? value : new HashMap<>();
    }
}
