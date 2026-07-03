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
    private SlackMessageRepository messageRepository;

    @Mock
    private SlackThreadRepository threadRepository;

    @Mock
    private SlackMentorIdentityResolver identityResolver;

    @Mock
    private ConversationFeedbackErasure conversationFeedbackErasure;

    private SlackIngestService service;

    /** Construct the service with the channel-ingest capability flag in the given state. */
    private SlackIngestService serviceWithFlag(boolean conversationIngestEnabled) {
        return new SlackIngestService(
            workspaceResolver,
            monitoredChannelRepository,
            consentGate,
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
    void activeChannel_stampsResolvedMemberIdAndUpsertsThread() {
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.of(7L));
        when(consentGate.ingestAllowed(7L, "C1")).thenReturn(true);
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
