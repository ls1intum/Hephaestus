package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackMentorIdentityResolver;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Ingest write-path unit tests. Deterministic: the repositories and the identity resolver are mocked, so
 * these lock the consent gate (only ACTIVE channels flow content), the author→member firewall stamp, and the
 * thread upsert bookkeeping — the behavioral break from the old "auto-PENDING then persist unconditionally" path.
 */
class SlackIngestServiceTest extends BaseUnitTest {

    @Mock
    private SlackWorkspaceResolver workspaceResolver;

    @Mock
    private SlackMonitoredChannelRepository monitoredChannelRepository;

    @Mock
    private SlackChannelConsentGate consentGate;

    @Mock
    private SlackParticipantConsentGate participantConsentGate;

    @Mock
    private SlackMessageRepository messageRepository;

    @Mock
    private SlackThreadRepository threadRepository;

    @Mock
    private SlackMentorIdentityResolver identityResolver;

    @Mock
    private ConversationFeedbackErasure conversationFeedbackErasure;

    /** A consent-announcement stamp well before the test messages (ts "100.1"), so the forward-only gate passes. */
    private static final Instant ANNOUNCED_BEFORE = Instant.ofEpochSecond(1);

    private SlackIngestService service;

    /** Construct the service with the channel-ingest capability flag in the given state. */
    private SlackIngestService serviceWithFlag(boolean conversationIngestEnabled) {
        return new SlackIngestService(
            workspaceResolver,
            monitoredChannelRepository,
            consentGate,
            participantConsentGate,
            messageRepository,
            threadRepository,
            identityResolver,
            conversationFeedbackErasure,
            conversationIngestEnabled
        );
    }

    @BeforeEach
    void setUp() {
        // Existing behavior is exercised with the capability ENABLED; the disabled-by-default gate is covered
        // explicitly by flagOff_* below.
        service = serviceWithFlag(true);
    }

    @Test
    void flagOff_channelMessage_isCompletelyDormant_storesNothingAndDoesNotEvenDiscover() {
        // Off-by-default capability gate (fail-closed layer 1). Even a well-formed message on what would be an
        // ACTIVE channel must not touch any collaborator: no workspace resolution, no discovery row, no consent
        // check, no store. This is the explicit, operator-controlled parked state — remove the flag gate and this
        // test fails (resolveWorkspaceId would be called before the consent gate could apply).
        SlackIngestService disabled = serviceWithFlag(false);

        disabled.ingestChannelMessage("T1", "C1", "100.1", "99.0", "U1", "hi");

        verifyNoInteractions(
            workspaceResolver,
            monitoredChannelRepository,
            consentGate,
            participantConsentGate,
            messageRepository,
            threadRepository,
            identityResolver
        );
    }

    @Test
    void unknownTeam_persistsNothing() {
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.empty());

        service.ingestChannelMessage("T1", "C1", "100.1", null, "U1", "hi");

