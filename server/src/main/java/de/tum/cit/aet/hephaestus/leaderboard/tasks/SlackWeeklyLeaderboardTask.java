package de.tum.cit.aet.hephaestus.leaderboard.tasks;

import de.tum.cit.aet.hephaestus.config.ApplicationProperties;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardEntryDTO;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardMode;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardNotificationTask;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardScheduleResolver;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardService;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardSortType;
import de.tum.cit.aet.hephaestus.leaderboard.spi.LeaderboardDigestReadyEvent;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Weekly Slack digest — leaderboard-side half, per workspace.
 *
 * <p>The {@code LeaderboardTaskScheduler} invokes {@link #runForWorkspace(Workspace)} on the
 * workspace's own scheduled cron tick. This task owns data assembly: resolve the workspace's ACTIVE
 * Slack connection config, compute its weekly window (via {@link LeaderboardScheduleResolver}),
 * fetch its top-3 leaderboard entries, and publish a vendor-neutral
 * {@link LeaderboardDigestReadyEvent}. The actual Slack publish lives in
 * {@code integration/slack/leaderboard/}, so this leaderboard-module class imports no Slack types.
 *
 * <p>Skip preconditions stay here (notifications-off, no active Slack connection, no channel
 * configured, empty leaderboard) — when the assembled payload would be empty no event is emitted.
 *
 * <p>Gated on {@code hephaestus.integration.slack.enabled}: when off, the bean is absent and the
 * scheduler simply has no Slack notification task to run.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackWeeklyLeaderboardTask implements LeaderboardNotificationTask {

    private static final Logger log = LoggerFactory.getLogger(SlackWeeklyLeaderboardTask.class);

    private final ApplicationProperties applicationProperties;
    private final LeaderboardService leaderboardService;
    private final ConnectionService connectionService;
    private final LeaderboardScheduleResolver scheduleResolver;
    private final ApplicationEventPublisher eventPublisher;

    public SlackWeeklyLeaderboardTask(
        ApplicationProperties applicationProperties,
        LeaderboardService leaderboardService,
        ConnectionService connectionService,
        LeaderboardScheduleResolver scheduleResolver,
        ApplicationEventPublisher eventPublisher
    ) {
        this.applicationProperties = applicationProperties;
        this.leaderboardService = leaderboardService;
        this.connectionService = connectionService;
        this.scheduleResolver = scheduleResolver;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void runForWorkspace(Workspace workspace) {
        if (workspace == null || workspace.getId() == null) {
            return;
        }
        long workspaceId = workspace.getId();

        if (!Boolean.TRUE.equals(workspace.getLeaderboardNotificationEnabled())) {
            log.debug("Skipped Slack notification: reason=disabled, workspaceId={}", workspaceId);
            return;
        }

        Optional<ConnectionConfig.SlackConfig> slackConfig = connectionService.findSlackNotificationConfig(workspaceId);
        if (slackConfig.isEmpty()) {
            log.info("Skipped Slack notification: reason=noActiveSlackConnection, workspaceId={}", workspaceId);
            return;
        }
        String channelId = slackConfig.get().notificationChannelId();
        if (channelId == null || channelId.isBlank()) {
            log.info("Skipped Slack notification: reason=noChannelConfigured, workspaceId={}", workspaceId);
            return;
        }

        Optional<String> team = Optional.ofNullable(slackConfig.get().teamLabel()).filter(t -> !t.isBlank());
        LeaderboardScheduleResolver.CycleWindow window = scheduleResolver.previousCycleWindow(workspace);
        List<LeaderboardEntryDTO> topEntries = fetchTopEntries(workspace, window, team);
        if (topEntries.isEmpty()) {
            log.info("Skipped Slack notification: reason=noQualifiedReviewers, workspaceId={}", workspaceId);
            return;
        }

        eventPublisher.publishEvent(
            new LeaderboardDigestReadyEvent(
                workspaceId,
                workspace.getWorkspaceSlug(),
                channelId,
                team.orElse(null),
                Instant.now().getEpochSecond(),
                window.after(),
                window.before(),
                topEntries,
                normalizeBaseUrl(applicationProperties.hostUrl())
            )
        );
    }

    private List<LeaderboardEntryDTO> fetchTopEntries(
        Workspace workspace,
        LeaderboardScheduleResolver.CycleWindow window,
        Optional<String> team
    ) {
        var leaderboard = leaderboardService.createLeaderboard(
            workspace,
            window.after(),
            window.before(),
            team.orElse("all"),
            LeaderboardSortType.SCORE,
            LeaderboardMode.INDIVIDUAL
        );
        var top3 = leaderboard.subList(0, Math.min(3, leaderboard.size()));
        log.debug(
            "Fetched top reviewers: workspaceId={}, userCount={}, users={}",
            workspace.getId(),
            top3.size(),
            top3
                .stream()
                .map(entry -> entry.user() != null ? entry.user().name() : "<team>")
                .toList()
        );
        return top3;
    }

    private String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String trimmed = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }
}
