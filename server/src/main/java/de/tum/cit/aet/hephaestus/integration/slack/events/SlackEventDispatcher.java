package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackAppHomeService;
import de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackOnboardingService;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Routes a verified Slack {@code event_callback} payload to the right handler. Extracted from
 * {@link SlackEventsController} so the controller stays a thin signature-verify + dedup + ACK entry point and
 * the event fan-out (mentor DM, channel ingest, App Home, assistant seed, uninstall) lives in one place —
 * mirroring how {@code SlackInteractivityController} delegates to {@code SlackFeedbackHandler}.
 *
 * <p>The caller wraps {@link #dispatch(JsonNode)} in a try/catch and always ACKs within Slack's 3&nbsp;s window.
 * Every branch that makes a slow/remote Slack Web API call before it can finish is offloaded off the ACK thread so
 * NO Slack Web API call runs before the 200. The offloads use two isolated pools: the DM mentor turn (its
 * {@code assistant.threads.setStatus} ping and any canned reply) runs on {@code slackMentorDmExecutor}, while the
 * privacy/consent surfaces — the App Home re-render + onboarding CTA and the {@code assistant_thread_started}
 * suggested-prompt seed — run on the dedicated {@code slackHomeExecutor}, so a DM burst can never delay the App
 * Home render that shows the privacy disclosure and research-consent toggle. All offloaded branches are
 * best-effort: a dropped Home render or prompt seed re-fires on the next open, and a saturated pool drops the task
 * (logged) without holding the ACK.
 *
 * <p>This dispatcher handles only the INTERACTIVE branches. PASSIVE monitored-channel {@code message} events
 * (plain/edit/delete on a channel or group) are NOT routed here — {@link SlackEventsController} republishes them
 * onto the durable NATS transport and {@code SlackChannelMessageHandler} persists them off the ACK thread with
 * at-least-once semantics. Uninstall / token-revocation stays synchronous so it commits before the ACK.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SlackEventDispatcher.class);

    private final SlackMentorService mentorService;
    private final SlackOnboardingService onboardingService;
    private final SlackAppHomeService appHomeService;
    private final SlackAssistantEventHandler assistantEventHandler;
    private final SlackUninstallService uninstallService;
    private final SlackChannelJoinNoticeHandler joinNoticeHandler;
    private final Executor mentorDmExecutor;
    private final Executor homeExecutor;

    public SlackEventDispatcher(
        SlackMentorService mentorService,
        SlackOnboardingService onboardingService,
        SlackAppHomeService appHomeService,
        SlackAssistantEventHandler assistantEventHandler,
        SlackUninstallService uninstallService,
        SlackChannelJoinNoticeHandler joinNoticeHandler,
        @Qualifier("slackMentorDmExecutor") Executor mentorDmExecutor,
        @Qualifier("slackHomeExecutor") Executor homeExecutor
    ) {
        this.mentorService = mentorService;
        this.onboardingService = onboardingService;
        this.appHomeService = appHomeService;
        this.assistantEventHandler = assistantEventHandler;
        this.uninstallService = uninstallService;
        this.joinNoticeHandler = joinNoticeHandler;
        this.mentorDmExecutor = mentorDmExecutor;
        this.homeExecutor = homeExecutor;
    }

    /** Route one {@code event_callback} envelope by its inner event type. */
    public void dispatch(JsonNode root) {
        String teamId = root.path("team_id").asString("");
        JsonNode event = root.path("event");
        String eventType = event.path("type").asString("");
        if ("app_home_opened".equals(eventType)) {
            // Only (re)publish on the Home tab open; the Messages tab open fires the same event with tab=messages.
            if ("home".equals(event.path("tab").asString("home"))) {
                String slackUserId = event.path("user").asString("");
                // Both publishes go through SlackMessageService.callHonoringRateLimit, whose Retry-After budget is
                // up to 30s each — a 429 on the ACK thread would blow Slack's 3s Events-API window and provoke a
                // retry storm. Offload onto the dedicated Home pool (isolated from the DM pool so a DM burst cannot
                // delay this privacy/consent render; best-effort: a dropped render re-fires on the next open).
                offload(
                    homeExecutor,
                    () -> {
                        // publish the persistent Home tab (disclosure + research-consent toggle).
                        appHomeService.onHomeOpened(teamId, slackUserId);
                        // the DM link CTA for a not-yet-linked member (no-op once linked).
                        onboardingService.onHomeOpened(teamId, slackUserId);
                    },
                    "App Home render"
                );
            }
            return;
        }
        if ("assistant_thread_started".equals(eventType)) {
            // Seed the mentor DM with suggested prompts (assistant.threads.setSuggestedPrompts, a remote Slack call)
            // — MUST route before the non-message early-return below. Offloaded onto the dedicated Home pool for the
            // same Retry-After-budget reason as App Home (best-effort: a dropped seed re-fires on the next open).
            offload(homeExecutor, () -> assistantEventHandler.onThreadStarted(teamId, event), "assistant thread seed");
            return;
        }
        // App removal / token revocation — MUST route before the non-message early-return (which would otherwise
        // drop it, leaving a dead token and orphaned Slack content behind). Flip the Connection to UNINSTALLED and
        // purge the Slack data.
        if ("app_uninstalled".equals(eventType) || "tokens_revoked".equals(eventType)) {
            uninstallService.onUninstall(teamId, eventType);
            return;
        }
        if ("member_joined_channel".equals(eventType)) {
            // Just-in-time consent notice for a member who joins an already-active monitored channel — the one-time
            // activation announcement never reaches a later joiner (ICO ongoing + just-in-time transparency). The
            // handler resolves the workspace + channel consent and posts an EPHEMERAL notice (all DB/remote work),
            // so offload onto the dedicated Home pool (the privacy/consent surface pool), best-effort like the App
            // Home render — a dropped notice does not block anything.
            offload(
                homeExecutor,
                () -> joinNoticeHandler.onMemberJoined(teamId, event),
                "member-joined consent notice"
            );
            return;
        }
        if (!"message".equals(eventType)) {
            return;
        }
        // Passive monitored-channel messages (channel/group, including message_changed/message_deleted subtypes)
        // never reach the dispatcher — SlackEventsController publishes them to the durable transport before this
        // runs. Only the interactive DM (im) mentor turn is handled here. A subtyped or bot-authored message with
        // no im mentor semantics is dropped.
        String subtype = event.path("subtype").asString("");
        if (event.has("bot_id") || !subtype.isEmpty()) {
            return;
        }
        String channelType = event.path("channel_type").asString("");
        if ("im".equals(channelType)) {
            String channelId = event.path("channel").asString("");
            String slackUserId = event.path("user").asString("");
            String text = event.path("text").asString("");
            // A DM mentor turn makes a remote Slack call (setStatus + any canned reply) before the turn runs, so
            // run it off the ACK thread — the 200 must return inside Slack's 3s window. Best-effort: a crash before
            // completion loses at most one mentor reply, which the member can re-request.
            String messageTs = event.path("ts").asString("");
            offload(
                mentorDmExecutor,
                () -> mentorService.handleDm(teamId, channelId, slackUserId, text, messageTs),
                "mentor DM"
            );
        }
    }

    /**
     * Run best-effort post-ACK work on the given pool so no Slack Web API call precedes the 200 on the events
     * endpoint. DM turns go on {@code slackMentorDmExecutor}; the App Home render and assistant-thread seed go on the
     * isolated {@code slackHomeExecutor}. A saturated pool rejects (default {@code AbortPolicy}) → logged as a drop;
     * the ACK still goes out. {@code label} names the dropped work in the log line.
     */
    private void offload(Executor executor, Runnable work, String label) {
        try {
            executor.execute(work);
        } catch (RuntimeException rejected) {
            log.warn("Slack {} dropped: executor rejected the task ({})", label, rejected.getMessage());
        }
    }
}
