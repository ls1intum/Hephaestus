package de.tum.cit.aet.hephaestus.integration.slack.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackChannelConsentEvent;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackChannelConsentEventRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackParticipantConsentRepository;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackIngestService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * State-machine unit tests for {@link SlackChannelConsentService}. Deterministic (all collaborators mocked): each
 * test locks one edge of the mentoring-only machine and its side effect, and would fail if the guard or the side
 * effect were removed.
 */
class SlackChannelConsentServiceTest extends BaseUnitTest {

    private static final long WS = 7L;
    private static final String CHANNEL = "C1";

    @Mock
    private SlackMonitoredChannelRepository monitoredChannelRepository;

    @Mock
    private SlackChannelConsentEventRepository consentEventRepository;

    @Mock
    private SlackParticipantConsentRepository participantConsentRepository;

    @Mock
    private SlackIngestService ingestService;

    @Mock
    private SlackMessageService slackMessageService;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private UserRepository userRepository;

    private SlackChannelConsentService service() {
        return new SlackChannelConsentService(
            monitoredChannelRepository,
            consentEventRepository,
            participantConsentRepository,
            ingestService,
            slackMessageService,
            connectionService,
            userRepository
        );
    }

    private SlackMonitoredChannel channel(ConsentState state, Instant announcedAt) {
        SlackMonitoredChannel c = new SlackMonitoredChannel();
        c.setId(1L);
        c.setWorkspaceId(WS);
        c.setSlackTeamId("T1");
        c.setSlackChannelId(CHANNEL);
        c.setConsentState(state);
        c.setConsentAnnouncedAt(announcedAt);
        return c;
    }

    private void stubChannel(SlackMonitoredChannel c) {
        when(monitoredChannelRepository.findByWorkspaceIdAndSlackChannelId(WS, CHANNEL)).thenReturn(Optional.of(c));
    }

    private void stubActor(long actorId) {
        User user = new User();
        user.setId(actorId);
        when(userRepository.getCurrentUser()).thenReturn(Optional.of(user));
    }

    @Test
    void pendingToActive_stampsAnnouncedAt_postsAnnouncement_andWritesAudit() {
        SlackMonitoredChannel c = channel(ConsentState.PENDING, null);
        stubChannel(c);
        stubActor(5L);

        SlackMonitoredChannelDTO dto = service().transition(WS, CHANNEL, ConsentState.ACTIVE, "pilot go");

        // Announcement posted + forward-only boundary stamped + state advanced.
        verify(slackMessageService).sendForWorkspace(eq(WS), eq(CHANNEL), anyList(), anyString());
        assertThat(c.getConsentAnnouncedAt()).isNotNull();
        assertThat(c.getConsentState()).isEqualTo(ConsentState.ACTIVE);
        verify(monitoredChannelRepository).save(c);
        assertThat(dto.consentState()).isEqualTo(ConsentState.ACTIVE);

        // Audit row: PENDING → ACTIVE by actor 5 with the reason.
        ArgumentCaptor<SlackChannelConsentEvent> captor = ArgumentCaptor.forClass(SlackChannelConsentEvent.class);
        verify(consentEventRepository).save(captor.capture());
        SlackChannelConsentEvent event = captor.getValue();
        assertThat(event.getFromState()).isEqualTo(ConsentState.PENDING);
        assertThat(event.getToState()).isEqualTo(ConsentState.ACTIVE);
        assertThat(event.getActorUserId()).isEqualTo(5L);
        assertThat(event.getReason()).isEqualTo("pilot go");
    }

