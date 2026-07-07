package de.tum.cit.aet.hephaestus.integration.slack.interactivity;

import de.tum.cit.aet.hephaestus.core.auth.spi.ConsentSource;
import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchParticipationCommand;
import de.tum.cit.aet.hephaestus.integration.slack.channel.SlackConsentBlocks;
import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorTurnRating;
import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorTurnRatingRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.TurnRating;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackParticipantConsentService;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackPersonErasureService;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackWorkspaceResolver;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackFeedbackBlocks;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackMentorIdentityResolver;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackAppHomeService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Routes verified Slack interactivity payloads into the right store:
 *
 * <ul>
 *   <li><strong>Thumbs</strong> ({@code turn_helpful}/{@code turn_unhelpful}) append a {@link MentorTurnRating}.
 *       "Latest wins" is a read ordering over the append-only rows, so a re-click just adds a newer row.</li>
 *   <li><strong>App Home consent</strong> ({@code research_opt_out}/{@code research_opt_in}) drives BOTH purposes
 *       from one toggle: it persists the person-level ingestion opt-out/-in ({@code slack_participant_consent},
 *       member-optional), on opt-out ERASES that person's already-stored Slack data
 *       ({@link SlackPersonErasureService}), and still flips the research-participation flag via
 *       {@link ResearchParticipationCommand#setForLogin} (source {@link ConsentSource#SLACK_APP_HOME}) and
 *       re-publishes the Home tab. Handled outside the member-id guard so an unlinked user can still opt out.</li>
 * </ul>
 *
 * <p>The rater is resolved from the verified Slack identity ({@code (team, user)} → workspace member), so this
 * runs with no HTTP {@code SecurityContext}. Every branch is best-effort: a Slack failure or an invalid target is
 * logged and swallowed (the controller already ACKed within Slack's 3s window).
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackFeedbackHandler {

    private static final Logger log = LoggerFactory.getLogger(SlackFeedbackHandler.class);

    private final MentorTurnRatingRepository ratingRepository;
    private final SlackWorkspaceResolver workspaceResolver;
    private final SlackMentorIdentityResolver identityResolver;
    private final ResearchParticipationCommand researchParticipationCommand;
    private final SlackAppHomeService appHomeService;
    private final SlackParticipantConsentService participantConsentService;
    private final SlackPersonErasureService personErasureService;
    private final SlackMessageService messageService;

    public SlackFeedbackHandler(
        MentorTurnRatingRepository ratingRepository,
        SlackWorkspaceResolver workspaceResolver,
        SlackMentorIdentityResolver identityResolver,
        ResearchParticipationCommand researchParticipationCommand,
        SlackAppHomeService appHomeService,
        SlackParticipantConsentService participantConsentService,
        SlackPersonErasureService personErasureService,
        SlackMessageService messageService
    ) {
        this.ratingRepository = ratingRepository;
        this.workspaceResolver = workspaceResolver;
        this.identityResolver = identityResolver;
        this.researchParticipationCommand = researchParticipationCommand;
        this.appHomeService = appHomeService;
        this.participantConsentService = participantConsentService;
        this.personErasureService = personErasureService;
        this.messageService = messageService;
    }

    /** Handle a {@code block_actions} payload: each element routes by its {@code action_id}. */
    public void handleBlockActions(JsonNode payload) {
        String teamId = payload.path("team").path("id").asString("");
        String slackUserId = payload.path("user").path("id").asString("");
        String channelId = payload.path("channel").path("id").asString("");

        Optional<Long> workspaceOpt = workspaceResolver.resolveWorkspaceId(teamId);
        if (workspaceOpt.isEmpty()) {
            return;
        }
        long workspaceId = workspaceOpt.get();

        // App Home consent toggles are handled FIRST and OUTSIDE the member-id guard below: they key on the Slack
        // user id and are member-optional by design, so an unlinked user can still opt out (the decision is recorded
        // and takes effect once they later link). Everything else (the thumbs) needs a resolved member id.
        List<JsonNode> memberGatedActions = new ArrayList<>();
        for (JsonNode action : payload.path("actions")) {
            String actionId = action.path("action_id").asString("");
            switch (actionId) {
                case SlackAppHomeService.ACTION_RESEARCH_OPT_OUT -> handleConsentToggle(
                    workspaceId,
                    teamId,
                    slackUserId,
                    false
                );
                case SlackAppHomeService.ACTION_RESEARCH_OPT_IN -> handleConsentToggle(
                    workspaceId,
                    teamId,
                    slackUserId,
                    true
                );
                // The one-click in-message opt-out (from the channel consent announcement / the just-in-time join
                // notice): reuse the SAME path as the App Home "Opt out" button, then confirm ephemerally. Kept
                // OUTSIDE the member-id guard for the same reason as the App Home toggle — member-optional by design.
                case SlackConsentBlocks.ACTION_PARTICIPANT_OPT_OUT -> handleInMessageOptOut(
                    workspaceId,
                    teamId,
                    slackUserId,
                    channelId
                );
                // Defensive: a pointer back to the App Home privacy tab just re-renders the Home view (best-effort).
                case SlackConsentBlocks.ACTION_OPEN_PRIVACY_HOME -> appHomeService.onHomeOpened(teamId, slackUserId);
                default -> memberGatedActions.add(action);
            }
        }
        if (memberGatedActions.isEmpty()) {
            return;
        }

        Optional<Long> raterOpt = identityResolver.resolveMemberId(workspaceId, teamId, slackUserId);
        if (raterOpt.isEmpty()) {
            log.debug("slack.interactivity: unlinked Slack user {} in team {} — dropping action", slackUserId, teamId);
            return;
        }
        long raterUserId = raterOpt.get();

        for (JsonNode action : memberGatedActions) {
            String actionId = action.path("action_id").asString("");
            String value = action.path("value").asString("");
            switch (actionId) {
                case SlackFeedbackBlocks.ACTION_TURN_HELPFUL -> recordRating(
                    workspaceId,
                    raterUserId,
                    payload,
                    channelId,
                    value,
                    TurnRating.HELPFUL
                );
                case SlackFeedbackBlocks.ACTION_TURN_UNHELPFUL -> recordRating(
                    workspaceId,
                    raterUserId,
                    payload,
                    channelId,
                    value,
                    TurnRating.UNHELPFUL
                );
                // Defensive fallback for any action_id we do not (yet) route — logged and ignored.
                default -> log.debug("slack.interactivity: unhandled action_id {}", actionId);
            }
        }
    }

    /**
     * Route an App Home consent toggle. A single toggle now drives BOTH purposes:
     * <ol>
     *   <li><strong>Person ingestion consent</strong> — persist the opt-out/-in keyed by the Slack user id (always,
     *       even for an unlinked user, so a later link is covered) via {@link SlackParticipantConsentService}. This is
     *       what {@link de.tum.cit.aet.hephaestus.integration.slack.events.SlackIngestService} consults to stop future
     *       ingestion of an opted-out person.</li>
     *   <li><strong>Erase-on-opt-out</strong> — on opt-out, immediately erase this person's already-stored Slack data
     *       (messages + participant-array prune + derived CONVERSATION feedback) via {@link SlackPersonErasureService},
     *       so opting out both STOPS future ingestion and ERASES past data. Requires a resolved member id; an unlinked
     *       user has nothing stored to erase (logged, skipped — never thrown). Opting back in never un-erases.</li>
     *   <li><strong>Research participation</strong> — the pre-existing flag write, unchanged.</li>
     * </ol>
     * Lenient throughout: this runs on the already-ACKed thread, so nothing here may throw.
     */
    private void handleConsentToggle(long workspaceId, String teamId, String slackUserId, boolean optIn) {
        // 1) Persist the person-level ingestion (+ research) consent, keyed by Slack user id (member-optional).
        participantConsentService.recordAppHomeDecision(workspaceId, slackUserId, optIn);

        // 2) On opt-out, erase this person's already-collected Slack data (needs a resolved workspace member id).
        if (!optIn) {
            identityResolver
                .resolveMemberId(workspaceId, teamId, slackUserId)
                .ifPresentOrElse(
                    memberId -> personErasureService.eraseMember(workspaceId, memberId, slackUserId),
                    () ->
                        log.debug(
                            "slack.interactivity: ingestion opt-out from unlinked Slack user {} in team {} — consent recorded, no stored data to erase",
                            slackUserId,
                            teamId
                        )
                );
        }

        // 3) Pre-existing research-participation flag write (+ Home-tab re-render), unchanged.
        setResearchParticipation(workspaceId, teamId, slackUserId, optIn);
    }

    /**
     * Handle the in-message {@code "Opt me out"} button. Delegates to the EXACT App Home opt-out path
     * ({@link #handleConsentToggle} with {@code optIn = false}) — person ingestion opt-out + erase of already-collected
     * data + research flag + Home re-render — so there is one opt-out implementation, not two. Then confirms to the
     * actor with an ephemeral message in the channel the button was clicked in. Lenient: this runs on the already-ACKed
     * thread, so a Slack failure on the confirmation is logged and swallowed (the opt-out itself already took effect).
     */
    private void handleInMessageOptOut(long workspaceId, String teamId, String slackUserId, String channelId) {
        handleConsentToggle(workspaceId, teamId, slackUserId, false);
        if (channelId == null || channelId.isBlank()) {
            return; // no channel context to confirm into (should not happen for a channel-posted button)
        }
        try {
            messageService.sendEphemeralForWorkspace(
                workspaceId,
                channelId,
                slackUserId,
                SlackConsentBlocks.optOutConfirmation(),
                SlackConsentBlocks.CONFIRMATION_TEXT
            );
        } catch (SlackSendException e) {
            log.debug(
                "slack.interactivity: opt-out confirmation ephemeral failed for user {} in channel {}: {}",
                slackUserId,
                channelId,
                e.slackError()
            );
        }
    }

    /**
     * Flip the acting member's research-participation flag from the App Home consent toggle, then re-publish the
     * Home tab so it renders the new state. Lenient by the {@link ResearchParticipationCommand} contract and by
     * the ACK path: an unlinked user is logged and skipped (never thrown), so Slack's 3s ACK is never blocked.
     */
    private void setResearchParticipation(long workspaceId, String teamId, String slackUserId, boolean participate) {
        Optional<String> login = identityResolver.resolveDeveloperLogin(workspaceId, teamId, slackUserId);
        if (login.isEmpty()) {
            log.debug(
                "slack.interactivity: research consent toggle from unlinked Slack user {} in team {} — skipping",
                slackUserId,
                teamId
            );
            return;
        }
        researchParticipationCommand.setForLogin(login.get(), participate, ConsentSource.SLACK_APP_HOME);
        // Best-effort re-render of the Home tab so the toggle immediately reflects the new consent state.
        appHomeService.onHomeOpened(teamId, slackUserId);
    }

    private void recordRating(
        long workspaceId,
        long raterUserId,
        JsonNode payload,
        String channelId,
        String value,
        TurnRating rating
    ) {
        String messageTs = parseTs(value);
        if (messageTs.isEmpty()) {
            messageTs = payload.path("message").path("ts").asString("");
        }
        String threadTs = payload.path("container").path("thread_ts").asString("");
        if (threadTs.isEmpty()) {
            threadTs = payload.path("message").path("thread_ts").asString(messageTs);
        }

        MentorTurnRating row = MentorTurnRating.builder()
            .workspaceId(workspaceId)
            .raterUserId(raterUserId)
            .channelId(channelId)
            .threadTs(threadTs.isEmpty() ? null : threadTs)
            .slackMessageTs(messageTs)
            .rating(rating)
            .build();
        ratingRepository.save(row);
    }

    // --- value parsing (compact JSON produced by SlackFeedbackBlocks) ---

    private static String parseTs(String value) {
        return extract(value, "ts");
    }

    /** Minimal, dependency-free extraction of a string field from the trusted compact JSON we authored. */
    private static String extract(String json, String field) {
        if (json == null) {
            return "";
        }
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end);
    }
}
