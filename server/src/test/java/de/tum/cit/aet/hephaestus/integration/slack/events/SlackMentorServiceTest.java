package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackOnboardingService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

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
    private SlackOnboardingService onboardingService;

    private SlackMentorService service() {
        return new SlackMentorService(
            workspaceResolver,
            threadLinker,
            mentorTurnRunner,
            slackMessageService,
            identityResolver,
            new KeywordSlackMentorInputGuard(),
            onboardingService
        );
    }

    @Test
    void selfHarmMessage_postsSupportResponse_andNeverMentors() {
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.of(WORKSPACE));
        SlackMentorService service = service();

        service.handleDm(TEAM, CHANNEL, USER, "I want to kill myself", "100.1", "100.1");

        verify(slackMessageService).sendForWorkspace(
            eq(WORKSPACE),
            eq(CHANNEL),
            eq("100.1"),
            eq(List.of()),
            eq(KeywordSlackMentorInputGuard.SELF_HARM_RESPONSE)
        );
        verify(mentorTurnRunner, never()).run(any(), any(), any());
        // Diverted before any identity resolution — the classifier applies even to an unlinked sender.
        verifyNoInteractions(identityResolver);
    }

    @Test
    void harassmentMessage_isIgnoredWithoutPostingBotSpeak_andNeverMentors() {
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.of(WORKSPACE));
        SlackMentorService service = service();

        service.handleDm(TEAM, CHANNEL, USER, "stupid bot", "100.1", "100.1");

        verify(slackMessageService, never()).sendForWorkspace(
            anyLong(),
            anyString(),
            anyString(),
            anyList(),
            anyString()
        );
        verify(mentorTurnRunner, never()).run(any(), any(), any());
        verifyNoInteractions(identityResolver);
    }

    @Test
    void unlinkedMember_getsSelfServeAccountLinkCta() {
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.of(WORKSPACE));
        when(identityResolver.resolveDeveloperLogin(WORKSPACE, TEAM, USER)).thenReturn(Optional.empty());
        when(onboardingService.linkCtaBlocks()).thenReturn(List.of());

        SlackMentorService service = service();

        service.handleDm(TEAM, CHANNEL, USER, "Can you review my PR practice?", "100.1", "100.1");

        verify(slackMessageService).sendForWorkspace(
            eq(WORKSPACE),
            eq(CHANNEL),
            eq("100.1"),
            eq(List.of()),
            eq("Connect your Slack account to Hephaestus so the mentor can find your work.")
        );
        verify(mentorTurnRunner, never()).run(any(), any(), any());
    }

    @Test
    void linkedMemberMessages_runMentorTurnsWithoutSlackSpecificQuota() {
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.of(WORKSPACE));
        when(identityResolver.resolveDeveloperLogin(WORKSPACE, TEAM, USER)).thenReturn(Optional.of("alice"));
        UUID threadId = UUID.randomUUID();
        when(threadLinker.findOrCreateThread(WORKSPACE, TEAM, CHANNEL, "100.1", USER, "alice")).thenReturn(threadId);
        when(threadLinker.findOrCreateThread(WORKSPACE, TEAM, CHANNEL, "100.2", USER, "alice")).thenReturn(threadId);

        SlackMentorService service = service();

        service.handleDm(TEAM, CHANNEL, USER, "how is my review practice?", "100.1", "100.1");
        service.handleDm(TEAM, CHANNEL, USER, "and my PR practice?", "100.2", "100.2");

        ArgumentCaptor<MentorTurnRequest> requestCaptor = ArgumentCaptor.forClass(MentorTurnRequest.class);
        verify(mentorTurnRunner, times(2)).run(requestCaptor.capture(), any(), eq("alice"));
        assertThat(requestCaptor.getAllValues()).allSatisfy(request ->
            assertThat(request.threadId()).isEqualTo(threadId)
        );
        assertThat(
            requestCaptor.getAllValues().stream().map(MentorTurnRequest::clientUserMessageId).toList()
        ).containsExactly(
            UUID.nameUUIDFromBytes("slack:T1:D9:100.1".getBytes(StandardCharsets.UTF_8)),
            UUID.nameUUIDFromBytes("slack:T1:D9:100.2".getBytes(StandardCharsets.UTF_8))
        );
    }
}
