package de.tum.in.www1.hephaestus.leaderboard;

import java.io.IOException;
import java.time.LocalDate;
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

    private List<LeaderboardEntryDTO> getTop3SlackReviewers() {
        LocalDate after = LocalDate.now().minusDays(8);
        LocalDate before = LocalDate.now().minusDays(1);
        var leaderboard = leaderboardService.createLeaderboard(Optional.of(after), Optional.of(before),
                Optional.empty());
        return leaderboard.subList(0, Math.min(3, leaderboard.size()));
    }

    @Scheduled(cron = "${slack.cronSchedule}")
    public void sendScheduledLeaderboard() {
        if (!runScheduledMessage) {
            return;
        }

        var top3reviewers = getTop3SlackReviewers();
        logger.info("Top 3 Accounts of the last week: " + top3reviewers);

        logger.info("Sending scheduled message to Slack channel...");
        List<LayoutBlock> blocks = asBlocks(
                header(header -> header.text(
                        plainText(pt -> pt.text(":newspaper: Developer Reviews of the last week :newspaper:")))),
                divider(),
                context(context -> context
                        .elements(List.of(markdownText("*October 31, 2024* | Hephaestus Announcement")))),
                section(section -> section.text(markdownText(
                        "Another *review leaderboard* has concluded. You can check out your placement at <https://hephaestus.ase.cit.tum.de/|hephaestus.ase.cit.tum.de>. The score is a weighted heuristic based on the complexities of the reviewed PRs (similar to the old algorithm)."))),
                section(section -> section.text(markdownText(
                        "If you would like to check out more leaderboards or statistics, head over to last week's leaderboard or this month's leaderboard."))),
                section(section -> section.text(markdownText(
                        "The top 3 reviewers of the last week are:"))),
                section(section -> section.text(markdownText(
                        IntStream.range(0, top3reviewers.size())
                                .mapToObj(i -> ("• *" + (i + 1) + ". @" + top3reviewers.get(i).user().name() + " *"))
                                .reduce((a, b) -> a + "\n" + b).orElse(""))))

        );
        try {
            sendMessage(channelId, blocks, "Developer Reviews of the last week");
        } catch (IOException | SlackApiException e) {
            logger.error("Failed to send scheduled message to Slack channel: " +
                    e.getMessage());
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