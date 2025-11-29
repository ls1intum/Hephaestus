package de.tum.in.www1.hephaestus.monitoring;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for limiting which organizations and repositories are monitored.
 * <p>
 * <strong>Use case:</strong> During local development, you typically don't want to sync ALL
 * repositories from ALL installations. These filters let you focus on specific orgs/repos.
 * <p>
 * <strong>Behavior:</strong>
 * <ul>
 *   <li>Empty lists = no filtering (monitor everything)</li>
 *   <li>Non-empty lists = only monitor matching orgs/repos</li>
 *   <li>Organization filter: matches workspace account login (case-insensitive)</li>
 *   <li>Repository filter: matches nameWithOwner like "org/repo" (case-insensitive)</li>
 * </ul>
 * <p>
 * <strong>Example configuration:</strong>
 * <pre>
 * monitoring:
 *   filters:
 *     allowed-organizations:
 *       - ls1intum
 *       - HephaestusTest
 *     allowed-repositories:
 *       - ls1intum/Hephaestus
 *       - ls1intum/Artemis
 *       - HephaestusTest/demo-repository
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "monitoring.filters")
public class MonitoringFilterProperties {

    /**
     * Limit monitoring to workspaces owned by these organizations.
     * Empty list means all organizations are allowed.
     */
    private List<String> allowedOrganizations = new ArrayList<>();

    /**
     * Limit monitoring to these specific repositories (format: owner/repo).
     * Empty list means all repositories are allowed.
     */
    private List<String> allowedRepositories = new ArrayList<>();

    public List<String> getAllowedOrganizations() {
        return allowedOrganizations;
    }

    public void setAllowedOrganizations(List<String> allowedOrganizations) {
        this.allowedOrganizations = allowedOrganizations != null ? allowedOrganizations : new ArrayList<>();
    }

    public List<String> getAllowedRepositories() {
        return allowedRepositories;
    }

    public void setAllowedRepositories(List<String> allowedRepositories) {
        this.allowedRepositories = allowedRepositories != null ? allowedRepositories : new ArrayList<>();
    }
}
