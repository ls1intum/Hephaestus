package de.tum.in.www1.hephaestus.monitoring;

import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Filters which workspaces and repositories are actively monitored.
 * <p>
 * <strong>Purpose:</strong> In development, you don't want to sync hundreds of repositories
 * from every GitHub App installation. This filter lets you focus on specific orgs/repos
 * while keeping all workspace metadata intact.
 * <p>
 * <strong>How it works:</strong>
 * <ul>
 *   <li>If both filter lists are empty → everything is allowed (production behavior)</li>
 *   <li>If org filter is set → only workspaces with matching accountLogin are monitored</li>
 *   <li>If repo filter is set → only matching repositories are synced</li>
 * </ul>
 *
 * @see MonitoringFilterProperties
 */
@Component
public class MonitoringScopeFilter {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringScopeFilter.class);

    private final Set<String> allowedOrganizations;
    private final Set<String> allowedRepositories;

    public MonitoringScopeFilter(MonitoringFilterProperties properties) {
        this.allowedOrganizations = normalizeSet(properties.getAllowedOrganizations());
        this.allowedRepositories = normalizeSet(properties.getAllowedRepositories());
    }

    @PostConstruct
    void logConfiguration() {
        if (isActive()) {
            logger.info(
                "Monitoring scope filter ACTIVE: allowed-organizations={}, allowed-repositories={}",
                allowedOrganizations,
                allowedRepositories
            );
        } else {
            logger.info("Monitoring scope filter INACTIVE: all workspaces and repositories will be synced.");
        }
    }

    public boolean isWorkspaceAllowed(Workspace workspace) {
        if (!isActive()) {
            return true;
        }
        if (workspace == null) {
            return false;
        }
        return allowedOrganizations.isEmpty() || allowedOrganizations.contains(normalize(workspace.getAccountLogin()));
    }

    public boolean isRepositoryAllowed(RepositoryToMonitor repository) {
        return isRepositoryAllowed(repository != null ? repository.getNameWithOwner() : null);
    }

    public boolean isRepositoryAllowed(String nameWithOwner) {
        if (allowedRepositories.isEmpty()) {
            return true;
        }
        return nameWithOwner != null && allowedRepositories.contains(normalize(nameWithOwner));
    }

    public boolean isOrganizationAllowed(String login) {
        if (allowedOrganizations.isEmpty()) {
            return true;
        }
        return allowedOrganizations.contains(normalize(login));
    }

    public boolean isActive() {
        return !(allowedOrganizations.isEmpty() && allowedRepositories.isEmpty());
    }

    private Set<String> normalizeSet(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return values
            .stream()
            .map(this::normalize)
            .filter(s -> s != null && !s.isEmpty())
            .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ENGLISH);
    }
}
