package de.tum.cit.aet.hephaestus.leaderboard.tasks;

import de.tum.cit.aet.hephaestus.config.ApplicationProperties;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardEntryDTO;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardMode;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardNotificationTask;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardProperties;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardService;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardSortType;
import de.tum.cit.aet.hephaestus.leaderboard.spi.LeaderboardDigestReadyEvent;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Weekly Slack digest fan-out — leaderboard-side half.
 *
 * <p>Owns the schedule + data assembly: enumerates active Slack connections, computes the
 * weekly window, fetches the top-3 leaderboard entries per workspace, and publishes a
 * vendor-neutral {@link LeaderboardDigestReadyEvent} per qualifying workspace-channel
 * target. The actual Slack publish lives in {@code integration/slack/leaderboard/} so
 * this module no longer imports any Slack types.
 *
 * <p>Fan-out is per-target: one workspace → one channel → one event. Skip preconditions
 * (notifications-off, no channel configured, empty leaderboard) remain on the leaderboard
 * side — if the assembled payload would be empty, no event is emitted (preserves the
 * "reason=noQualifiedReviewers" / "reason=disabled" / "reason=noChannelConfigured" early
 * returns of the original code path).
 *
 * <p>NOTE: This task remains a {@link LeaderboardNotificationTask} so the existing cron
 * wiring in {@code LeaderboardTaskScheduler} picks it up unchanged. The class is still
 * gated on {@code hephaestus.integration.slack.enabled} because publishing leaderboard
 * digest events that no subscriber listens to would be wasted work — the gate keeps the
 * cron firing pattern identical to the pre-flip behaviour.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackWeeklyLeaderboardTask implements LeaderboardNotificationTask {

    private static final Logger log = LoggerFactory.getLogger(SlackWeeklyLeaderboardTask.class);

    private final LeaderboardProperties leaderboardProperties;
    private final ApplicationProperties applicationProperties;
    private final LeaderboardService leaderboardService;
    private final ConnectionRepository connectionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SlackWeeklyLeaderboardTask(
        LeaderboardProperties leaderboardProperties,
        ApplicationProperties applicationProperties,
        LeaderboardService leaderboardService,
        ConnectionRepository connectionRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.leaderboardProperties = leaderboardProperties;
        this.applicationProperties = applicationProperties;
        this.leaderboardService = leaderboardService;
        this.connectionRepository = connectionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void run() {
        List<Connection> connections = connectionRepository.findByKindAndStateWithWorkspace(
            IntegrationKind.SLACK,
            IntegrationState.ACTIVE
        );
        if (connections.isEmpty()) {
            log.info("Skipped Slack notification: reason=noActiveSlackConnections");
            return;
        }

        long currentDate = Instant.now().getEpochSecond();
        String[] timeParts = leaderboardProperties.schedule().time().split(":");
        ZonedDateTime zonedNow = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime zonedBefore = zonedNow
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.of(leaderboardProperties.schedule().day())))
            .withHour(Integer.parseInt(timeParts[0]))
            .withMinute(timeParts.length > 1 ? Integer.parseInt(timeParts[1]) : 0)
            .withSecond(0)
            .withNano(0);
        Instant before = zonedBefore.toInstant();
        Instant after = zonedBefore.minusWeeks(1).toInstant();
        String baseUrl = normalizeBaseUrl(applicationProperties.hostUrl());

        for (Connection connection : connections) {
            // Skip-then-iterate; one workspace's failure must NOT block the next.
            try {
                publishForConnection(connection, currentDate, after, before, baseUrl);
            } catch (RuntimeException e) {
                log.warn(
                    "Slack notification fan-out failed for workspace={}: {}",
                    connection.getWorkspace() == null ? null : connection.getWorkspace().getId(),
                    e.getMessage()
                );
            }
        }
    }

    private void publishForConnection(
        Connection connection,
        long currentDate,
        Instant after,
        Instant before,
        String baseUrl
    ) {
        Workspace workspace = connection.getWorkspace();
        if (workspace == null) {
            log.warn("Slack Connection {} has no workspace — skipping", connection.getId());
            return;
        }
        if (!Boolean.TRUE.equals(workspace.getLeaderboardNotificationEnabled())) {
            log.debug("Skipped Slack notification: reason=disabled, workspaceId={}", workspace.getId());
            return;
        }
        // Cast is safe: the repo query filters by IntegrationKind.SLACK, so the config
        // variant is always SlackConfig. (Cross-variant returns are guarded by
        // ConnectionService.updateConfig.)
        ConnectionConfig.SlackConfig slackConfig = (ConnectionConfig.SlackConfig) connection.getConfig();
        String channelId = slackConfig.notificationChannelId();
        if (channelId == null || channelId.isBlank()) {
            log.info("Skipped Slack notification: reason=noChannelConfigured, workspaceId={}", workspace.getId());
            return;
        }

        Optional<String> team = Optional.ofNullable(slackConfig.teamLabel()).filter(t -> !t.isBlank());
        List<LeaderboardEntryDTO> topEntries = fetchTopEntries(workspace, after, before, team);
        if (topEntries.isEmpty()) {
            log.info("Skipped Slack notification: reason=noQualifiedReviewers, workspaceId={}", workspace.getId());
            return;
        }

        eventPublisher.publishEvent(
            new LeaderboardDigestReadyEvent(
                workspace.getId(),
                workspace.getWorkspaceSlug(),
                channelId,
                team.orElse(null),
                currentDate,
                after,
                before,
                topEntries,
                baseUrl
            )
        );
    }

    private List<LeaderboardEntryDTO> fetchTopEntries(
        Workspace workspace,
        Instant after,
        Instant before,
        Optional<String> team
    ) {
        if (workspace == null || workspace.getId() == null) {
            return List.of();
        }
        var leaderboard = leaderboardService.createLeaderboard(
            workspace,
            after,
            before,
            team.orElse("all"),
            LeaderboardSortType.SCORE,
            LeaderboardMode.INDIVIDUAL
        );
        var top3 = leaderboard.subList(0, Math.min(3, leaderboard.size()));
        log.debug(
            "Fetched top reviewers: workspaceId={}, userCount={}, users={}",
            workspace.getId(),
            top3.size(),
            top3.stream().map(entry -> entry.user() != null ? entry.user().name() : "<team>").toList()
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
