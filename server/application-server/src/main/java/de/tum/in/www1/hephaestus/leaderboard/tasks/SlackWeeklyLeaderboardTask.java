package de.tum.in.www1.hephaestus.leaderboard.tasks;

import static com.slack.api.model.block.Blocks.asBlocks;
import static com.slack.api.model.block.Blocks.context;
import static com.slack.api.model.block.Blocks.divider;
import static com.slack.api.model.block.Blocks.header;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;

import com.slack.api.methods.SlackApiException;
import com.slack.api.model.User;
import com.slack.api.model.block.LayoutBlock;
import de.tum.in.www1.hephaestus.config.ApplicationProperties;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.leaderboard.*;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Task to send a weekly leaderboard message to the Slack channel.
 *
 * @see SlackMessageService
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.leaderboard.notification", name = "enabled", havingValue = "true")
public class SlackWeeklyLeaderboardTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(SlackWeeklyLeaderboardTask.class);

    private final LeaderboardProperties leaderboardProperties;
    private final ApplicationProperties applicationProperties;
    private final SlackMessageService slackMessageService;
    private final LeaderboardService leaderboardService;
    private final WorkspaceRepository workspaceRepository;

    public SlackWeeklyLeaderboardTask(
        LeaderboardProperties leaderboardProperties,
        ApplicationProperties applicationProperties,
        @Autowired(required = false) SlackMessageService slackMessageService,
        LeaderboardService leaderboardService,
        WorkspaceRepository workspaceRepository
    ) {
        this.leaderboardProperties = leaderboardProperties;
        this.applicationProperties = applicationProperties;
        this.slackMessageService = slackMessageService;
        this.leaderboardService = leaderboardService;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Test the Slack connection.
     *
     * @return {@code true} if the connection is valid, {@code false} otherwise.
     */
    public boolean testSlackConnection() {
        return (
            leaderboardProperties.notification().enabled() &&
            slackMessageService != null &&
            slackMessageService.initTest()
        );
    }

    /**
     * Gets the Slack handles of the top 3 reviewers in the given time frame.
     *
     * @return
     */
    private List<User> getTop3SlackReviewers(
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
            top3
                .stream()
                .map(entry -> entry.user() != null ? entry.user().name() : "<team>")
                .toList()
        );

        List<User> allSlackUsers = slackMessageService != null ? slackMessageService.getAllMembers() : List.of();
        return top3
            .stream()
            .map(mapToSlackUser(allSlackUsers))
            .filter(user -> user != null)
            .toList();
    }

    private Function<LeaderboardEntryDTO, User> mapToSlackUser(List<User> allSlackUsers) {
        return entry -> {
            UserInfoDTO leaderboardUser = entry.user();
            if (leaderboardUser == null) {
                return null;
            }

            var exactUser = allSlackUsers
                .stream()
                .filter(
                    user ->
                        user.getName().equalsIgnoreCase(leaderboardUser.name()) ||
                        (user.getProfile().getEmail() != null &&
                            user.getProfile().getEmail().equalsIgnoreCase(leaderboardUser.email()))
                )
                .findFirst();
            if (exactUser.isPresent()) {
                return exactUser.get();
            }

            // find through String edit distance
            return allSlackUsers
                .stream()
                .min((a, b) -> {
                    String aName = a.getRealName() != null ? a.getRealName() : a.getName();
                    String bName = b.getRealName() != null ? b.getRealName() : b.getName();

                    return Integer.compare(
                        LevenshteinDistance.getDefaultInstance().apply(leaderboardUser.name(), aName),
                        LevenshteinDistance.getDefaultInstance().apply(leaderboardUser.name(), bName)
                    );
                })
                .orElse(null);
        };
    }

    private String formatDateForURL(Instant instant) {
        // Use ISO-8601 for query params (e.g., 2025-01-01T00:00:00Z)
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    @Override
    public void run() {
        if (slackMessageService == null) {
            log.warn("Skipped Slack notification: reason=serviceUnavailable");
            return;
        }

        List<Workspace> workspaces = workspaceRepository.findAll();
        if (workspaces.isEmpty()) {
            log.info("Skipped Slack notification: reason=noWorkspacesConfigured");
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

        String team = leaderboardProperties.notification().team();
        String channelId = leaderboardProperties.notification().channelId();

        for (Workspace workspace : workspaces) {
            var topReviewers = getTop3SlackReviewers(workspace, after, before, Optional.ofNullable(team));
            if (topReviewers.isEmpty()) {
                log.info("Skipped Slack notification: reason=noQualifiedReviewers, workspaceId={}", workspace.getId());
                continue;
            }

            List<LayoutBlock> blocks = buildBlocks(workspace, topReviewers, currentDate, after, before, team);
            try {
                slackMessageService.sendMessage(channelId, blocks, "Weekly review highlights");
            } catch (IOException | SlackApiException e) {
                log.error("Failed to send scheduled Slack message: workspaceId={}", workspace.getId(), e);
            }
        }
    }

    private List<LayoutBlock> buildBlocks(
        Workspace workspace,
        List<User> topReviewers,
        long currentDate,
        Instant after,
        Instant before,
        String team
    ) {
        final String baseUrl = normalizeBaseUrl(applicationProperties.hostUrl());
        final String workspaceBase = baseUrl + "/w/" + workspace.getWorkspaceSlug();
        String teamFilter = team == null ? "all" : team;
        return asBlocks(
            header(header -> header.text(plainText(pt -> pt.text(":newspaper: Weekly review highlights :newspaper:")))),
            context(context ->
                context.elements(
                    List.of(
                        markdownText(
                            "<!date^" + currentDate + "^{date} at {time}| Today at 9:00AM CEST> | " + workspaceBase
                        )
                    )
                )
            ),
            divider(),
            section(section ->
                section.text(
                    markdownText(
                        "Last week's review leaderboard is finalized. See where you landed <" +
                            workspaceBase +
                            "?after=" +
                            encode(formatDateForURL(after)) +
                            "&before=" +
                            encode(formatDateForURL(before)) +
                            "&team=" +
                            encode(teamFilter) +
                            "|here>."
                    )
                )
            ),
            section(section -> section.text(markdownText("Congrats to last week's top 3 reviewers:"))),
            section(section ->
                section.text(
                    markdownText(
                        IntStream.range(0, topReviewers.size())
                            .mapToObj(i -> ((i + 1) + ". <@" + topReviewers.get(i).getId() + ">"))
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse("")
                    )
                )
            ),
            section(section -> section.text(markdownText("Thanks for keeping reviews moving! :rocket:")))
        );
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

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
