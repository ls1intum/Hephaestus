package de.tum.cit.aet.hephaestus.integration.slack.messaging;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.auth.AuthTestResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.model.User;
import com.slack.api.model.block.LayoutBlock;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.slack.credentials.SlackCredentialProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Per-workspace Slack messaging. Bot token resolved at send time via
 * {@link SlackCredentialProvider}; failures throw {@link SlackSendException} carrying
 * the Slack error code so callers can choose the HTTP mapping.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackMessageService {

    private static final Logger log = LoggerFactory.getLogger(SlackMessageService.class);

    private static final int USERS_LIST_PAGE_SIZE = 1000;
    private static final int USERS_LIST_MAX_PAGES = 50; // hard cap: 50_000 users is well above any realistic workspace

    private final Slack slack;
    private final SlackCredentialProvider credentialProvider;

    public SlackMessageService(SlackCredentialProvider credentialProvider) {
        this.slack = Slack.getInstance();
        this.credentialProvider = credentialProvider;
    }

    public boolean initTest(long workspaceId) {
        Optional<String> token = resolveToken(workspaceId);
        if (token.isEmpty()) {
            log.debug("Slack init test skipped: no token for workspaceId={}", workspaceId);
            return false;
        }
        try {
            AuthTestResponse response = slack.methods(token.get()).authTest(r -> r);
            if (response.isOk()) {
                return true;
            }
            log.warn("Slack auth.test failed: workspaceId={}, error={}", workspaceId, response.getError());
            return false;
        } catch (SlackApiException | IOException e) {
            log.warn("Slack auth.test transport failure: workspaceId={}, error={}", workspaceId, e.getMessage());
            return false;
        }
    }

    public void sendForWorkspace(long workspaceId, String channelId, List<LayoutBlock> blocks, String fallback) {
        String token = resolveToken(workspaceId).orElseThrow(() ->
            new SlackSendException(workspaceId, channelId, "no_active_slack_connection")
        );
        ChatPostMessageRequest request = ChatPostMessageRequest.builder()
            .channel(channelId)
            .blocks(blocks)
            .text(fallback) // fallback text shown in notifications + accessibility tools
            .build();
        try {
            ChatPostMessageResponse response = slack.methods(token).chatPostMessage(request);
            if (!response.isOk()) {
                String error = response.getError() == null ? "unknown" : response.getError();
                log.warn(
                    "Slack chat.postMessage failed: workspaceId={}, channelId={}, error={}",
                    workspaceId,
                    channelId,
                    error
                );
                throw new SlackSendException(workspaceId, channelId, error);
            }
        } catch (SlackApiException | IOException e) {
            log.warn(
                "Slack chat.postMessage transport failure: workspaceId={}, channelId={}, error={}",
                workspaceId,
                channelId,
                e.getMessage()
            );
            throw new SlackSendException(workspaceId, channelId, "transport_failure", e);
        }
    }

    public List<User> listMembers(long workspaceId) {
        Optional<String> token = resolveToken(workspaceId);
        if (token.isEmpty()) {
            log.debug("Slack listMembers skipped: no token for workspaceId={}", workspaceId);
            return List.of();
        }
        MethodsClient methods = slack.methods(token.get());
        List<User> accumulator = new ArrayList<>();
        String cursor = "";
        int pages = 0;
        try {
            do {
                final String pageCursor = cursor;
                UsersListResponse response = methods.usersList(r -> r.limit(USERS_LIST_PAGE_SIZE).cursor(pageCursor));
                if (!response.isOk()) {
                    log.warn(
                        "Slack users.list returned ok=false: workspaceId={}, error={}",
                        workspaceId,
                        response.getError()
                    );
                    return accumulator;
                }
                if (response.getMembers() != null) {
                    accumulator.addAll(response.getMembers());
                }
                cursor = response.getResponseMetadata() == null ? null : response.getResponseMetadata().getNextCursor();
                pages++;
            } while (cursor != null && !cursor.isBlank() && pages < USERS_LIST_MAX_PAGES);
            if (pages >= USERS_LIST_MAX_PAGES) {
                log.warn(
                    "Slack users.list pagination hit cap: workspaceId={}, pages={}, members={}",
                    workspaceId,
                    pages,
                    accumulator.size()
                );
            }
            return accumulator;
        } catch (SlackApiException | IOException e) {
            log.warn(
                "Slack users.list transport failure: workspaceId={}, collected={}, error={}",
                workspaceId,
                accumulator.size(),
                e.getMessage()
            );
            return accumulator;
        }
    }

    private Optional<String> resolveToken(long workspaceId) {
        return credentialProvider
            .resolve(
                new de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef(
                    de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.SLACK,
                    workspaceId,
                    null
                )
            )
            .filter(b -> b instanceof BearerToken)
            .map(b -> ((BearerToken) b).token());
    }
}
