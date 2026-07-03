package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorSlackThreadService;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.slack.retention.SlackWorkspacePurgeAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routes inbound Slack {@code app_uninstalled} / {@code tokens_revoked} events into a clean teardown. These
 * arrive on {@code POST /slack/events} and were previously dropped by the controller's non-{@code message}
 * early-return, so a workspace that removed the app kept its ingested Slack content and a now-dead bot token.
 *
 * <p>The teardown, in one transaction: flip the Slack {@link Connection} to {@link IntegrationState#UNINSTALLED}
 * (which clears the stored credentials via {@link ConnectionService#transition}) <em>then</em> drop the Slack
 * content through {@link SlackWorkspacePurgeAdapter} <em>and</em> erase the Slack-originated mentor DM content
 * (the {@code SLACK_DM} {@code chat_thread}/{@code chat_message} rows) via
 * {@link MentorSlackThreadService#purgeSlackThreads} — the five {@code slack_*} tables alone leave the derived
 * mentor conversation behind, which an uninstall (a common GDPR-erasure trigger) should also remove. Ordering the
 * connection flip before/with the purge mirrors the workspace-purge chain, where {@code SlackWorkspacePurgeAdapter}
 * ({@code @Order -200}) already runs ahead of {@code ConnectionPurgeContributor} ({@code -100}).
 *
 * <p>The audit row's stable {@code correlationId} makes a Slack retry idempotent: the second delivery's transition
 * is a no-op and the content-purge is already empty.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
@WorkspaceAgnostic("Resolves the workspace from the Slack team_id in the uninstall payload, not from a scoped read")
public class SlackUninstallService {

    private static final Logger log = LoggerFactory.getLogger(SlackUninstallService.class);

    private final SlackWorkspaceResolver workspaceResolver;
    private final ConnectionService connectionService;
    private final SlackWorkspacePurgeAdapter purgeAdapter;
    private final MentorSlackThreadService mentorSlackThreadService;

    public SlackUninstallService(
        SlackWorkspaceResolver workspaceResolver,
        ConnectionService connectionService,
        SlackWorkspacePurgeAdapter purgeAdapter,
        MentorSlackThreadService mentorSlackThreadService
    ) {
        this.workspaceResolver = workspaceResolver;
        this.connectionService = connectionService;
        this.purgeAdapter = purgeAdapter;
        this.mentorSlackThreadService = mentorSlackThreadService;
    }

    /**
     * Handle an uninstall/revocation for the Slack {@code teamId}. No-op (logged) if no ACTIVE Slack connection
     * maps to the team — the app is already gone, or a retry already tore it down.
     *
     * @param eventType the Slack event type that triggered this ({@code app_uninstalled} or {@code tokens_revoked})
     */
    @Transactional
    public void onUninstall(String teamId, String eventType) {
        var workspaceOpt = workspaceResolver.resolveWorkspaceId(teamId);
        if (workspaceOpt.isEmpty()) {
            log.info("Slack {} for team {} — no ACTIVE connection, nothing to tear down", eventType, teamId);
            return;
        }
        long workspaceId = workspaceOpt.get();
        connectionService
            .findActive(workspaceId, IntegrationKind.SLACK)
            .ifPresent(connection ->
                connectionService.transition(
                    connection,
                    new ConnectionService.TransitionRequest(
                        IntegrationState.UNINSTALLED,
                        "APP_UNINSTALLED".equals(eventType) || "app_uninstalled".equals(eventType)
                            ? "APP_UNINSTALLED"
                            : "TOKENS_REVOKED",
                        "SLACK",
                        teamId,
                        "slack-uninstall-" + teamId,
                        "Slack " + eventType + " received"
                    )
                )
            );
        purgeAdapter.deleteWorkspaceData(workspaceId);
        // Also erase the derived Slack-originated mentor DM conversation (SLACK_DM chat_thread/chat_message rows);
        // the slack_* tables alone would leave it behind. Idempotent, so a Slack uninstall redelivery is a no-op.
        int purgedThreads = mentorSlackThreadService.purgeSlackThreads(workspaceId);
        log.info(
            "Slack {} for team {} → workspace {} torn down (connection UNINSTALLED, content purged, {} mentor DM threads erased)",
            eventType,
            teamId,
            workspaceId,
            purgedThreads
        );
    }
}
