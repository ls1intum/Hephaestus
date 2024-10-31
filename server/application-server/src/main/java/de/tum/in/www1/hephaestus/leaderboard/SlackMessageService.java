package de.tum.in.www1.hephaestus.leaderboard;

import java.io.IOException;
import java.util.List;

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

    @Scheduled(cron = "${slack.cronSchedule}")
    public void sendScheduledLeaderboard() {
        if (!runScheduledMessage) {
            return;
        }
        logger.info("Sending scheduled message to Slack channel...");
        var blocks = asBlocks(
                header(header -> header.text(
                        plainText(pt -> pt.text(":newspaper:  Developer Reviews of the last week  :newspaper:")))),
                divider(),
                context(context -> context
                        .elements(List.of(markdownText("*October 31, 2024*  |  Hephaestus Announcement")))),
                section(section -> section.text(markdownText(
                        "Here is the *review table* summary for the last week. The table is taken directly from <https://hephaestus.ase.cit.tum.de/|Hephaestus> and now also includes non-review comments & code comments in addition to all variants of reviews for each developer. The score is a weighted heuristic based on the complexities of the reviewed PRs (similar to the old algorithm)."))),
                section(section -> section.text(markdownText(
                        "If you would like to check out more leaderboards or statistics, head over to last week's leaderboard or this month's leaderboard."))));
        try {
            sendMessage(channelId, blocks, "Developer Reviews of the last week");
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