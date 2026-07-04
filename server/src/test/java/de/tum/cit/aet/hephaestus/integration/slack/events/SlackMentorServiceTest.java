package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorTurnRequest;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorTurnRunner;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackMentorIdentityResolver;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Mentor-flow classification unit tests: an obvious-abuse diversion (self-harm / harassment) short-circuits before
 * any developer lookup or turn, and the per-user turn cap posts a friendly message instead of running a second turn.
 * Uses the real {@link ObviousAbuseFastPathSlackSafetyClassifier} and a real {@link SlackMentorQuotaGuard} so the
 * observable behaviour — not mock wiring — is under test.
 */
class SlackMentorServiceTest extends BaseUnitTest {

    private static final long WORKSPACE = 42L;
    private static final String TEAM = "T1";
    private static final String CHANNEL = "D9";
    private static final String USER = "U1";

    @Mock
    private SlackWorkspaceResolver workspaceResolver;

    @Mock
    private MentorSlackThreadLinker threadLinker;

    @Mock
    private MentorTurnRunner mentorTurnRunner;

    @Mock
    private SlackMessageService slackMessageService;

    @Mock
    private SlackMentorIdentityResolver identityResolver;

    @Mock
    private de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMentorDailyBudgetRepository dailyBudgetRepository;

    private SlackMentorQuotaGuard quotaGuard(int perUserCap) {
        return new SlackMentorQuotaGuard(dailyBudgetRepository, perUserCap, 1000, Clock.systemUTC());
    }

    private SlackMentorService service(SlackMentorQuotaGuard quotaGuard) {
        return new SlackMentorService(
            workspaceResolver,
            threadLinker,
            mentorTurnRunner,
            slackMessageService,
            identityResolver,
            quotaGuard,
            new ObviousAbuseFastPathSlackSafetyClassifier()
        );
    }

    @Test
    void selfHarmMessage_postsSupportResponse_andNeverMentors() {
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.of(WORKSPACE));
        SlackMentorService service = service(quotaGuard(50));

        service.handleDm(TEAM, CHANNEL, USER, "I want to kill myself", "100.1");

        verify(slackMessageService).sendForWorkspace(
            eq(WORKSPACE),
            eq(CHANNEL),
            eq(List.of()),
            eq(ObviousAbuseFastPathSlackSafetyClassifier.SELF_HARM_RESPONSE)
        );
        verify(mentorTurnRunner, never()).run(any(), any(), any());
        // Diverted before any identity resolution — the classifier applies even to an unlinked sender.
        verifyNoInteractions(identityResolver);
    }

    @Test
    void overUserCap_postsFriendlyMessage_andRunsOnlyTheFirstTurn() {
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.of(WORKSPACE));
        when(identityResolver.resolveDeveloperLogin(WORKSPACE, TEAM, USER)).thenReturn(Optional.of("alice"));
        when(threadLinker.findOrCreateThread(WORKSPACE, TEAM, CHANNEL, USER, "alice")).thenReturn(UUID.randomUUID());
        // Shared fleet budget always has room here; the per-user cap of 1 is what caps the second turn.
        when(
            dailyBudgetRepository.tryConsume(any(java.time.LocalDate.class), org.mockito.ArgumentMatchers.anyInt())
        ).thenReturn(1);

        SlackMentorService service = service(quotaGuard(1));

        service.handleDm(TEAM, CHANNEL, USER, "how is my review practice?", "100.1");
        service.handleDm(TEAM, CHANNEL, USER, "and my PR practice?", "100.2");

        // Exactly one real turn ran; the second was capped.
        verify(mentorTurnRunner, times(1)).run(any(MentorTurnRequest.class), any(), eq("alice"));
        // The cap posts a friendly reply rather than throwing.
        verify(slackMessageService).sendForWorkspace(eq(WORKSPACE), eq(CHANNEL), eq(List.of()), any(String.class));
    }
}