        verify(monitoredChannelRepository, never()).insertIfAbsent(
            org.mockito.ArgumentMatchers.anyLong(),
            any(),
            any()
        );
        verify(messageRepository, never()).insertIfAbsent(
            org.mockito.ArgumentMatchers.anyLong(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void pendingChannel_registersButDoesNotIngest() {
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.of(7L));
        when(consentGate.ingestAllowed(7L, "C1")).thenReturn(false);

        service.ingestChannelMessage("T1", "C1", "100.1", null, "U1", "hi");

        // Discovery row is still created (so an admin can approve it later)…
        verify(monitoredChannelRepository).insertIfAbsent(7L, "T1", "C1");
        // …but the message and thread are NOT persisted until consent is ACTIVE.
        verify(messageRepository, never()).insertIfAbsent(
            org.mockito.ArgumentMatchers.anyLong(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
        verify(threadRepository, never()).upsertOnMessage(
            org.mockito.ArgumentMatchers.anyLong(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void activeChannel_authorOptedOut_isNotStored_evenOnActiveChannel() {
        // The person firewall (the #1-defect fix): capability ON + channel ACTIVE, but this individual opted out of
        // ingestion → nothing is stored. Remove the firewall and this test fails (the message would be inserted).
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.of(7L));
        when(consentGate.ingestAllowed(7L, "C1")).thenReturn(true);
        when(monitoredChannelRepository.findConsentAnnouncedAt(7L, "C1")).thenReturn(Optional.of(ANNOUNCED_BEFORE));
        when(participantConsentGate.ingestionAllowed(7L, "U1")).thenReturn(false);

        service.ingestChannelMessage("T1", "C1", "100.1", "99.0", "U1", "hi");

        // Discovery still happens; the store does not. The identity resolver is never even consulted.
        verify(monitoredChannelRepository).insertIfAbsent(7L, "T1", "C1");
        verify(messageRepository, never()).insertIfAbsent(
            org.mockito.ArgumentMatchers.anyLong(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
        verify(threadRepository, never()).upsertOnMessage(
            org.mockito.ArgumentMatchers.anyLong(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void activeChannel_preAnnouncementMessage_isNotStored_andPersonGateNotEvenConsulted() {
        // Forward-only invariant: on an ACTIVE channel, a message whose ts predates consent_announced_at is never
        // stored — pre-announcement history stays out. The check fires BEFORE the person firewall, so the participant
        // gate is not even consulted. Remove the forward-only guard and this test fails (the message would be stored).
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.of(7L));
        when(consentGate.ingestAllowed(7L, "C1")).thenReturn(true);
        // Announcement is AFTER the message ts (100.1) → the message predates consent and must not enter.
        when(monitoredChannelRepository.findConsentAnnouncedAt(7L, "C1")).thenReturn(
            Optional.of(Instant.ofEpochSecond(200))
        );

        service.ingestChannelMessage("T1", "C1", "100.1", null, "U1", "old");

        verifyNoInteractions(participantConsentGate);
        verify(messageRepository, never()).insertIfAbsent(
            org.mockito.ArgumentMatchers.anyLong(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void activeChannel_missingAnnouncementStamp_failsClosed() {
        // An ACTIVE channel is always stamped at activation; a missing stamp is an inconsistency and must fail closed
        // (store nothing) rather than fall through to ingest unbounded history.
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.of(7L));
        when(consentGate.ingestAllowed(7L, "C1")).thenReturn(true);
        when(monitoredChannelRepository.findConsentAnnouncedAt(7L, "C1")).thenReturn(Optional.empty());

        service.ingestChannelMessage("T1", "C1", "100.1", null, "U1", "hi");

        verifyNoInteractions(participantConsentGate);
        verify(messageRepository, never()).insertIfAbsent(
            org.mockito.ArgumentMatchers.anyLong(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void activeChannel_stampsResolvedMemberIdAndUpsertsThread() {
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.of(7L));
        when(consentGate.ingestAllowed(7L, "C1")).thenReturn(true);
        when(monitoredChannelRepository.findConsentAnnouncedAt(7L, "C1")).thenReturn(Optional.of(ANNOUNCED_BEFORE));
        when(participantConsentGate.ingestionAllowed(7L, "U1")).thenReturn(true);
        when(identityResolver.resolveMemberId(7L, "T1", "U1")).thenReturn(Optional.of(42L));
        when(messageRepository.insertIfAbsent(7L, "T1", "C1", "100.1", "99.0", "U1", 42L, "hi")).thenReturn(1);

        service.ingestChannelMessage("T1", "C1", "100.1", "99.0", "U1", "hi");

        verify(messageRepository).insertIfAbsent(7L, "T1", "C1", "100.1", "99.0", "U1", 42L, "hi");
        // thread_ts is the reply's parent (99.0); the new message's own ts (100.1) advances the window.
        verify(threadRepository).upsertOnMessage(7L, "C1", "99.0", "100.1", 42L);
    }

    @Test
    void activeChannel_rootMessageUsesOwnTsAsThreadTs() {
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.of(7L));
        when(consentGate.ingestAllowed(7L, "C1")).thenReturn(true);
        when(monitoredChannelRepository.findConsentAnnouncedAt(7L, "C1")).thenReturn(Optional.of(ANNOUNCED_BEFORE));
        when(participantConsentGate.ingestionAllowed(7L, "U1")).thenReturn(true);
        when(identityResolver.resolveMemberId(7L, "T1", "U1")).thenReturn(Optional.empty());
        when(
            messageRepository.insertIfAbsent(
                eq(7L),
                eq("T1"),
                eq("C1"),
                eq("100.1"),
                isNull(),
                eq("U1"),
                isNull(),
                eq("hi")
            )
        ).thenReturn(1);

        service.ingestChannelMessage("T1", "C1", "100.1", null, "U1", "hi");

        // Root (no thread_ts) → aggregate keyed on its own ts; unlinked author stamps a null member id.
        verify(threadRepository).upsertOnMessage(7L, "C1", "100.1", "100.1", null);
    }

    @Test
    void eraseChannel_revokesConsentErasesDerivedFeedbackAndDeletesStoredContentPromptly() {
        // The channel's thread ids are collected first (the derived practice rows are keyed by slack_thread.id).
        when(threadRepository.findIdsByWorkspaceIdAndSlackChannelId(7L, "C1")).thenReturn(List.of(11L, 22L));

        service.eraseChannel(7L, "C1");

        // Consent flip stops future ingestion + drops threads out of the ACTIVE-consent projectors…
        verify(monitoredChannelRepository).revokeConsent(7L, "C1");
        // …the derived CONVERSATION_THREAD observations/feedback are hard-deleted through the practices port
        // (true erasure, not inert-by-gate)…
        verify(conversationFeedbackErasure).eraseForThreads(7L, List.of(11L, 22L));
        // …and the channel's raw content (messages) + thread aggregates are deleted now, not left for the sweep.
        verify(messageRepository).deleteByWorkspaceIdAndSlackChannelId(7L, "C1");
        verify(threadRepository).deleteByWorkspaceIdAndSlackChannelId(7L, "C1");
    }

    @Test
    void duplicateMessage_doesNotBumpThread() {
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.of(7L));
        when(consentGate.ingestAllowed(7L, "C1")).thenReturn(true);
        when(monitoredChannelRepository.findConsentAnnouncedAt(7L, "C1")).thenReturn(Optional.of(ANNOUNCED_BEFORE));
        when(participantConsentGate.ingestionAllowed(7L, "U1")).thenReturn(true);
        when(identityResolver.resolveMemberId(7L, "T1", "U1")).thenReturn(Optional.of(42L));
        when(messageRepository.insertIfAbsent(7L, "T1", "C1", "100.1", null, "U1", 42L, "hi")).thenReturn(0);

        service.ingestChannelMessage("T1", "C1", "100.1", null, "U1", "hi");

        // Idempotent retry: the message already existed (0 inserted) → no double thread bookkeeping.
        verify(threadRepository, never()).upsertOnMessage(
            org.mockito.ArgumentMatchers.anyLong(),
            any(),
            any(),
            any(),
            any()
        );
    }
}
