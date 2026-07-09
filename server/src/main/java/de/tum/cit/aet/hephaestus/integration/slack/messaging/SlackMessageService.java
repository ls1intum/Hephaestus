package de.tum.cit.aet.hephaestus.integration.slack.messaging;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostEphemeralRequest;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.auth.AuthTestResponse;
import com.slack.api.methods.response.chat.ChatAppendStreamResponse;
import com.slack.api.methods.response.chat.ChatPostEphemeralResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatStartStreamResponse;
import com.slack.api.methods.response.chat.ChatStopStreamResponse;
import com.slack.api.methods.response.conversations.ConversationsInfoResponse;
import com.slack.api.methods.response.conversations.ConversationsJoinResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.methods.response.views.ViewsPublishResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.ConversationType;
import com.slack.api.model.User;
import com.slack.api.model.assistant.SuggestedPrompt;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.view.View;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.slack.credentials.SlackCredentialProvider;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;
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
    private static final int CONVERSATIONS_LIST_PAGE_SIZE = 200;
    private static final int CONVERSATIONS_LIST_MAX_PAGES = 20;

    /**
     * Total {@code Retry-After} budget for a single non-streaming send; a throttle beyond this gives up rather than
     * blocking a request/worker thread unboundedly.
     */
    private static final long RATE_LIMIT_TOTAL_BUDGET_MS = 30_000L;
    /** Never honor a single {@code Retry-After} longer than this — defends against an absurd/hostile header. */
    private static final long RATE_LIMIT_MAX_WAIT_MS = 20_000L;

    private final Slack slack;
    private final SlackCredentialProvider credentialProvider;
    private final ConcurrentMap<Long, String> botUserIdCache = new ConcurrentHashMap<>();

    public SlackMessageService(SlackCredentialProvider credentialProvider) {
        this.slack = Slack.getInstance();
        this.credentialProvider = credentialProvider;
    }

    public void sendForWorkspace(long workspaceId, String channelId, List<LayoutBlock> blocks, String fallback) {
        sendForWorkspace(workspaceId, channelId, null, blocks, fallback);
    }

    public void sendForWorkspace(
        long workspaceId,
        String channelId,
        @Nullable String threadTs,
        List<LayoutBlock> blocks,
        String fallback
    ) {
        String token = resolveToken(workspaceId).orElseThrow(() ->
            new SlackSendException(workspaceId, channelId, "no_active_slack_connection")
        );
        ChatPostMessageRequest.ChatPostMessageRequestBuilder request = ChatPostMessageRequest.builder()
            .channel(channelId)
            .blocks(blocks)
            .text(fallback); // fallback text shown in notifications + accessibility tools
        if (threadTs != null && !threadTs.isBlank()) {
            request.threadTs(threadTs);
        }
        try {
            ChatPostMessageResponse response = callHonoringRateLimit(() ->
                slack.methods(token).chatPostMessage(request.build())
            );
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
     * Post an <strong>ephemeral</strong> message via {@code chat.postEphemeral} — visible only to {@code slackUserId}
     * in {@code channelId}, seen by no one else and gone on reload. The seam the just-in-time consent notice (shown
     * to a member who joins an already-active channel) and the in-message opt-out confirmation render through.
     * Mirrors {@link #sendForWorkspace}: throws {@link SlackSendException} carrying the Slack error so the caller can
     * log-and-swallow (the notice is best-effort).
     */
    public void sendEphemeralForWorkspace(
        long workspaceId,
        String channelId,
        String slackUserId,
        List<LayoutBlock> blocks,
        String fallback
    ) {
        String token = resolveToken(workspaceId).orElseThrow(() ->
            new SlackSendException(workspaceId, channelId, "no_active_slack_connection")
        );
        ChatPostEphemeralRequest request = ChatPostEphemeralRequest.builder()
            .channel(channelId)
            .user(slackUserId)
            .blocks(blocks)
            .text(fallback) // fallback text shown in notifications + accessibility tools
            .build();
        try {
            ChatPostEphemeralResponse response = callHonoringRateLimit(() ->
                slack.methods(token).chatPostEphemeral(request)
            );
            if (!response.isOk()) {
                String error = response.getError() == null ? "unknown" : response.getError();
                log.warn(
                    "Slack chat.postEphemeral failed: workspaceId={}, channelId={}, userId={}, error={}",
                    workspaceId,
                    channelId,
                    slackUserId,
                    error
                );
                throw new SlackSendException(workspaceId, channelId, error);
            }
        } catch (SlackApiException | IOException e) {
            log.warn(
                "Slack chat.postEphemeral transport failure: workspaceId={}, channelId={}, error={}",
                workspaceId,
                channelId,
                e.getMessage()
            );
            throw new SlackSendException(workspaceId, channelId, "transport_failure", e);
        }
    }

    /**
     * The app's own bot user id ({@code U…}) for this workspace via {@code auth.test}, or empty when it cannot be
     * resolved (no active connection / Slack failure). Best-effort and never throws — used only to skip the bot's
     * OWN {@code member_joined_channel} event (adding the app to a channel fires that event too), so an unresolved
     * id degrades to "cannot confirm it's the bot" rather than blocking the caller.
     *
     * <p>Positive results are cached per workspace: the bot user id is stable for an installation, and this runs on
     * the serial event-consumer thread for every {@code member_joined_channel}, so an uncached remote call per join
     * would stall unrelated workspaces' events. Failures are not cached (retried on the next event).
     */
    /** Evict the cached bot user id, e.g. on uninstall — a later reconnect may install a different app. */
    public void evictBotUserId(long workspaceId) {
        botUserIdCache.remove(workspaceId);
    }

    public Optional<String> resolveBotUserId(long workspaceId) {
        String cached = botUserIdCache.get(workspaceId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<String> token = resolveToken(workspaceId);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        try {
            AuthTestResponse r = callHonoringRateLimit(() -> slack.methods(token.get()).authTest(req -> req));
            if (r.isOk() && r.getUserId() != null && !r.getUserId().isBlank()) {
                botUserIdCache.put(workspaceId, r.getUserId());
                return Optional.of(r.getUserId());
            }
            log.debug("Slack auth.test not ok for workspaceId={}: error={}", workspaceId, r.getError());
            return Optional.empty();
        } catch (SlackApiException | IOException e) {
            log.debug("Slack auth.test failed for workspaceId={}: {}", workspaceId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Begin a streamed assistant reply in {@code threadTs}; returns the streaming message {@code ts} to append
     * to. {@code markdownText} is standard Markdown (Slack renders it, incl. tables) — not Slack mrkdwn.
     */
    public String startStream(long workspaceId, String channel, String threadTs, String markdownText) {
        String token = resolveToken(workspaceId).orElseThrow(() ->
            new SlackSendException(workspaceId, channel, "no_active_slack_connection")
        );
        try {
            ChatStartStreamResponse r = slack
                .methods(token)
                .chatStartStream(req -> req.channel(channel).threadTs(threadTs).markdownText(markdownText));
            if (!r.isOk()) {
                throw new SlackSendException(workspaceId, channel, r.getError() == null ? "unknown" : r.getError());
            }
            return r.getTs();
        } catch (SlackApiException e) {
            throw streamFailure(workspaceId, channel, e);
        } catch (IOException e) {
            throw new SlackSendException(workspaceId, channel, "transport_failure", e);
        }
    }

    /** Append a Markdown delta to an in-progress stream. Throws with the Slack error so callers can detect a gone recipient. */
    public void appendStream(long workspaceId, String channel, String ts, String markdownText) {
        String token = resolveToken(workspaceId).orElseThrow(() ->
            new SlackSendException(workspaceId, channel, "no_active_slack_connection")
        );
        try {
            ChatAppendStreamResponse r = slack
                .methods(token)
                .chatAppendStream(req -> req.channel(channel).ts(ts).markdownText(markdownText));
            if (!r.isOk()) {
                throw new SlackSendException(workspaceId, channel, r.getError() == null ? "unknown" : r.getError());
            }
        } catch (SlackApiException e) {
            throw streamFailure(workspaceId, channel, e);
        } catch (IOException e) {
            throw new SlackSendException(workspaceId, channel, "transport_failure", e);
        }
    }

    /** Finalize a stream, optionally attaching terminal blocks (finding chips / actions). */
    public void stopStream(long workspaceId, String channel, String ts, List<LayoutBlock> blocks) {
        String token = resolveToken(workspaceId).orElseThrow(() ->
            new SlackSendException(workspaceId, channel, "no_active_slack_connection")
        );
        try {
            ChatStopStreamResponse r = slack
                .methods(token)
                .chatStopStream(req -> req.channel(channel).ts(ts).blocks(blocks));
            if (!r.isOk()) {
                throw new SlackSendException(workspaceId, channel, r.getError() == null ? "unknown" : r.getError());
            }
        } catch (SlackApiException e) {
            throw streamFailure(workspaceId, channel, e);
        } catch (IOException e) {
            throw new SlackSendException(workspaceId, channel, "transport_failure", e);
        }
    }

    /**
     * Publish (replace) the App Home tab view for one member via {@code views.publish}. The seam the App
     * Home (disclosure + research-consent toggle) renders through — the sibling of
     * {@code chat.postMessage} for the Home surface. Throws {@link SlackSendException} carrying the Slack
     * error so the caller can log-and-swallow (App Home render is best-effort, like the onboarding CTA).
     */
    public void publishHomeView(long workspaceId, String slackUserId, View view) {
        String token = resolveToken(workspaceId).orElseThrow(() ->
            new SlackSendException(workspaceId, slackUserId, "no_active_slack_connection")
        );
        try {
            ViewsPublishResponse r = callHonoringRateLimit(() ->
                slack.methods(token).viewsPublish(req -> req.userId(slackUserId).view(view))
            );
            if (!r.isOk()) {
                String error = r.getError() == null ? "unknown" : r.getError();
                log.warn(
                    "Slack views.publish failed: workspaceId={}, userId={}, error={}",
                    workspaceId,
                    slackUserId,
                    error
                );
                throw new SlackSendException(workspaceId, slackUserId, error);
            }
        } catch (SlackApiException | IOException e) {
            log.warn(
                "Slack views.publish transport failure: workspaceId={}, userId={}, error={}",
                workspaceId,
                slackUserId,
                e.getMessage()
            );
            throw new SlackSendException(workspaceId, slackUserId, "transport_failure", e);
        }
    }

    /**
     * Set the assistant "thinking…" status on a thread. Best-effort: only assistant threads support it, so a
     * failure (e.g. a plain DM thread) is swallowed — it's a liveness nicety, never load-bearing.
     */
    public void setStatus(long workspaceId, String channel, String threadTs, String status) {
        Optional<String> token = resolveToken(workspaceId);
        if (token.isEmpty()) {
            return;
        }
        try {
            slack
                .methods(token.get())
                .assistantThreadsSetStatus(req -> req.channelId(channel).threadTs(threadTs).status(status));
        } catch (Exception e) {
            log.debug("Slack setStatus skipped (channel={}): {}", channel, e.getMessage());
        }
    }

    /**
     * Set suggested prompts at the top of the agent Messages tab. Best-effort: prompts are a discovery nicety,
     * never load-bearing.
     */
    public void setSuggestedPrompts(long workspaceId, String channel, String title, List<SuggestedPrompt> prompts) {
        Optional<String> token = resolveToken(workspaceId);
        if (token.isEmpty() || prompts.isEmpty()) {
            return;
        }
        try {
            slack
                .methods(token.get())
                .assistantThreadsSetSuggestedPrompts(req -> req.channelId(channel).title(title).prompts(prompts));
        } catch (Exception e) {
            log.debug("Slack setSuggestedPrompts skipped (channel={}): {}", channel, e.getMessage());
        }
    }

    public List<SlackConversationInfo> listConversations(long workspaceId) {
        Optional<String> token = resolveToken(workspaceId);
        if (token.isEmpty()) {
            log.debug("Slack conversations.list skipped: no token for workspaceId={}", workspaceId);
            return List.of();
        }

        MethodsClient methods = slack.methods(token.get());
        List<SlackConversationInfo> channels = new ArrayList<>();
        String cursor = "";
        int pages = 0;
        try {
            do {
                String pageCursor = cursor;
                ConversationsListResponse response = callHonoringRateLimit(() ->
                    methods.conversationsList(req ->
                        req
                            .types(List.of(ConversationType.PUBLIC_CHANNEL, ConversationType.PRIVATE_CHANNEL))
                            .excludeArchived(false)
                            .limit(CONVERSATIONS_LIST_PAGE_SIZE)
                            .cursor(pageCursor)
                    )
                );
                if (!response.isOk()) {
                    log.warn(
                        "Slack conversations.list returned ok=false: workspaceId={}, error={}",
                        workspaceId,
                        response.getError()
                    );
                    return channels;
                }
                if (response.getChannels() != null) {
                    response.getChannels().stream().map(SlackMessageService::toConversationInfo).forEach(channels::add);
                }
                cursor = response.getResponseMetadata() == null ? null : response.getResponseMetadata().getNextCursor();
                pages++;
            } while (cursor != null && !cursor.isBlank() && pages < CONVERSATIONS_LIST_MAX_PAGES);
            if (pages >= CONVERSATIONS_LIST_MAX_PAGES) {
                log.warn(
                    "Slack conversations.list pagination hit cap: workspaceId={}, pages={}, channels={}",
                    workspaceId,
                    pages,
                    channels.size()
                );
            }
            return channels;
        } catch (SlackApiException | IOException e) {
            log.warn(
                "Slack conversations.list transport failure: workspaceId={}, collected={}, error={}",
                workspaceId,
                channels.size(),
                e.getMessage()
            );
            return channels;
        }
    }

    public Optional<SlackConversationInfo> lookupConversation(long workspaceId, String channelId) {
        String token = resolveToken(workspaceId).orElseThrow(() ->
            new SlackSendException(workspaceId, channelId, "no_active_slack_connection")
        );
        try {
            ConversationsInfoResponse response = callHonoringRateLimit(() ->
                slack.methods(token).conversationsInfo(req -> req.channel(channelId).includeNumMembers(true))
            );
            if (!response.isOk()) {
                log.debug(
                    "Slack conversations.info returned ok=false: workspaceId={}, channelId={}, error={}",
                    workspaceId,
                    channelId,
                    response.getError()
                );
                return Optional.empty();
            }
            return Optional.ofNullable(response.getChannel()).map(SlackMessageService::toConversationInfo);
        } catch (SlackApiException | IOException e) {
            log.warn(
                "Slack conversations.info transport failure: workspaceId={}, channelId={}, error={}",
                workspaceId,
                channelId,
                e.getMessage()
            );
            return Optional.empty();
        }
    }

    public void joinPublicChannel(long workspaceId, String channelId) {
        String token = resolveToken(workspaceId).orElseThrow(() ->
            new SlackSendException(workspaceId, channelId, "no_active_slack_connection")
        );
        try {
            ConversationsJoinResponse response = callHonoringRateLimit(() ->
                slack.methods(token).conversationsJoin(req -> req.channel(channelId))
            );
            if (!response.isOk()) {
                throw new SlackSendException(
                    workspaceId,
                    channelId,
                    response.getError() == null ? "unknown" : response.getError()
                );
            }
        } catch (SlackApiException e) {
            throw streamFailure(workspaceId, channelId, e);
        } catch (IOException e) {
            throw new SlackSendException(workspaceId, channelId, "transport_failure", e);
        }
    }

    private static SlackConversationInfo toConversationInfo(Conversation conversation) {
        return new SlackConversationInfo(
            conversation.getId(),
            conversation.getName() == null || conversation.getName().isBlank()
                ? conversation.getId()
                : conversation.getName(),
            conversation.isPrivate(),
            conversation.isMember(),
            conversation.isArchived()
        );
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
                UsersListResponse response = callHonoringRateLimit(() ->
                    methods.usersList(r -> r.limit(USERS_LIST_PAGE_SIZE).cursor(pageCursor))
                );
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

    public record SlackConversationInfo(
        String channelId,
        String channelName,
        boolean privateChannel,
        boolean member,
        boolean archived
    ) {}

    /** A synchronous Slack Web API call that may throw the SDK's checked failures. */
    @FunctionalInterface
    private interface SlackCall<T> {
        T call() throws SlackApiException, IOException;
    }

    /**
     * Run a synchronous Slack Web API call, transparently honoring a 429 {@code Retry-After} (jittered, bounded by
     * {@link #RATE_LIMIT_TOTAL_BUDGET_MS}). The SDK's synchronous {@code MethodsClient} surfaces a 429 as a
     * {@link SlackApiException} and does <em>not</em> retry it (only the async client throttles), so the bounded
     * wait-and-retry happens here. Non-429 {@link SlackApiException} and {@link IOException} propagate unchanged so
     * the caller's existing error handling is preserved.
     */
    private <T> T callHonoringRateLimit(SlackCall<T> call) throws SlackApiException, IOException {
        long budgetLeftMs = RATE_LIMIT_TOTAL_BUDGET_MS;
        int attempt = 0;
        while (true) {
            try {
                return call.call();
            } catch (SlackApiException e) {
                long retryAfterMs = rateLimitRetryAfterMillis(e);
                if (retryAfterMs == SlackSendException.NOT_RATE_LIMITED || budgetLeftMs <= 0) {
                    throw e; // not a rate-limit, or the retry budget is spent — let the caller map it
                }
                long waitMs = Math.min(backoffWithJitter(retryAfterMs, ++attempt), budgetLeftMs);
                log.warn(
                    "Slack rate-limited (429); backing off {} ms before retry (attempt {}, budget left {} ms)",
                    waitMs,
                    attempt,
                    budgetLeftMs
                );
                budgetLeftMs -= waitMs;
                sleepQuietly(waitMs);
                if (Thread.currentThread().isInterrupted()) {
                    // Interrupted mid-backoff (e.g. a shutting-down worker thread): stop retrying immediately rather
                    // than spinning the rest of the budget in a tight loop; surface the last 429 unchanged (the
                    // interrupt flag stays set for the caller) so its error handling maps it.
                    throw e;
                }
            }
        }
    }

    /**
     * Map a streaming-call {@link SlackApiException} onto a {@link SlackSendException}, carrying the 429
     * {@code Retry-After} wait so the streaming channel can honor it without counting it as a stream death.
     */
    private static SlackSendException streamFailure(long workspaceId, String channel, SlackApiException e) {
        long retryAfterMs = rateLimitRetryAfterMillis(e);
        if (retryAfterMs != SlackSendException.NOT_RATE_LIMITED) {
            return new SlackSendException(workspaceId, channel, "ratelimited", retryAfterMs, e);
        }
        return new SlackSendException(workspaceId, channel, "transport_failure", e);
    }

    /**
     * The {@code Retry-After} wait in ms for a 429, else {@link SlackSendException#NOT_RATE_LIMITED}. Slack always
     * sends {@code Retry-After} on a 429; a missing/garbled header defaults to 1s.
     */
    static long rateLimitRetryAfterMillis(SlackApiException e) {
        Response resp = e.getResponse();
        if (resp == null || resp.code() != 429) {
            return SlackSendException.NOT_RATE_LIMITED;
        }
        String header = resp.header("Retry-After");
        long seconds = 1L;
        if (header != null) {
            try {
                seconds = Long.parseLong(header.trim());
            } catch (NumberFormatException ignored) {
                // keep the 1s default
            }
        }
        return Math.max(0L, seconds) * 1000L;
    }

    /**
     * Honor {@code retryAfterMs} (capped at {@link #RATE_LIMIT_MAX_WAIT_MS}) plus a small jitter; when the header
     * asked for no wait, fall back to a mild exponential backoff (1s, 2s, 4s… capped). Jitter avoids a thundering
     * herd of replicas retrying in lockstep.
     */
    private static long backoffWithJitter(long retryAfterMs, int attempt) {
        long base = retryAfterMs > 0 ? retryAfterMs : 1000L * (1L << Math.min(attempt - 1, 4));
        base = Math.min(base, RATE_LIMIT_MAX_WAIT_MS);
        return base + ThreadLocalRandom.current().nextLong(0L, 250L);
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Optional<String> resolveToken(long workspaceId) {
        return credentialProvider
            .resolve(new IntegrationRef(IntegrationKind.SLACK, workspaceId, null))
            .flatMap(b -> b instanceof BearerToken bt ? Optional.of(bt.token()) : Optional.empty());
    }
}
