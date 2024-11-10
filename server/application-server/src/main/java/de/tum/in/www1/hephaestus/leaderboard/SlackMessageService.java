package de.tum.in.www1.hephaestus.leaderboard;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.slack.api.bolt.App;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.auth.AuthTestResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.User;
import com.slack.api.model.block.LayoutBlock;
import org.apache.commons.text.similarity.LevenshteinDistance;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;

@Order(value = Ordered.LOWEST_PRECEDENCE)
@EnableScheduling
@Service
public class SlackMessageService {

    private static final Logger logger = LoggerFactory.getLogger(SlackMessageService.class);

    @Value("${hephaestus.notification.leaderboard.channelId}")
    private String channelId;

    @Value("${hephaestus.notification.leaderboard.enabled}")
    private boolean runScheduledMessage;

    @Value("${hephaestus.host_url:localhost:8080}")
    private String hephaestusUrl;

    @Autowired
    private App slackApp;

    @Autowired
    private LeaderboardService leaderboardService;

    /**
     * Sends a message to the specified Slack channel.
     * @param channelId Slack channel ID
     * @param blocks message blocks
     * @param fallback used for example in notifications
     * @throws IOException
     * @throws SlackApiException
     */
    public void sendMessage(String channelId, List<LayoutBlock> blocks, String fallback)
            throws IOException, SlackApiException {
        ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(channelId)
                .blocks(blocks)
                .text(fallback) // used for example in notifications
                .build();

        ChatPostMessageResponse response = slackApp.client().chatPostMessage(request);

        if (!response.isOk()) {
            logger.error("Failed to send message to Slack channel: " + response.getError());
        }
    }

    /**
     * Gets the Slack handles of the top 3 reviewers of the last week.
     * @return
     */
    private List<User> getTop3SlackReviewers() {
        // exactly 7 days ago
        OffsetDateTime after = OffsetDateTime.of(LocalDate.now().minusDays(7), OffsetDateTime.now().toLocalTime(),
                OffsetDateTime.now().getOffset());
        var leaderboard = leaderboardService.createLeaderboard(after, OffsetDateTime.now(), Optional.empty());
        var top3 = leaderboard.subList(0, Math.min(3, leaderboard.size()));
        logger.debug("Top 3 Users of the last week: " + top3.stream().map(e -> e.user().name()).toList());

        List<User> allSlackUsers;
        try {
            allSlackUsers = slackApp.client().usersList(r -> r).getMembers();
        } catch (SlackApiException | IOException e) {
            logger.error("Failed to get Slack users: " + e.getMessage());
            return new ArrayList<>();
        }

        return top3.stream().map(entry -> {
            var exactUser = allSlackUsers.stream()
                .filter(user -> user.getName().equals(entry.user().name()) || (user.getProfile().getEmail() != null && user.getProfile().getEmail().equals(entry.user().email()))).findFirst();
            if (exactUser.isPresent()) {
                return exactUser.get();
            }

            // find through String edit distance
            return allSlackUsers.stream()
                .min((a, b) -> Integer.compare(
                    LevenshteinDistance.getDefaultInstance().apply(
                        entry.user().name(), a.getName()),
                    LevenshteinDistance.getDefaultInstance().apply(
                        entry.user().name(), b.getName()))).orElse(null);
        }).filter(user -> user != null).toList();
    }

    @Scheduled(cron = "${hephaestus.notification.leaderboard.cron}")
    public void sendScheduledLeaderboard() {
        if (!runScheduledMessage) {
            return;
        }

        // get date in unix format
        var currentDate = OffsetDateTime.now().toEpochSecond();

        var top3reviewers = getTop3SlackReviewers();

        logger.info("Sending scheduled message to Slack channel...");
        List<LayoutBlock> blocks = asBlocks(
                header(header -> header.text(
                        plainText(pt -> pt.text(":newspaper: Reviews of the last week :newspaper:")))),
                context(context -> context
                        .elements(List.of(markdownText("<!date^" + currentDate + "^{date} at {time}| Today at 9:00AM CEST> | " + hephaestusUrl)))),
                divider(),
                section(section -> section.text(markdownText(
                        "Another *review leaderboard* has concluded. You can check out your placement <" + hephaestusUrl + "|here>."))),
                section(section -> section.text(markdownText(
                        "Special thanks to our top 3 reviewers of last week:"))),
                section(section -> section.text(markdownText(
                        IntStream.range(0, top3reviewers.size())
                                .mapToObj(i -> ((i + 1) + ". <@" + top3reviewers.get(i).getId() + ">"))
                                .reduce((a, b) -> a + "\n" + b).orElse("")))),
                section(section -> section.text(markdownText("Happy coding and reviewing! :rocket:")))
        );
        try {
            sendMessage(channelId, blocks, "Reviews of the last week");
        } catch (IOException | SlackApiException e) {
            logger.error("Failed to send scheduled message to Slack channel: " + e.getMessage());
        }
    }

    /**
     * Test the Slack app initialization on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        if (!runScheduledMessage) {
            logger.info("Slack scheduled messages are disabled, skipping Slack app init test.");
            return;
        }
        logger.info("Testing Slack app initialization...");
        AuthTestResponse response;
        try {
            response = slackApp.client().authTest(r -> r);
        } catch (SlackApiException | IOException e) {
            response = new AuthTestResponse();
            response.setOk(false);
            response.setError(e.getMessage());
        }
        if (response.isOk()) {
            logger.info("Slack app is successfully initialized.");
        } else {
            logger.error("Failed to initialize Slack app: " + response.getError());
        }
    }
}