    @Test
    void pausedToActive_doesNotReAnnounceOrRestamp() {
        Instant originalAnnouncedAt = Instant.parse("2020-01-01T00:00:00Z");
        SlackMonitoredChannel c = channel(ConsentState.PAUSED, originalAnnouncedAt);
        stubChannel(c);
        stubActor(5L);

        service().transition(WS, CHANNEL, ConsentState.ACTIVE, null);

        // Resuming an already-announced channel keeps the original boundary and never re-posts.
        verifyNoInteractions(slackMessageService);
        assertThat(c.getConsentAnnouncedAt()).isEqualTo(originalAnnouncedAt);
        assertThat(c.getConsentState()).isEqualTo(ConsentState.ACTIVE);
        verify(consentEventRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void activeToPaused_stopsIngestKeepsData_noEraseNoAnnounce() {
        SlackMonitoredChannel c = channel(ConsentState.ACTIVE, Instant.parse("2020-01-01T00:00:00Z"));
        stubChannel(c);
        stubActor(5L);

        SlackMonitoredChannelDTO dto = service().transition(WS, CHANNEL, ConsentState.PAUSED, null);

        assertThat(c.getConsentState()).isEqualTo(ConsentState.PAUSED);
        assertThat(dto.consentState()).isEqualTo(ConsentState.PAUSED);
        verify(monitoredChannelRepository).save(c);
        verifyNoInteractions(ingestService, slackMessageService);
    }

    @Test
    void revoke_erasesRawAndDerived_andAudits() {
        SlackMonitoredChannel c = channel(ConsentState.ACTIVE, Instant.parse("2020-01-01T00:00:00Z"));
        stubChannel(c);
        stubActor(5L);

        SlackMonitoredChannelDTO dto = service().transition(WS, CHANNEL, ConsentState.REVOKED, "opt-out");

        // Single erasure choke point drives the raw + derived deletion.
        verify(ingestService).eraseChannel(WS, CHANNEL);
        assertThat(dto.consentState()).isEqualTo(ConsentState.REVOKED);
        verifyNoInteractions(slackMessageService);

        ArgumentCaptor<SlackChannelConsentEvent> captor = ArgumentCaptor.forClass(SlackChannelConsentEvent.class);
        verify(consentEventRepository).save(captor.capture());
        assertThat(captor.getValue().getFromState()).isEqualTo(ConsentState.ACTIVE);
        assertThat(captor.getValue().getToState()).isEqualTo(ConsentState.REVOKED);
    }

    @Test
    void illegalEdge_pendingToPaused_throws409_withNoSideEffects() {
        SlackMonitoredChannel c = channel(ConsentState.PENDING, null);
        stubChannel(c);

        assertThatThrownBy(() -> service().transition(WS, CHANNEL, ConsentState.PAUSED, null)).isInstanceOf(
            SlackChannelConsentViolationException.class
        );

        // Guard rejects before any mutation, side effect, or audit write.
        verify(monitoredChannelRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(ingestService, slackMessageService, consentEventRepository);
    }

    @Test
    void revokedIsTerminal_toActive_throws409() {
        SlackMonitoredChannel c = channel(ConsentState.REVOKED, Instant.parse("2020-01-01T00:00:00Z"));
        stubChannel(c);

        assertThatThrownBy(() -> service().transition(WS, CHANNEL, ConsentState.ACTIVE, null)).isInstanceOf(
            SlackChannelConsentViolationException.class
        );

        verifyNoInteractions(ingestService, slackMessageService, consentEventRepository);
    }

    @Test
    void sameState_isIdempotentNoOp() {
        SlackMonitoredChannel c = channel(ConsentState.ACTIVE, Instant.parse("2020-01-01T00:00:00Z"));
        stubChannel(c);

        SlackMonitoredChannelDTO dto = service().transition(WS, CHANNEL, ConsentState.ACTIVE, null);

        assertThat(dto.consentState()).isEqualTo(ConsentState.ACTIVE);
        // No transition happened: no side effect, no audit, no save.
        verify(monitoredChannelRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(ingestService, slackMessageService, consentEventRepository);
    }

    @Test
    void channelNotFound_throwsEntityNotFound() {
        when(monitoredChannelRepository.findByWorkspaceIdAndSlackChannelId(WS, CHANNEL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().transition(WS, CHANNEL, ConsentState.ACTIVE, null)).isInstanceOf(
            EntityNotFoundException.class
        );

        verifyNoInteractions(ingestService, slackMessageService, consentEventRepository);
    }
}
