package de.tum.in.www1.hephaestus.monitoring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for monitoring and synchronization.
 * <p>
 * <strong>Example configuration:</strong>
 * <pre>
 * monitoring:
 *   run-on-startup: true
 *   timeframe: 7
 *   sync-cron: "0 0 3 * * *"
 *   sync-cooldown-in-minutes: 15
 * </pre>
 * <p>
 * Properties:
 * <ul>
 *   <li>{@code run-on-startup} - Whether to run monitoring sync when the application starts</li>
 *   <li>{@code timeframe} - Number of days to look back for initial sync (before incremental sync is established)</li>
 *   <li>{@code sync-cron} - Cron expression for scheduled sync (used directly in @Scheduled annotations)</li>
 *   <li>{@code sync-cooldown-in-minutes} - Minimum interval between syncs for the same target</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "monitoring")
public class MonitoringProperties {

    /**
     * Whether to run monitoring sync when the application starts.
     * <p>
     * Default: true
     */
    private boolean runOnStartup = true;

    /**
     * Number of days to look back when performing the first sync for a repository.
     * <p>
     * This acts as a fallback when no previous sync timestamp exists, preventing
     * the initial sync from fetching the entire history of issues and pull requests.
     * <p>
     * Default: 7 days
     */
    private int timeframe = 7;

    /**
     * Cron expression for scheduled synchronization.
     * <p>
     * Note: This property is primarily accessed via SpEL in @Scheduled annotations,
     * but is also available here for programmatic access if needed.
     * <p>
     * Default: "0 0 3 * * *" (daily at 3 AM)
     */
    private String syncCron = "0 0 3 * * *";

    /**
     * Minimum interval in minutes between syncs for the same target.
     * <p>
     * Prevents excessive API calls by enforcing a cooldown period between syncs
     * for the same repository or scope.
     * <p>
     * Default: 15 minutes
     */
    private int syncCooldownInMinutes = 15;

    public boolean isRunOnStartup() {
        return runOnStartup;
    }

    public void setRunOnStartup(boolean runOnStartup) {
        this.runOnStartup = runOnStartup;
    }

    public int getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(int timeframe) {
        this.timeframe = timeframe;
    }

    public String getSyncCron() {
        return syncCron;
    }

    public void setSyncCron(String syncCron) {
        this.syncCron = syncCron;
    }

    public int getSyncCooldownInMinutes() {
        return syncCooldownInMinutes;
    }

    public void setSyncCooldownInMinutes(int syncCooldownInMinutes) {
        this.syncCooldownInMinutes = syncCooldownInMinutes;
    }
}
