package de.tum.cit.aet.hephaestus.integration.slack.messaging;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatAppendStreamResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatStartStreamResponse;
import com.slack.api.methods.response.chat.ChatStopStreamResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.methods.response.views.ViewsPublishResponse;
import com.slack.api.model.User;
import com.slack.api.model.assistant.SuggestedPrompt;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.view.View;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.slack.credentials.SlackCredentialProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import okhttp3.Response;
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

    /**
     * Total {@code Retry-After} budget for a single non-streaming send; a throttle beyond this gives up rather than
     * blocking a request/worker thread unboundedly.
     */
    private static final long RATE_LIMIT_TOTAL_BUDGET_MS = 30_000L;
    /** Never honor a single {@code Retry-After} longer than this — defends against an absurd/hostile header. */
    private static final long RATE_LIMIT_MAX_WAIT_MS = 20_000L;

    private final Slack slack;
    private final SlackCredentialProvider credentialProvider;

    public SlackMessageService(SlackCredentialProvider credentialProvider) {
        this.slack = Slack.getInstance();
        this.credentialProvider = credentialProvider;
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
            ChatPostMessageResponse response = callHonoringRateLimit(() ->
                slack.methods(token).chatPostMessage(request)
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
     * Begin a streamed assistant reply in {@code threadTs}; returns the streaming message {@code ts} to append
     * to. {@code markdownText} is standard Markdown (Slack renders it, incl. tables) — never legacy mrkdwn.
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
     * Home (disclosure + research-consent toggle + quiet-hours) renders through — the sibling of
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
     * Open a modal on an interaction {@code trigger_id} via {@code views.open} — the seam the interactivity
     * dispute flow renders through (a thumbs-down / "Disagree" on a bound turn asks for the required dispute
     * reason). Throws {@link SlackSendException} carrying the Slack error so the caller can log-and-swallow
     * (a stale/expired trigger just means the modal never opened; the ACK already went out).
     */
    public void openModal(long workspaceId, String triggerId, View view) {
        String token = resolveToken(workspaceId).orElseThrow(() ->
            new SlackSendException(workspaceId, triggerId, "no_active_slack_connection")
        );
        try {
            ViewsOpenResponse r = slack.methods(token).viewsOpen(req -> req.triggerId(triggerId).view(view));
            if (!r.isOk()) {
                String error = r.getError() == null ? "unknown" : r.getError();
                log.warn("Slack views.open failed: workspaceId={}, error={}", workspaceId, error);
                throw new SlackSendException(workspaceId, triggerId, error);
            }
        } catch (SlackApiException | IOException e) {
            log.warn("Slack views.open transport failure: workspaceId={}, error={}", workspaceId, e.getMessage());
            throw new SlackSendException(workspaceId, triggerId, "transport_failure", e);
        }
    }

    /**
     * Set the suggested prompts on an assistant thread ({@code assistant.threads.setSuggestedPrompts}). Rendered
     * when a member opens a mentor DM ({@code assistant_thread_started}). Best-effort: only assistant threads
     * accept it, so a failure is swallowed — the prompts are a discovery nicety, never load-bearing.
     */
    public void setSuggestedPrompts(
        long workspaceId,
        String channel,
        String threadTs,
        String title,
        List<SuggestedPrompt> prompts
    ) {
        Optional<String> token = resolveToken(workspaceId);
        if (token.isEmpty() || prompts.isEmpty()) {
            return;
        }
        try {
            slack
                .methods(token.get())
                .assistantThreadsSetSuggestedPrompts(req ->
                    req.channelId(channel).threadTs(threadTs).title(title).prompts(prompts)
                );
        } catch (Exception e) {
            log.debug("Slack setSuggestedPrompts skipped (channel={}): {}", channel, e.getMessage());
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

    // --- rate-limit handling ---

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
            .resolve(
                new de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef(
                    de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.SLACK,
                    workspaceId,
                    null
                )
            )
            .flatMap(b -> b instanceof BearerToken bt ? Optional.of(bt.token()) : Optional.empty());
    }
}
