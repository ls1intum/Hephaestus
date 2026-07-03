package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackAppHomeService;
import de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackOnboardingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Routes a verified Slack {@code event_callback} payload to the right handler. Extracted from
 * {@link SlackEventsController} so the controller stays a thin signature-verify + dedup + ACK entry point and
 * the event fan-out (mentor DM, channel ingest, App Home, assistant seed, uninstall) lives in one place —
 * mirroring how {@code SlackInteractivityController} delegates to {@code SlackFeedbackHandler}.
 *
 * <p>Every branch is best-effort; the caller wraps {@link #dispatch(JsonNode)} in a try/catch and always ACKs
 * within Slack's 3&nbsp;s window.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackEventDispatcher {

    private final SlackMentorService mentorService;
    private final SlackIngestService ingestService;
    private final SlackOnboardingService onboardingService;
    private final SlackAppHomeService appHomeService;
    private final SlackAssistantEventHandler assistantEventHandler;
    private final SlackUninstallService uninstallService;

    public SlackEventDispatcher(
        SlackMentorService mentorService,
        SlackIngestService ingestService,
        SlackOnboardingService onboardingService,
        SlackAppHomeService appHomeService,
        SlackAssistantEventHandler assistantEventHandler,
        SlackUninstallService uninstallService
    ) {
        this.mentorService = mentorService;
        this.ingestService = ingestService;
        this.onboardingService = onboardingService;
        this.appHomeService = appHomeService;
        this.assistantEventHandler = assistantEventHandler;
        this.uninstallService = uninstallService;
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
                // publish the persistent Home tab (disclosure + research-consent toggle + quiet-hours).
                appHomeService.onHomeOpened(teamId, slackUserId);
                // the DM link CTA for a not-yet-linked member (no-op once linked).
                onboardingService.onHomeOpened(teamId, slackUserId);
            }
            return;
        }
        if ("assistant_thread_started".equals(eventType)) {
            // Seed the mentor DM with suggested prompts — MUST route before the non-message early-return below.
            assistantEventHandler.onThreadStarted(teamId, event);
            return;
        }
        // App removal / token revocation — MUST route before the non-message early-return (which would otherwise
        // drop it, leaving a dead token and orphaned Slack content behind). Flip the Connection to UNINSTALLED and
        // purge the Slack data.
        if ("app_uninstalled".equals(eventType) || "tokens_revoked".equals(eventType)) {
            uninstallService.onUninstall(teamId, eventType);
            return;
        }
        if (!"message".equals(eventType)) {
            return;
        }
        // Edits/deletes arrive as message SUBTYPES, so they MUST be routed before the subtype early-return below
        // (which otherwise drops every subtyped message). Slack carries the changed/deleted payload nested under
        // event.message / event.previous_message, so the outer bot_id guard does not apply to these — a bot-authored
        // message we never stored simply no-ops the scoped UPDATE.
        String subtype = event.path("subtype").asString("");
        String channelId = event.path("channel").asString("");
        if ("message_deleted".equals(subtype)) {
            // Tombstone keys on the DELETED message's ts (deleted_ts, fallback previous_message.ts), never event.ts.
            String deletedTs = event
                .path("deleted_ts")
                .asString(event.path("previous_message").path("ts").asString(""));
            ingestService.tombstoneMessage(teamId, channelId, deletedTs);
            return;
        }
        if ("message_changed".equals(subtype)) {
            JsonNode changed = event.path("message");
            ingestService.editMessage(
                teamId,
                channelId,
                changed.path("ts").asString(""),
                changed.path("text").asString("")
            );
            return;
        }
        // Never react to our own bot's messages, or to any other subtype (joins, channel_topic, thread_broadcast…).
        if (event.has("bot_id") || !subtype.isEmpty()) {
            return;
        }
        String channelType = event.path("channel_type").asString("");
        String slackUserId = event.path("user").asString("");
        String text = event.path("text").asString("");

        if ("im".equals(channelType)) {
            mentorService.handleDm(teamId, channelId, slackUserId, text, event.path("ts").asString(""));
        } else if ("channel".equals(channelType) || "group".equals(channelType)) {
            ingestService.ingestChannelMessage(
                teamId,
                channelId,
                event.path("ts").asString(""),
                event.path("thread_ts").asString(null),
                slackUserId,
                text
            );
        }
    }
}
