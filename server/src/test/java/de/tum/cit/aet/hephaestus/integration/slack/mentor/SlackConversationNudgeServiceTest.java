package de.tum.cit.aet.hephaestus.integration.slack.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationFeedbackPreparedEvent;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/** Guard chain, count-only copy, and one-DM-per-cooldown-window semantics of the prepared-feedback nudge. */
class SlackConversationNudgeServiceTest extends BaseUnitTest {

    private static final long WS = 42L;
    private static final long RECIPIENT = 7L;
    private static final String TEAM = "T1";
    private static final String SLACK_USER = "U1";

    @Mock
    private ConnectionService connectionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountPreferencesQuery accountPreferencesQuery;

    @Mock
    private SlackMentorIdentityResolver identityResolver;

    @Mock
    private SlackMessageService slackMessageService;

    private SlackConversationNudgeService service;

    @BeforeEach
    void setUp() {
        service = new SlackConversationNudgeService(
            connectionService,
            userRepository,
            accountPreferencesQuery,
            identityResolver,
            slackMessageService
        );
    }

    private static ConversationFeedbackPreparedEvent event(int unitCount) {
        return new ConversationFeedbackPreparedEvent(WS, RECIPIENT, unitCount);
    }

    private void stubActiveConnection() {
        Connection connection = mock(Connection.class);
        // lenient: guard-skip tests never reach the identity resolution that reads the team key.
        lenient().when(connection.getInstanceKey()).thenReturn(TEAM);
        when(connectionService.findActive(WS, IntegrationKind.SLACK)).thenReturn(Optional.of(connection));
    }

    private void stubRecipient(boolean aiReviewEnabled) {
        User recipient = new User();
        recipient.setLogin("dev");
        when(userRepository.findById(RECIPIENT)).thenReturn(Optional.of(recipient));
        when(accountPreferencesQuery.preferencesForLogin("dev")).thenReturn(
            Optional.of(new AccountPreferencesQuery.PreferencesView(false, aiReviewEnabled))
        );
    }

    private void stubAllGuardsPass() {
        stubActiveConnection();
        stubRecipient(true);
        when(identityResolver.resolveSlackUserId(RECIPIENT, TEAM)).thenReturn(Optional.of(SLACK_USER));
    }

    @Test
    void allGuardsPass_sendsOneCountOnlyDmToTheRecipient() {
        stubAllGuardsPass();

        service.onConversationFeedbackPrepared(event(2));

        ArgumentCaptor<String> fallback = ArgumentCaptor.forClass(String.class);
        verify(slackMessageService).sendForWorkspace(eq(WS), eq(SLACK_USER), anyList(), fallback.capture());
        // Doorbell only: a count, never finding content.
        assertThat(fallback.getValue()).isEqualTo(
            "You have 2 new practice observations to explore — reply here to go through them."
        );
    }

    @Test
    void singleUnit_usesSingularCopy() {
        stubAllGuardsPass();

        service.onConversationFeedbackPrepared(event(1));

        verify(slackMessageService).sendForWorkspace(
            eq(WS),
            eq(SLACK_USER),
            anyList(),
            eq("You have 1 new practice observation to explore — reply here to go through it.")
        );
    }

    @Test
    void secondEventInsideCooldownWindow_sendsExactlyOneDm() {
        stubAllGuardsPass();

        service.onConversationFeedbackPrepared(event(2));
        service.onConversationFeedbackPrepared(event(1));

        verify(slackMessageService, times(1)).sendForWorkspace(eq(WS), eq(SLACK_USER), anyList(), anyString());
    }

    @Test
    void noActiveSlackConnection_skipsSilently() {
        when(connectionService.findActive(WS, IntegrationKind.SLACK)).thenReturn(Optional.empty());

        service.onConversationFeedbackPrepared(event(2));

        verifyNoInteractions(identityResolver, slackMessageService);
    }

    @Test
    void recipientOptedOutOfAiReview_skipsSilently() {
        stubActiveConnection();
        stubRecipient(false);

        service.onConversationFeedbackPrepared(event(2));

        verifyNoInteractions(identityResolver, slackMessageService);
    }

    @Test
    void recipientWithoutSlackIdentityLink_skipsSilently() {
        stubActiveConnection();
        stubRecipient(true);
        when(identityResolver.resolveSlackUserId(RECIPIENT, TEAM)).thenReturn(Optional.empty());

        service.onConversationFeedbackPrepared(event(2));

        verifyNoInteractions(slackMessageService);
    }

    @Test
    void sendFailure_releasesTheCooldownWindow_soTheNextEventRetries() {
        stubAllGuardsPass();
        doThrow(new SlackSendException(WS, SLACK_USER, "transport_failure"))
            .doNothing()
            .when(slackMessageService)
            .sendForWorkspace(eq(WS), eq(SLACK_USER), anyList(), anyString());

        service.onConversationFeedbackPrepared(event(2));
        service.onConversationFeedbackPrepared(event(2));

        verify(slackMessageService, times(2)).sendForWorkspace(eq(WS), eq(SLACK_USER), anyList(), anyString());
    }

    @Test
    void zeroUnitCount_isIgnored() {
        service.onConversationFeedbackPrepared(event(0));

        verifyNoInteractions(connectionService, userRepository, identityResolver, slackMessageService);
    }
}
