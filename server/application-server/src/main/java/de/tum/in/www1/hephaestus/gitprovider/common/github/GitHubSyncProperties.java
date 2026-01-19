package de.tum.in.www1.hephaestus.gitprovider.common.github;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for GitHub synchronization operations.
 * <p>
 * These properties control various aspects of the sync behavior including:
 * <ul>
 *   <li>GraphQL operation timeouts</li>
 *   <li>Incremental sync settings</li>
 * </ul>
 * <p>
 * <strong>Example configuration:</strong>
 * <pre>
 * hephaestus:
 *   sync:
 *     graphql-timeout-seconds: 60
 *     extended-graphql-timeout-seconds: 120
 *     incremental-sync-enabled: true
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "hephaestus.sync")
public class GitHubSyncProperties {

    /**
     * Default timeout for GraphQL operations in seconds.
     * <p>
     * Used when blocking on reactive GraphQL client responses.
     * Default: 30 seconds
     */
    private int graphqlTimeoutSeconds = 30;

    /**
     * Extended timeout for complex GraphQL operations in seconds.
     * <p>
     * Used for operations that may take longer, such as syncing review comments
     * with nested thread structures.
     * Default: 60 seconds
     */
    private int extendedGraphqlTimeoutSeconds = 60;

    /**
     * Whether incremental sync is enabled.
     * <p>
     * When enabled, sync operations will use the last sync timestamp to only
     * fetch items that have been updated since the last sync.
     * Default: true
     */
    private boolean incrementalSyncEnabled = true;

    public int getGraphqlTimeoutSeconds() {
        return graphqlTimeoutSeconds;
    }

    public void setGraphqlTimeoutSeconds(int graphqlTimeoutSeconds) {
        this.graphqlTimeoutSeconds = graphqlTimeoutSeconds;
    }

    public int getExtendedGraphqlTimeoutSeconds() {
        return extendedGraphqlTimeoutSeconds;
    }

    public void setExtendedGraphqlTimeoutSeconds(int extendedGraphqlTimeoutSeconds) {
        this.extendedGraphqlTimeoutSeconds = extendedGraphqlTimeoutSeconds;
    }

    public boolean isIncrementalSyncEnabled() {
        return incrementalSyncEnabled;
    }

    public void setIncrementalSyncEnabled(boolean incrementalSyncEnabled) {
        this.incrementalSyncEnabled = incrementalSyncEnabled;
    }

    /**
     * Gets the GraphQL timeout as a Duration.
     *
     * @return the timeout duration
     */
    public Duration getGraphqlTimeout() {
        return Duration.ofSeconds(graphqlTimeoutSeconds);
    }

    /**
     * Gets the extended GraphQL timeout as a Duration.
     *
     * @return the extended timeout duration
     */
    public Duration getExtendedGraphqlTimeout() {
        return Duration.ofSeconds(extendedGraphqlTimeoutSeconds);
    }
}
