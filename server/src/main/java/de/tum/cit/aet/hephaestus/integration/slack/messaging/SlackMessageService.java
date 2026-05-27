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
 * Per-workspace Slack messaging.
 *
 * <p>Replaces the legacy global-token wrapper that lived under {@code leaderboard/}: every
 * outbound call now resolves the bot token from the workspace's ACTIVE Slack
 * {@link de.tum.cit.aet.hephaestus.integration.core.connection.Connection} via
 * {@link SlackCredentialProvider}, then builds a one-shot {@link MethodsClient}. The
 * underlying {@link Slack} singleton is thread-safe and pools its HTTP client; reusing
 * it across workspaces is the documented Slack-SDK pattern.
 *
 * <p>Errors surface as {@link SlackSendException} with the Slack error code so callers
 * can map (channel_not_found → 404, not_in_channel → 400, rate_limited → 429) without
 * peeking at tokens. {@link #listMembers} swallows pagination errors and returns what
 * it has — best-effort, used only for Slack-handle resolution in the leaderboard task.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackMessageService {

    private static final Logger log = LoggerFactory.getLogger(SlackMessageService.class);

    private static final int USERS_LIST_PAGE_SIZE = 1000;
    private static final int USERS_LIST_MAX_PAGES = 50; // hard cap: 50_000 users is well above any realistic workspace

    /**
     * Singleton {@link Slack} instance. The Slack SDK explicitly documents this as
     * thread-safe + connection-pooled — building a new instance per call would leak
     * threads. Holding a single static reference matches the SDK's intended usage.
     */
    private final Slack slack;
    private final SlackCredentialProvider credentialProvider;

    public SlackMessageService(SlackCredentialProvider credentialProvider) {
        this.slack = Slack.getInstance();
        this.credentialProvider = credentialProvider;
    }

    /**
     * Verifies that the workspace has a usable Slack bot token (active Connection,
     * decryptable bundle, and Slack-side {@code auth.test} succeeds). Returns false
     * — does not throw — so the caller can choose to skip the workspace silently.
     */
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

    /**
     * Post a {@code chat.postMessage} to {@code channelId} using the workspace's bot
     * token. Throws if no token, or if Slack rejects the request.
     */
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

    /**
     * Returns the bot token's visible users (paginated via {@code cursor}). Returns the
     * accumulated set on transport failure rather than throwing — the leaderboard task
     * degrades to "no Slack mention" rather than skipping the post entirely.
     */
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
