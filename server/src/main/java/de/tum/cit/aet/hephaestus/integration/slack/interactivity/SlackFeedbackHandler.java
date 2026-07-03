package de.tum.cit.aet.hephaestus.integration.slack.interactivity;

import static com.slack.api.model.block.Blocks.input;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.plainTextInput;

import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewClose;
import com.slack.api.model.view.ViewSubmit;
import de.tum.cit.aet.hephaestus.core.auth.spi.ConsentSource;
import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchParticipationCommand;
import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorTurnRating;
import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorTurnRatingRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.RatingSource;
import de.tum.cit.aet.hephaestus.integration.slack.domain.TurnRating;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackParticipantConsentService;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackPersonErasureService;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackWorkspaceResolver;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackFeedbackBlocks;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackMentorIdentityResolver;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackAppHomeService;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionAction;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
 *       A thumb is a satisfaction signal, NEVER a {@code Reaction} — it never writes {@code ADDRESSED}.
 *       "Latest wins" is a read ordering over the append-only rows, so a re-click just adds a newer row.</li>
 *   <li><strong>Uptake</strong> ({@code uptake_addressed}/{@code uptake_not_applicable}) write a
 *       {@code Reaction} via {@link ReactionService}. This is the ONLY path into {@code Reaction}.</li>
 *   <li><strong>Dispute</strong> — a thumbs-down on a bound turn, or the uptake "Disagree", opens a modal that
 *       collects the required reason; the modal's {@code view_submission} routes into {@code Reaction} as
 *       {@code DISPUTED} with that reason.</li>
 *   <li><strong>App Home consent</strong> ({@code research_opt_out}/{@code research_opt_in}) drives BOTH purposes
 *       from one toggle: it persists the person-level ingestion opt-out/-in ({@code slack_participant_consent},
 *       member-optional), on opt-out ERASES that person's already-stored Slack data
 *       ({@link SlackPersonErasureService}), and still flips the research-participation flag via
 *       {@link ResearchParticipationCommand#setForLogin} (source {@link ConsentSource#SLACK_APP_HOME}) and
 *       re-publishes the Home tab. Handled outside the member-id guard so an unlinked user can still opt out.</li>
 * </ul>
 *
 * <p>The reactor is resolved from the verified Slack identity ({@code (team, user)} → workspace member), so this
 * runs with no HTTP {@code SecurityContext}. Every branch is best-effort: a Slack failure or an invalid target is
 * logged and swallowed (the controller already ACKed within Slack's 3s window).
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackFeedbackHandler {

    private static final Logger log = LoggerFactory.getLogger(SlackFeedbackHandler.class);

    static final String DISPUTE_CALLBACK_ID = "mentor_dispute";
    static final String DISPUTE_BLOCK_ID = "dispute_reason";
    static final String DISPUTE_INPUT_ACTION_ID = "reason";

    private final MentorTurnRatingRepository ratingRepository;
    private final SlackWorkspaceResolver workspaceResolver;
    private final SlackMentorIdentityResolver identityResolver;
    private final ReactionService reactionService;
    private final SlackMessageService messageService;
    private final ResearchParticipationCommand researchParticipationCommand;
    private final SlackAppHomeService appHomeService;
    private final SlackParticipantConsentService participantConsentService;
    private final SlackPersonErasureService personErasureService;

    public SlackFeedbackHandler(
        MentorTurnRatingRepository ratingRepository,
        SlackWorkspaceResolver workspaceResolver,
        SlackMentorIdentityResolver identityResolver,
        ReactionService reactionService,
        SlackMessageService messageService,
        ResearchParticipationCommand researchParticipationCommand,
        SlackAppHomeService appHomeService,
        SlackParticipantConsentService participantConsentService,
        SlackPersonErasureService personErasureService
    ) {
        this.ratingRepository = ratingRepository;
        this.workspaceResolver = workspaceResolver;
        this.identityResolver = identityResolver;
        this.reactionService = reactionService;
        this.messageService = messageService;
        this.researchParticipationCommand = researchParticipationCommand;
        this.appHomeService = appHomeService;
        this.participantConsentService = participantConsentService;
        this.personErasureService = personErasureService;
    }

    /** Handle a {@code block_actions} payload: each element routes by its {@code action_id}. */
    public void handleBlockActions(JsonNode payload) {
        String teamId = payload.path("team").path("id").asString("");
        String slackUserId = payload.path("user").path("id").asString("");
        String channelId = payload.path("channel").path("id").asString("");
        String triggerId = payload.path("trigger_id").asString("");

        Optional<Long> workspaceOpt = workspaceResolver.resolveWorkspaceId(teamId);
        if (workspaceOpt.isEmpty()) {
            return;
        }
        long workspaceId = workspaceOpt.get();

        // App Home consent toggles are handled FIRST and OUTSIDE the member-id guard below: they key on the Slack
        // user id and are member-optional by design, so an unlinked user can still opt out (the decision is recorded
        // and takes effect once they later link). Everything else (thumbs/uptake/dispute) needs a resolved member id.
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
                    TurnRating.HELPFUL,
                    triggerId
                );
                case SlackFeedbackBlocks.ACTION_TURN_UNHELPFUL -> recordRating(
                    workspaceId,
                    raterUserId,
                    payload,
                    channelId,
                    value,
                    TurnRating.UNHELPFUL,
                    triggerId
                );
                case SlackFeedbackBlocks.ACTION_UPTAKE_ADDRESSED -> routeReaction(
                    workspaceId,
                    raterUserId,
                    parseFid(value),
                    ReactionAction.ADDRESSED
                );
                case SlackFeedbackBlocks.ACTION_UPTAKE_NOT_APPLICABLE -> routeReaction(
                    workspaceId,
                    raterUserId,
                    parseFid(value),
                    ReactionAction.NOT_APPLICABLE
                );
                case SlackFeedbackBlocks.ACTION_UPTAKE_DISPUTED -> openDisputeModal(
                    workspaceId,
                    triggerId,
                    parseFid(value)
                );
                // Defensive fallback for any action_id we do not (yet) route — logged and ignored.
                default -> log.debug("slack.interactivity: unhandled action_id {}", actionId);
            }
        }
    }

    /** Handle a {@code view_submission}: the dispute modal collects the reason and writes a DISPUTED reaction. */
    public void handleViewSubmission(JsonNode payload) {
        JsonNode view = payload.path("view");
        if (!DISPUTE_CALLBACK_ID.equals(view.path("callback_id").asString(""))) {
            return;
        }
        UUID feedbackId = parseUuid(view.path("private_metadata").asString(""));
        if (feedbackId == null) {
            return;
        }
        String reason = view
            .path("state")
            .path("values")
            .path(DISPUTE_BLOCK_ID)
            .path(DISPUTE_INPUT_ACTION_ID)
            .path("value")
            .asString("");

        String teamId = payload.path("team").path("id").asString("");
        String slackUserId = payload.path("user").path("id").asString("");
        Optional<Long> workspaceOpt = workspaceResolver.resolveWorkspaceId(teamId);
        if (workspaceOpt.isEmpty()) {
            return;
        }
        long workspaceId = workspaceOpt.get();
        identityResolver
            .resolveMemberId(workspaceId, teamId, slackUserId)
            .ifPresent(raterUserId ->
                routeReaction(workspaceId, raterUserId, feedbackId, ReactionAction.DISPUTED, reason)
            );
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
        TurnRating rating,
        String triggerId
    ) {
        String messageTs = parseTs(value);
        if (messageTs.isEmpty()) {
            messageTs = payload.path("message").path("ts").asString("");
        }
        String threadTs = payload.path("container").path("thread_ts").asString("");
        if (threadTs.isEmpty()) {
            threadTs = payload.path("message").path("thread_ts").asString(messageTs);
        }
        UUID feedbackId = parseFid(value);

        MentorTurnRating row = MentorTurnRating.builder()
            .workspaceId(workspaceId)
            .raterUserId(raterUserId)
            .channelId(channelId)
            .threadTs(threadTs.isEmpty() ? null : threadTs)
            .slackMessageTs(messageTs)
            .feedbackId(feedbackId)
            .rating(rating)
            .source(RatingSource.BUTTON)
            .build();
        ratingRepository.save(row);

        // A thumbs-down on a turn that raised a piece of feedback opens the dispute path (reasoned rejection).
        if (rating == TurnRating.UNHELPFUL && feedbackId != null) {
            openDisputeModal(workspaceId, triggerId, feedbackId);
        }
    }

    private void routeReaction(long workspaceId, long raterUserId, @Nullable UUID feedbackId, ReactionAction action) {
        routeReaction(workspaceId, raterUserId, feedbackId, action, null);
    }

    private void routeReaction(
        long workspaceId,
        long raterUserId,
        @Nullable UUID feedbackId,
        ReactionAction action,
        @Nullable String explanation
    ) {
        if (feedbackId == null) {
            log.debug("slack.interactivity: {} on an unbound turn — no feedback to react to", action);
            return;
        }
        try {
            reactionService.submitReactionForRecipient(workspaceId, feedbackId, raterUserId, action, explanation);
        } catch (RuntimeException e) {
            // Not the recipient / not delivered / missing reason — best-effort, the ACK already went out.
            log.debug("slack.interactivity: reaction {} on {} rejected: {}", action, feedbackId, e.getMessage());
        }
    }

    private void openDisputeModal(long workspaceId, String triggerId, @Nullable UUID feedbackId) {
        if (feedbackId == null || triggerId.isBlank()) {
            return;
        }
        View modal = View.builder()
            .type("modal")
            .callbackId(DISPUTE_CALLBACK_ID)
            .privateMetadata(feedbackId.toString())
            .title(ViewTitleFactory.title("Disagree with feedback"))
            .submit(ViewSubmit.builder().type("plain_text").text("Submit").build())
            .close(ViewClose.builder().type("plain_text").text("Cancel").build())
            .blocks(
                List.of(
                    input(i ->
                        i
                            .blockId(DISPUTE_BLOCK_ID)
                            .label(plainText("Why doesn't this feedback apply?"))
                            .element(
                                plainTextInput(pt -> pt.actionId(DISPUTE_INPUT_ACTION_ID).multiline(true).minLength(1))
                            )
                    )
                )
            )
            .build();
        try {
            messageService.openModal(workspaceId, triggerId, modal);
        } catch (SlackSendException e) {
            log.debug("slack.interactivity: dispute modal open failed: {}", e.slackError());
        }
    }

    // --- value parsing (compact JSON produced by SlackFeedbackBlocks) ---

    private static String parseTs(String value) {
        return extract(value, "ts");
    }

    private static @Nullable UUID parseFid(String value) {
        return parseUuid(extract(value, "fid"));
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

    private static @Nullable UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Tiny helper so the modal title stays a one-liner (Slack view titles are plain_text objects). */
    private static final class ViewTitleFactory {

        private static com.slack.api.model.view.ViewTitle title(String text) {
            return com.slack.api.model.view.ViewTitle.builder().type("plain_text").text(text).build();
        }
    }
}
