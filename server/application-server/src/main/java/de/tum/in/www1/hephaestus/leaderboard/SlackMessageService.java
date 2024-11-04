package de.tum.in.www1.hephaestus.leaderboard;

import java.io.IOException;
import java.time.LocalDate;
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

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;

@Order(value = Ordered.LOWEST_PRECEDENCE)
@EnableScheduling
@Service
public class SlackMessageService {

    private static final Logger logger = LoggerFactory.getLogger(SlackMessageService.class);

    @Value("${slack.channelId}")
    private String channelId;

    @Value("${slack.runScheduledMessage}")
    private boolean runScheduledMessage;

    @Value("${spring.url:localhost:8080}")
    private String hephaestusUrl;

    @Autowired
    private App slackApp;

    @Autowired
    private LeaderboardService leaderboardService;

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

    private List<User> getTop3SlackReviewers() {
        LocalDate after = LocalDate.now().minusDays(8);
        LocalDate before = LocalDate.now().minusDays(2);
        var leaderboard = leaderboardService.createLeaderboard(Optional.of(after), Optional.of(before),
                Optional.empty());
        var top3 = leaderboard.subList(0, Math.min(3, leaderboard.size()));

        List<User> allSlackUsers;
        try {
            allSlackUsers = slackApp.client().usersList(r -> r).getMembers();
        } catch (SlackApiException | IOException e) {
            logger.error("Failed to get Slack users: " + e.getMessage());
            return new ArrayList<>();
        }

        return top3.stream().map(entry -> allSlackUsers.stream()
                .filter(user -> user.getName().equals(entry.user().name())).findFirst().orElse(null))
                .filter(user -> user != null).toList();
    }

    @Scheduled(cron = "${slack.cronSchedule}")
    public void sendScheduledLeaderboard() {
        if (!runScheduledMessage) {
            return;
        }

        // get date in format October 31, 2024
        var currentDate = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy"));

        var top3reviewers = getTop3SlackReviewers();
        // logger.info("Top 3 Accounts of the last week: " + top3reviewers);

        logger.info("Sending scheduled message to Slack channel...");
        List<LayoutBlock> blocks = asBlocks(
                header(header -> header.text(
                        plainText(pt -> pt.text(":newspaper: Reviews of the last week :newspaper:")))),
                divider(),
                context(context -> context
                        .elements(List.of(markdownText("*" + currentDate + "*")))),
                section(section -> section.text(markdownText(
                        "Another *review leaderboard* has concluded. You can check out your placement at "
                                + hephaestusUrl
                                + "."))),
                section(section -> section.text(markdownText(
                        "Special thanks to our top 3 reviewers of last week:"))),
                section(section -> section.text(markdownText(
                        IntStream.range(0, top3reviewers.size())
                                .mapToObj(i -> ("• *" + (i + 1) + ". @" + top3reviewers.get(i).getTeamId() + " *"))
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