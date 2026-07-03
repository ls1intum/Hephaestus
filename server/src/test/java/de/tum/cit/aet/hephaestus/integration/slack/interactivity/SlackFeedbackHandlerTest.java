package de.tum.cit.aet.hephaestus.integration.slack.interactivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.slack.api.model.view.View;
import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorTurnRating;
import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorTurnRatingRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.TurnRating;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackWorkspaceResolver;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackFeedbackBlocks;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackMentorIdentityResolver;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionAction;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * S5 interactivity-routing unit tests. Lock the correctness trap: a binary thumb goes ONLY to
 * {@code mentor_turn_rating}, never to {@code Reaction}; the three-way uptake block is the only path into
 * {@code Reaction}; a thumbs-down on a bound turn opens the dispute modal, whose submission is what writes DISPUTED.
 */
class SlackFeedbackHandlerTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 42L;
    private static final long RATER_ID = 7L;
    private static final String TEAM = "T1";
    private static final String USER = "U1";
    private static final String CHANNEL = "D9";
    private static final String MESSAGE_TS = "100.5";
    private static final String TRIGGER = "trig-123";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Mock
    private MentorTurnRatingRepository ratingRepository;

    @Mock
    private SlackWorkspaceResolver workspaceResolver;

    @Mock
    private SlackMentorIdentityResolver identityResolver;

    @Mock
    private ReactionService reactionService;

    @Mock
    private SlackMessageService messageService;

    private SlackFeedbackHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SlackFeedbackHandler(
            ratingRepository,
            workspaceResolver,
            identityResolver,
            reactionService,
            messageService
        );
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.of(WORKSPACE_ID));
        when(identityResolver.resolveMemberId(WORKSPACE_ID, TEAM, USER)).thenReturn(Optional.of(RATER_ID));
    }

    private ObjectNode blockActions(String actionId, String value) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("type", "block_actions");
        payload.put("trigger_id", TRIGGER);
        payload.putObject("team").put("id", TEAM);
        payload.putObject("user").put("id", USER);
        payload.putObject("channel").put("id", CHANNEL);
        payload.putObject("container").put("thread_ts", "100.0");
        ArrayNode actions = payload.putArray("actions");
        ObjectNode action = actions.addObject();
        action.put("action_id", actionId);
        action.put("value", value);
        return payload;
    }

    @Test
    void thumbsUp_writesHelpfulRating_andNeverReacts() {
        handler.handleBlockActions(
            blockActions(SlackFeedbackBlocks.ACTION_TURN_HELPFUL, SlackFeedbackBlocks.turnValue(MESSAGE_TS, null))
        );

        ArgumentCaptor<MentorTurnRating> captor = ArgumentCaptor.forClass(MentorTurnRating.class);
        verify(ratingRepository).save(captor.capture());
        assertThat(captor.getValue().getRating()).isEqualTo(TurnRating.HELPFUL);
        verifyNoInteractions(reactionService);
    }

    @Test
    void thumbsUp_neverOpensModal() {
        handler.handleBlockActions(
            blockActions(SlackFeedbackBlocks.ACTION_TURN_HELPFUL, SlackFeedbackBlocks.turnValue(MESSAGE_TS, null))
        );

        verify(messageService, never()).openModal(anyLong(), any(), any(View.class));
    }

    @Test
    void thumbsDown_onBoundTurn_writesRating_andOpensDisputeModal() {
        UUID fid = UUID.randomUUID();

        handler.handleBlockActions(
            blockActions(SlackFeedbackBlocks.ACTION_TURN_UNHELPFUL, SlackFeedbackBlocks.turnValue(MESSAGE_TS, fid))
        );

        ArgumentCaptor<MentorTurnRating> captor = ArgumentCaptor.forClass(MentorTurnRating.class);
        verify(ratingRepository).save(captor.capture());
        assertThat(captor.getValue().getRating()).isEqualTo(TurnRating.UNHELPFUL);
        assertThat(captor.getValue().getFeedbackId()).isEqualTo(fid);
        verify(messageService).openModal(eq(WORKSPACE_ID), eq(TRIGGER), any(View.class));
        // A thumb NEVER writes a reaction directly — DISPUTED only lands on the modal submission.
        verifyNoInteractions(reactionService);
    }

    @Test
    void thumbsDown_onUnboundTurn_writesRating_noModal() {
        handler.handleBlockActions(
            blockActions(SlackFeedbackBlocks.ACTION_TURN_UNHELPFUL, SlackFeedbackBlocks.turnValue(MESSAGE_TS, null))
        );

        verify(ratingRepository).save(any(MentorTurnRating.class));
        verify(messageService, never()).openModal(anyLong(), any(), any(View.class));
    }

    @Test
    void latestWins_appendsANewRowPerClick_inClickOrder() {
        handler.handleBlockActions(
            blockActions(SlackFeedbackBlocks.ACTION_TURN_HELPFUL, SlackFeedbackBlocks.turnValue(MESSAGE_TS, null))
        );
        handler.handleBlockActions(
            blockActions(SlackFeedbackBlocks.ACTION_TURN_UNHELPFUL, SlackFeedbackBlocks.turnValue(MESSAGE_TS, null))
        );

        ArgumentCaptor<MentorTurnRating> captor = ArgumentCaptor.forClass(MentorTurnRating.class);
        verify(ratingRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(MentorTurnRating::getRating)
            .containsExactly(TurnRating.HELPFUL, TurnRating.UNHELPFUL);
    }

    @Test
    void uptakeAddressed_writesReaction_notRating() {
        UUID fid = UUID.randomUUID();

        handler.handleBlockActions(
            blockActions(SlackFeedbackBlocks.ACTION_UPTAKE_ADDRESSED, SlackFeedbackBlocks.fidValue(fid))
        );

        verify(reactionService).submitReactionForRecipient(WORKSPACE_ID, fid, RATER_ID, ReactionAction.ADDRESSED, null);
        verify(ratingRepository, never()).save(any());
    }

    @Test
    void viewSubmission_dispute_routesDisputedWithReason() {
        UUID fid = UUID.randomUUID();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("type", "view_submission");
        payload.putObject("team").put("id", TEAM);
        payload.putObject("user").put("id", USER);
        ObjectNode view = payload.putObject("view");
        view.put("callback_id", SlackFeedbackHandler.DISPUTE_CALLBACK_ID);
        view.put("private_metadata", fid.toString());
        view
            .putObject("state")
            .putObject("values")
            .putObject(SlackFeedbackHandler.DISPUTE_BLOCK_ID)
            .putObject(SlackFeedbackHandler.DISPUTE_INPUT_ACTION_ID)
            .put("value", "this rule does not apply to generated code");

        handler.handleViewSubmission(payload);

        verify(reactionService).submitReactionForRecipient(
            WORKSPACE_ID,
            fid,
            RATER_ID,
            ReactionAction.DISPUTED,
            "this rule does not apply to generated code"
        );
        verify(ratingRepository, never()).save(any());
    }

    @Test
    void unlinkedUser_dropsAction() {
        when(identityResolver.resolveMemberId(WORKSPACE_ID, TEAM, USER)).thenReturn(Optional.empty());

        handler.handleBlockActions(
            blockActions(SlackFeedbackBlocks.ACTION_TURN_HELPFUL, SlackFeedbackBlocks.turnValue(MESSAGE_TS, null))
        );

        verifyNoInteractions(ratingRepository, reactionService);
    }
}
