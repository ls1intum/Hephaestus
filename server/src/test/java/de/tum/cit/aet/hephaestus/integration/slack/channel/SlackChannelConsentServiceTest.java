package de.tum.cit.aet.hephaestus.integration.slack.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
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
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

        // Announcement posted as non-empty Block Kit (the one-click opt-out) with the plain-language fallback +
        // forward-only boundary stamped + state advanced.
        ArgumentCaptor<java.util.List<com.slack.api.model.block.LayoutBlock>> blocksCaptor = ArgumentCaptor.forClass(
            java.util.List.class
        );
        verify(slackMessageService).sendForWorkspace(
            eq(WS),
            eq(CHANNEL),
            blocksCaptor.capture(),
            eq(SlackConsentBlocks.FALLBACK_TEXT)
        );
        assertThat(blocksCaptor.getValue()).isNotEmpty();
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

    // --- #6: the full 4×4 legality matrix, locked in one method ---

    private enum Outcome {
        /** {@code from == target}: idempotent no-op, no side effect, no audit. */
        NOOP,
        /** A permitted edge: performs its side effect and writes an audit row. */
        LEGAL,
        /** A forbidden edge: 409, no mutation, no audit. */
        ILLEGAL,
    }

    static Stream<Arguments> edgeMatrix() {
        return Stream.of(
            Arguments.of(ConsentState.PENDING, ConsentState.PENDING, Outcome.NOOP),
            Arguments.of(ConsentState.PENDING, ConsentState.ACTIVE, Outcome.LEGAL),
            Arguments.of(ConsentState.PENDING, ConsentState.PAUSED, Outcome.ILLEGAL),
            Arguments.of(ConsentState.PENDING, ConsentState.REVOKED, Outcome.LEGAL),
            Arguments.of(ConsentState.ACTIVE, ConsentState.PENDING, Outcome.ILLEGAL),
            Arguments.of(ConsentState.ACTIVE, ConsentState.ACTIVE, Outcome.NOOP),
            Arguments.of(ConsentState.ACTIVE, ConsentState.PAUSED, Outcome.LEGAL),
            Arguments.of(ConsentState.ACTIVE, ConsentState.REVOKED, Outcome.LEGAL),
            Arguments.of(ConsentState.PAUSED, ConsentState.PENDING, Outcome.ILLEGAL),
            Arguments.of(ConsentState.PAUSED, ConsentState.ACTIVE, Outcome.LEGAL),
            Arguments.of(ConsentState.PAUSED, ConsentState.PAUSED, Outcome.NOOP),
            Arguments.of(ConsentState.PAUSED, ConsentState.REVOKED, Outcome.LEGAL),
            Arguments.of(ConsentState.REVOKED, ConsentState.PENDING, Outcome.ILLEGAL),
            Arguments.of(ConsentState.REVOKED, ConsentState.ACTIVE, Outcome.ILLEGAL),
            Arguments.of(ConsentState.REVOKED, ConsentState.PAUSED, Outcome.ILLEGAL),
            Arguments.of(ConsentState.REVOKED, ConsentState.REVOKED, Outcome.NOOP)
        );
    }

    /**
     * Locks the whole mentoring-only consent state machine (16 cells) in one place. The load-bearing new cells are
     * {@code PENDING → REVOKED} and {@code PAUSED → REVOKED}: dropping {@code || REVOKED} from the PENDING/PAUSED
     * guard arms turns those into 409s instead of the GDPR Art. 17 erase — this asserts they are LEGAL and drive
     * {@link SlackIngestService#eraseChannel}. Every REVOKED-target legal edge must erase; no other legal edge may.
     */
    @ParameterizedTest(name = "{0} → {1} is {2}")
    @MethodSource("edgeMatrix")
    void consentStateMachine_fullEdgeMatrix(ConsentState from, ConsentState target, Outcome outcome) {
        SlackMonitoredChannel c = channel(
            from,
            from == ConsentState.PENDING ? null : Instant.parse("2020-01-01T00:00:00Z")
        );
        stubChannel(c);
        SlackChannelConsentService svc = service();

        switch (outcome) {
            case NOOP -> {
                SlackMonitoredChannelDTO dto = svc.transition(WS, CHANNEL, target, null);
                assertThat(dto.consentState()).isEqualTo(from);
                verify(monitoredChannelRepository, never()).save(org.mockito.ArgumentMatchers.any());
                verifyNoInteractions(ingestService, consentEventRepository, slackMessageService);
            }
            case ILLEGAL -> {
                assertThatThrownBy(() -> svc.transition(WS, CHANNEL, target, null)).isInstanceOf(
                    SlackChannelConsentViolationException.class
                );
                verifyNoInteractions(ingestService, consentEventRepository);
            }
            case LEGAL -> {
                stubActor(5L);
                svc.transition(WS, CHANNEL, target, "reason");
                // Erasure fires iff the target is REVOKED — the cell that kills the dropped-`|| REVOKED` guard mutant.
                if (target == ConsentState.REVOKED) {
                    verify(ingestService).eraseChannel(WS, CHANNEL);
                } else {
                    verifyNoInteractions(ingestService);
                }
                ArgumentCaptor<SlackChannelConsentEvent> captor = ArgumentCaptor.forClass(
                    SlackChannelConsentEvent.class
                );
                verify(consentEventRepository).save(captor.capture());
                assertThat(captor.getValue().getFromState()).isEqualTo(from);
                assertThat(captor.getValue().getToState()).isEqualTo(target);
            }
        }
    }

    @Test
    void activate_whenAnnouncementFails_stillActivatesStampsAndAudits() {
        // A Slack-side posting failure (not_in_channel / no token) is best-effort: it must NOT roll the @Transactional
        // activation back. Remove the try/catch around the announcement and the exception propagates → the channel is
        // permanently un-activatable whenever Slack posting fails. Also pins stamp-before-post ordering (the stamp is
        // set on the entity before the failing post, so it survives).
        SlackMonitoredChannel c = channel(ConsentState.PENDING, null);
        stubChannel(c);
        stubActor(5L);
        doThrow(new SlackSendException(WS, CHANNEL, "not_in_channel"))
            .when(slackMessageService)
            .sendForWorkspace(eq(WS), eq(CHANNEL), anyList(), anyString());

        SlackMonitoredChannelDTO dto = service().transition(WS, CHANNEL, ConsentState.ACTIVE, "go");

        // No propagation: the transition completed, ACTIVE + stamped, and the audit row was written.
        assertThat(dto.consentState()).isEqualTo(ConsentState.ACTIVE);
        assertThat(c.getConsentState()).isEqualTo(ConsentState.ACTIVE);
        assertThat(c.getConsentAnnouncedAt()).isNotNull();
        verify(monitoredChannelRepository).save(c);
        verify(consentEventRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void channelNotFound_throwsEntityNotFound() {
        when(monitoredChannelRepository.findByWorkspaceIdAndSlackChannelId(WS, CHANNEL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().transition(WS, CHANNEL, ConsentState.ACTIVE, null)).isInstanceOf(
            EntityNotFoundException.class
        );

        verifyNoInteractions(ingestService, slackMessageService, consentEventRepository);
    }

    // --- #8: register() (the whole endpoint had zero tests) ---

    @Test
    void register_new_landsInPending_created() {
        when(monitoredChannelRepository.findByWorkspaceIdAndSlackChannelId(WS, CHANNEL)).thenReturn(Optional.empty());
        when(connectionService.findSlackNotificationConfig(WS)).thenReturn(
            Optional.of(new ConnectionConfig.SlackConfig("T1", null, null, null, null, Set.of()))
        );
        when(monitoredChannelRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        SlackChannelConsentService.RegistrationOutcome outcome = service().register(WS, CHANNEL, "general");

        assertThat(outcome.created()).isTrue();
        assertThat(outcome.channel().consentState()).isEqualTo(ConsentState.PENDING);
        verify(monitoredChannelRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void register_existing_backfillsName_returnsNotCreated() {
        // Idempotent on the natural key: a second registration returns the existing row (created=false), backfilling a
        // previously-unknown channelName. Kills an always-create mutant (which would ignore the existing row, consult
        // the connection, and INSERT a duplicate that trips uk_slack_monitored_channel → 500).
        SlackMonitoredChannel existing = channel(ConsentState.PENDING, null);
        existing.setChannelName(null);
        stubChannel(existing);

        SlackChannelConsentService.RegistrationOutcome outcome = service().register(WS, CHANNEL, "general");

        assertThat(outcome.created()).isFalse();
        assertThat(existing.getChannelName()).isEqualTo("general");
        verify(monitoredChannelRepository).save(existing);
        // The existing branch returns before the connection lookup — an always-create mutant would consult it.
        verifyNoInteractions(connectionService);
    }

    @Test
    void register_noSlackConnection_throwsNotFound() {
        // A purely-admin registration of an unseen channel needs an ACTIVE Slack connection to know the team id; its
        // absence is a 404 (the dead-untested branch at SlackChannelConsentService#register).
        when(monitoredChannelRepository.findByWorkspaceIdAndSlackChannelId(WS, CHANNEL)).thenReturn(Optional.empty());
        when(connectionService.findSlackNotificationConfig(WS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().register(WS, CHANNEL, "general")).isInstanceOf(
            EntityNotFoundException.class
        );

        verify(monitoredChannelRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
