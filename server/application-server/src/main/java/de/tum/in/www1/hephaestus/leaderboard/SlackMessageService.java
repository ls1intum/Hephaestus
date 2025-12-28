package de.tum.in.www1.hephaestus.leaderboard;

import com.slack.api.bolt.App;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.auth.AuthTestResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.User;
import com.slack.api.model.block.LayoutBlock;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Light wrapper around the Slack App to send messages to the Slack workspace.
 * @implNote Use the exposed method to test the Slack connection beforehand.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.leaderboard.notification", name = "enabled", havingValue = "true")
public class SlackMessageService {

    private static final Logger logger = LoggerFactory.getLogger(SlackMessageService.class);

    private final App slackApp;

    public SlackMessageService(App slackApp) {
        this.slackApp = slackApp;
    }

    /**
     * Gets all members of the Slack workspace.
     * @return
     */
    public List<User> getAllMembers() {
        try {
            return slackApp.client().usersList(r -> r).getMembers();
        } catch (IOException | SlackApiException e) {
            logger.error("Failed to get all members from Slack: " + e.getMessage());
            return new ArrayList<>();
        }
    }

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
     * Tests if the Slack app is correctly initialized and has access to the workspace.
     * Does not guarantee that the app has the necessary permissions to send messages.
     */
    public boolean initTest() {
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
            return true;
        } else {
            logger.error("Failed to initialize Slack app: " + response.getError());
            return false;
        }
    }
}
