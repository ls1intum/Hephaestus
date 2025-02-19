package de.tum.in.www1.hephaestus.leaderboard.tasks;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;

import com.slack.api.methods.SlackApiException;
import com.slack.api.model.User;
import com.slack.api.model.block.LayoutBlock;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardEntryDTO;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardService;
import de.tum.in.www1.hephaestus.leaderboard.SlackMessageService;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Task to send a weekly leaderboard message to the Slack channel.
 * @see SlackMessageService
 */
@Component
public class SlackWeeklyLeaderboardTask implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SlackWeeklyLeaderboardTask.class);

    @Value("${hephaestus.leaderboard.notification.channel-id}")
    private String channelId;

    @Value("${hephaestus.leaderboard.notification.enabled}")
    private boolean runScheduledMessage;

    @Value("${hephaestus.host-url:localhost:8080}")
    private String hephaestusUrl;

    @Value("${hephaestus.leaderboard.schedule.day}")
    private String scheduledDay;

    @Value("${hephaestus.leaderboard.schedule.time}")
    private String scheduledTime;

    @Autowired
    private SlackMessageService slackMessageService;

    @Autowired
    private LeaderboardService leaderboardService;

    /**
     * Test the Slack connection.
     * @return {@code true} if the connection is valid, {@code false} otherwise.
     */
    public boolean testSlackConnection() {
        return runScheduledMessage && slackMessageService.initTest();
    }

    /**
     * Gets the Slack handles of the top 3 reviewers in the given time frame.
     * @return
     */
    private List<User> getTop3SlackReviewers(OffsetDateTime after, OffsetDateTime before) {
        var leaderboard = leaderboardService.createLeaderboard(after, before, Optional.empty());
        var top3 = leaderboard.subList(0, Math.min(3, leaderboard.size()));
        logger.debug("Top 3 Users of the last week: " + top3.stream().map(e -> e.user().name()).toList());

        List<User> allSlackUsers = slackMessageService.getAllMembers();

        return top3.stream().map(mapToSlackUser(allSlackUsers)).filter(user -> user != null).toList();
    }

    private Function<LeaderboardEntryDTO, User> mapToSlackUser(List<User> allSlackUsers) {
        return entry -> {
            var exactUser = allSlackUsers
                .stream()
                .filter(
                    user ->
                        user.getName().equals(entry.user().name()) ||
                        (user.getProfile().getEmail() != null &&
                            user.getProfile().getEmail().equals(entry.user().email()))
                )
                .findFirst();
            if (exactUser.isPresent()) {
                return exactUser.get();
            }

            // find through String edit distance
            return allSlackUsers
                .stream()
                .min((a, b) ->
                    Integer.compare(
                        LevenshteinDistance.getDefaultInstance().apply(entry.user().name(), a.getName()),
                        LevenshteinDistance.getDefaultInstance().apply(entry.user().name(), b.getName())
                    )
                )
                .orElse(null);
        };
    }

    private String formatDateForURL(OffsetDateTime date) {
        return date.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME).replace("+", "%2B");
    }

    @Override
    public void run() {
        // get date in unix format
        long currentDate = OffsetDateTime.now().toEpochSecond();
        // Calculate the the last leaderboard schedule
        String[] timeParts = scheduledTime.split(":");
        OffsetDateTime before = OffsetDateTime.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.of(Integer.parseInt(scheduledDay))))
            .withHour(Integer.parseInt(timeParts[0]))
            .withMinute(timeParts.length > 1 ? Integer.parseInt(timeParts[1]) : 0)
            .withSecond(0)
            .withNano(0);
        OffsetDateTime after = before.minusWeeks(1);

        var top3reviewers = getTop3SlackReviewers(after, before);

        logger.info("Sending scheduled message to Slack channel...");

        List<LayoutBlock> blocks = asBlocks(
            header(header -> header.text(plainText(pt -> pt.text(":newspaper: Reviews of the last week :newspaper:")))),
            context(context ->
                context.elements(
                    List.of(
                        markdownText(
                            "<!date^" + currentDate + "^{date} at {time}| Today at 9:00AM CEST> | " + hephaestusUrl
                        )
                    )
                )
            ),
            divider(),
            section(section ->
                section.text(
                    markdownText(
                        "Another *review leaderboard* has concluded. You can check out your placement <" +
                        hephaestusUrl +
                        "?after=" +
                        formatDateForURL(after) +
                        "&before=" +
                        formatDateForURL(before) +
                        "|here>."
                    )
                )
            ),
            section(section -> section.text(markdownText("Special thanks to our top 3 reviewers of last week:"))),
            section(section ->
                section.text(
                    markdownText(
                        IntStream.range(0, top3reviewers.size())
                            .mapToObj(i -> ((i + 1) + ". <@" + top3reviewers.get(i).getId() + ">"))
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse("")
                    )
                )
            ),
            section(section -> section.text(markdownText("Happy coding and reviewing! :rocket:")))
        );
        try {
            slackMessageService.sendMessage(channelId, blocks, "Reviews of the last week");
        } catch (IOException | SlackApiException e) {
            logger.error("Failed to send scheduled message to Slack channel: " + e.getMessage());
        }
    }
}
