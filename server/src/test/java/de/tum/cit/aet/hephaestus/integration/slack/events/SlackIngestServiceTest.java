package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackMentorIdentityResolver;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
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

    private SlackIngestService service;

    @BeforeEach
    void setUp() {
        service = new SlackIngestService(
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
