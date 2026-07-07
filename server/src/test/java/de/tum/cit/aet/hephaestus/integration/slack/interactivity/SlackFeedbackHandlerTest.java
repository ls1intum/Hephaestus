package de.tum.cit.aet.hephaestus.integration.slack.interactivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackAppHomeService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Interactivity-routing unit tests. A binary thumb appends a {@code mentor_turn_rating} row (latest-wins over the
 * append-only rows), and the App Home consent toggle drives ingestion consent + erasure + research participation.
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
    private ResearchParticipationCommand researchParticipationCommand;

    @Mock
    private SlackAppHomeService appHomeService;

    @Mock
    private SlackParticipantConsentService participantConsentService;

    @Mock
    private SlackPersonErasureService personErasureService;

    @Mock
    private SlackMessageService messageService;

    private SlackFeedbackHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SlackFeedbackHandler(
            ratingRepository,
            workspaceResolver,
            identityResolver,
            researchParticipationCommand,
            appHomeService,
            participantConsentService,
            personErasureService,
            messageService
        );
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.of(WORKSPACE_ID));
        // The App Home opt-IN path no longer resolves a member id (only opt-out, for erasure), so this shared
        // stub is lenient — the member-gated action tests and the opt-out path still consume it.
        org.mockito.Mockito.lenient()
            .when(identityResolver.resolveMemberId(WORKSPACE_ID, TEAM, USER))
            .thenReturn(Optional.of(RATER_ID));
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
    void thumbsUp_writesHelpfulRating() {
        handler.handleBlockActions(
            blockActions(SlackFeedbackBlocks.ACTION_TURN_HELPFUL, SlackFeedbackBlocks.turnValue(MESSAGE_TS))
        );

        ArgumentCaptor<MentorTurnRating> captor = ArgumentCaptor.forClass(MentorTurnRating.class);
        verify(ratingRepository).save(captor.capture());
        assertThat(captor.getValue().getRating()).isEqualTo(TurnRating.HELPFUL);
        // A satisfaction thumb is NOT a consent decision — it must never touch research participation.
        verifyNoInteractions(researchParticipationCommand);
    }

    @Test
    void thumbsDown_writesUnhelpfulRating() {
        handler.handleBlockActions(
            blockActions(SlackFeedbackBlocks.ACTION_TURN_UNHELPFUL, SlackFeedbackBlocks.turnValue(MESSAGE_TS))
        );

        ArgumentCaptor<MentorTurnRating> captor = ArgumentCaptor.forClass(MentorTurnRating.class);
        verify(ratingRepository).save(captor.capture());
        assertThat(captor.getValue().getRating()).isEqualTo(TurnRating.UNHELPFUL);
    }

    @Test
    void latestWins_appendsANewRowPerClick_inClickOrder() {
        handler.handleBlockActions(
            blockActions(SlackFeedbackBlocks.ACTION_TURN_HELPFUL, SlackFeedbackBlocks.turnValue(MESSAGE_TS))
        );
        handler.handleBlockActions(
            blockActions(SlackFeedbackBlocks.ACTION_TURN_UNHELPFUL, SlackFeedbackBlocks.turnValue(MESSAGE_TS))
        );

        ArgumentCaptor<MentorTurnRating> captor = ArgumentCaptor.forClass(MentorTurnRating.class);
        verify(ratingRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(MentorTurnRating::getRating)
            .containsExactly(TurnRating.HELPFUL, TurnRating.UNHELPFUL);
    }

    @Test
    void appHomeOptOut_recordsIngestionConsent_erasesMemberData_setsResearchFalse_republishesHome() {
        when(identityResolver.resolveDeveloperLogin(WORKSPACE_ID, TEAM, USER)).thenReturn(Optional.of("octocat"));

        handler.handleBlockActions(blockActions(SlackAppHomeService.ACTION_RESEARCH_OPT_OUT, "false"));

        // Opting out BOTH stops future ingestion (person consent) AND erases past data (person erasure)…
        verify(participantConsentService).recordAppHomeDecision(WORKSPACE_ID, USER, false);
        verify(personErasureService).eraseMember(WORKSPACE_ID, RATER_ID, USER);
        // …and still flips research participation + re-renders the Home tab.
        verify(researchParticipationCommand).setForLogin("octocat", false, ConsentSource.SLACK_APP_HOME);
        verify(appHomeService).onHomeOpened(TEAM, USER);
        // A consent toggle is not a rating.
        verifyNoInteractions(ratingRepository);
    }

    @Test
    void appHomeOptIn_recordsConsent_neverUnErases_setsResearchTrue() {
        when(identityResolver.resolveDeveloperLogin(WORKSPACE_ID, TEAM, USER)).thenReturn(Optional.of("octocat"));

        handler.handleBlockActions(blockActions(SlackAppHomeService.ACTION_RESEARCH_OPT_IN, "true"));

        verify(participantConsentService).recordAppHomeDecision(WORKSPACE_ID, USER, true);
        verify(researchParticipationCommand).setForLogin("octocat", true, ConsentSource.SLACK_APP_HOME);
        verify(appHomeService).onHomeOpened(TEAM, USER);
        // Opting back in never un-erases — the erasure service is never touched.
        verifyNoInteractions(personErasureService);
    }

    @Test
    void appHomeOptOut_unlinkedUser_recordsConsent_noErase_noResearch_notThrown() {
        // A truly unlinked user (no member id, no SCM login): the opt-out is still recorded (keyed by Slack user id,
        // covered when they later link), nothing is erased (nothing stored under a member id), and the ACK path
        // never throws. This proves the consent write lives OUTSIDE the member-id guard.
        when(identityResolver.resolveMemberId(WORKSPACE_ID, TEAM, USER)).thenReturn(Optional.empty());
        when(identityResolver.resolveDeveloperLogin(WORKSPACE_ID, TEAM, USER)).thenReturn(Optional.empty());

        handler.handleBlockActions(blockActions(SlackAppHomeService.ACTION_RESEARCH_OPT_OUT, "false"));

        verify(participantConsentService).recordAppHomeDecision(WORKSPACE_ID, USER, false);
        verifyNoInteractions(personErasureService, researchParticipationCommand, appHomeService);
    }

    @Test
    void inMessageOptOut_reusesAppHomeOptOutPath_erasesData_andConfirmsEphemerally() {
        // The one-click "Opt me out" button on the channel notice must drive the SAME opt-out path as App Home:
        // person ingestion opt-out + erase already-collected data + research flag, then an ephemeral confirmation.
        when(identityResolver.resolveDeveloperLogin(WORKSPACE_ID, TEAM, USER)).thenReturn(Optional.of("octocat"));

        handler.handleBlockActions(blockActions(SlackConsentBlocks.ACTION_PARTICIPANT_OPT_OUT, ""));

        // Reused opt-out path (identical to appHomeOptOut) — proves it is not a second implementation.
        verify(participantConsentService).recordAppHomeDecision(WORKSPACE_ID, USER, false);
        verify(personErasureService).eraseMember(WORKSPACE_ID, RATER_ID, USER);
        verify(researchParticipationCommand).setForLogin("octocat", false, ConsentSource.SLACK_APP_HOME);
        // Ephemeral confirmation to the acting user, in the channel the button was clicked in.
        verify(messageService).sendEphemeralForWorkspace(
            eq(WORKSPACE_ID),
            eq(CHANNEL),
            eq(USER),
            anyList(),
            eq(SlackConsentBlocks.CONFIRMATION_TEXT)
        );
        // An opt-out is not a rating.
        verifyNoInteractions(ratingRepository);
    }

    @Test
    void openPrivacyHome_reRendersHomeView_only() {
        handler.handleBlockActions(blockActions(SlackConsentBlocks.ACTION_OPEN_PRIVACY_HOME, ""));

        verify(appHomeService).onHomeOpened(TEAM, USER);
        // The pointer button is a pure navigation nicety — it records no consent decision and posts no message.
        verifyNoInteractions(ratingRepository, participantConsentService, personErasureService, messageService);
    }

    @Test
    void unlinkedUser_dropsAction() {
        when(identityResolver.resolveMemberId(WORKSPACE_ID, TEAM, USER)).thenReturn(Optional.empty());

        handler.handleBlockActions(
            blockActions(SlackFeedbackBlocks.ACTION_TURN_HELPFUL, SlackFeedbackBlocks.turnValue(MESSAGE_TS))
        );

        verifyNoInteractions(ratingRepository);
    }
}
