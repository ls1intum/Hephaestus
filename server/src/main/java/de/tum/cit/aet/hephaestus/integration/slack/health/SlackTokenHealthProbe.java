package de.tum.cit.aet.hephaestus.integration.slack.health;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic {@code auth.test} liveness probe for every ACTIVE Slack connection (S9). Slack bot tokens can be revoked
 * out-of-band (workspace admin removes the app, org policy change) without Hephaestus hearing about it on any of its
 * own paths; this sweep catches a dead token and suspends the connection so downstream sends fail fast instead of
 * silently.
 *
 * <p>The actual Slack round-trip lives behind {@link SlackAuthLivenessClient} and is LIVE-only — the default client
 * returns {@link SlackAuthLivenessClient.Liveness#UNKNOWN}, so this builds and exercises the enumeration + call site
 * without any network dependency. When a live client reports {@code REVOKED}, the connection is transitioned to
 * {@link IntegrationState#SUSPENDED} (the token-rotation seam, {@link SlackAuthLivenessClient#rotateToken}, is a
 * no-op for Slack's non-rotating legacy bot tokens).
 *
 * <p>Enumeration is a workspace-agnostic raw read (the caller has no single workspace in hand), mirroring
 * {@code SlackWorkspaceResolver}. Scheduling is gated to the server role (the only role with
 * {@code @EnableScheduling}) and cross-pod races are held off by {@link SchedulerLock}, matching
 * {@code SlackRetentionSweeper}.
 */
@ConditionalOnServerRole
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
@WorkspaceAgnostic("Enumerates Slack connections fleet-wide to probe token liveness")
public class SlackTokenHealthProbe {

    private static final Logger log = LoggerFactory.getLogger(SlackTokenHealthProbe.class);

    private final JdbcTemplate jdbc;
    private final SlackAuthLivenessClient livenessClient;
    private final ConnectionService connectionService;

    public SlackTokenHealthProbe(
        JdbcTemplate jdbc,
        SlackAuthLivenessClient livenessClient,
        ConnectionService connectionService
    ) {
        this.jdbc = jdbc;
        this.livenessClient = livenessClient;
        this.connectionService = connectionService;
    }

    @Scheduled(cron = "0 20 */6 * * *")
    @SchedulerLock(name = "slack-token-health-probe", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void probe() {
        probeNow();
    }

    /**
     * Probe every ACTIVE Slack connection once and suspend any whose token Slack reports revoked.
     *
     * @return the number of connections probed
     */
    public int probeNow() {
        List<Long> workspaceIds = jdbc.queryForList(
            "SELECT workspace_id FROM connection WHERE kind = 'SLACK' AND state = 'ACTIVE'",
            Long.class
        );
        for (Long workspaceId : workspaceIds) {
            try {
                SlackAuthLivenessClient.Liveness liveness = livenessClient.authTest(workspaceId);
                if (liveness == SlackAuthLivenessClient.Liveness.REVOKED) {
                    suspendRevoked(workspaceId);
                }
            } catch (RuntimeException e) {
                // Isolate a poisoned connection — log and keep probing the rest.
                log.warn("slack.health: probe failed for workspaceId={}: {}", workspaceId, e.toString());
            }
        }
        return workspaceIds.size();
    }

    private void suspendRevoked(long workspaceId) {
        connectionService
            .findActive(workspaceId, IntegrationKind.SLACK)
            .ifPresent(connection -> {
                connectionService.transition(
                    connection,
                    new ConnectionService.TransitionRequest(
                        IntegrationState.SUSPENDED,
                        "TOKEN_REVOKED",
                        "SYSTEM",
                        "slack-health-probe",
                        "slack-health-" + workspaceId + "-" + connection.getId(),
                        "auth.test reported the Slack bot token as revoked"
                    )
                );
                log.warn("slack.health: suspended Slack connection for workspaceId={} (token revoked)", workspaceId);
            });
    }
}
