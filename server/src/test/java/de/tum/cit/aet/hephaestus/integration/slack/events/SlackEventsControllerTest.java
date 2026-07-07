package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackOnboardingService;
import de.tum.cit.aet.hephaestus.integration.slack.webhook.SlackChannelEventPublisher;
import de.tum.cit.aet.hephaestus.integration.slack.webhook.SlackChannelEventPublisher.PublishOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

/**
 * Controller-level classification tests. The signature verifier is mocked to pass. The controller's job now is a
 * thin verify → fast-classify: a monitored-channel message goes to {@link SlackChannelEventPublisher} (durable
 * transport) while every interactive event flows in-process through {@link SlackEventDispatcher}. The
 * channel-message SUBTYPE routing (edit/delete/plain) moved to {@code SlackChannelMessageHandler} and is tested
 * there.
 */
class SlackEventsControllerTest extends BaseUnitTest {

    @Mock
    private SlackSignatureVerifier verifier;

    @Mock
    private SlackMentorService mentorService;

    @Mock
    private SlackOnboardingService onboardingService;

    @Mock
    private de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackAppHomeService appHomeService;

    @Mock
    private SlackAssistantEventHandler assistantEventHandler;

    @Mock
    private SlackUninstallService uninstallService;

    @Mock
    private SlackChannelEventPublisher channelEventPublisher;

    private SlackEventsController controller;

    @BeforeEach
    void setUp() {
        // Real dispatcher over the mocked interactive handlers; same-thread executors so routing asserts run
        // synchronously. The publisher is mocked; default NOT_CHANNEL_MESSAGE routes everything to the dispatcher,
        // and individual tests override it for the channel-message path.
        SlackEventDispatcher dispatcher = new SlackEventDispatcher(
            mentorService,
            onboardingService,
            appHomeService,
            assistantEventHandler,
            uninstallService,
            Runnable::run,
            Runnable::run
        );
        controller = new SlackEventsController(
            verifier,
            dispatcher,
            channelEventPublisher,
            JsonMapper.builder().build()
        );
        when(verifier.verify(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        lenient()
            .when(channelEventPublisher.publishIfChannelMessage(any(), any()))
            .thenReturn(PublishOutcome.NOT_CHANNEL_MESSAGE);
    }

    private ResponseEntity<String> post(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Slack-Request-Timestamp", "1");
        headers.add("X-Slack-Signature", "v0=deadbeef");
        return controller.events(body.getBytes(StandardCharsets.UTF_8), headers);
    }

    @Test
    void channelMessage_isPublishedToDurableTransport_notDispatchedInProcess() {
        when(channelEventPublisher.publishIfChannelMessage(any(), any())).thenReturn(PublishOutcome.PUBLISHED);

        ResponseEntity<String> res = post(
            """
            {"type":"event_callback","team_id":"T1","event":{
              "type":"message","channel_type":"channel","channel":"C1","user":"U1","ts":"100.1","thread_ts":"100.0","text":"hello"}}
            """
        );

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        // Passive channel content never touches the in-process interactive handlers.
        verifyNoInteractions(mentorService);
    }

    @Test
    void channelMessage_whenPublisherUnavailable_returns503_soSlackRedelivers() {
        when(channelEventPublisher.publishIfChannelMessage(any(), any())).thenReturn(
            PublishOutcome.PUBLISHER_UNAVAILABLE
        );

        ResponseEntity<String> res = post(
            """
            {"type":"event_callback","team_id":"T1","event":{
              "type":"message","channel_type":"channel","channel":"C1","user":"U1","ts":"100.1","text":"hello"}}
            """
        );

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void channelMessage_whenPublishFailed_returns503_soSlackRedelivers() {
        when(channelEventPublisher.publishIfChannelMessage(any(), any())).thenReturn(PublishOutcome.PUBLISH_FAILED);

        ResponseEntity<String> res = post(
            """
            {"type":"event_callback","team_id":"T1","event":{
              "type":"message","channel_type":"channel","channel":"C1","user":"U1","ts":"100.1","text":"hello"}}
            """
        );

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void directMessage_acksBeforeRunningMentorWork_soNoSlackApiOrLlmCallPrecedesThe200() {
        // A capturing executor queues the offloaded task WITHOUT running it, so we can observe the ACK first.
        List<Runnable> queued = new ArrayList<>();
        SlackEventDispatcher asyncDispatcher = new SlackEventDispatcher(
            mentorService,
            onboardingService,
            appHomeService,
            assistantEventHandler,
            uninstallService,
            queued::add,
            queued::add
        );
        SlackEventsController asyncController = new SlackEventsController(
            verifier,
            asyncDispatcher,
            channelEventPublisher,
            JsonMapper.builder().build()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Slack-Request-Timestamp", "1");
        headers.add("X-Slack-Signature", "v0=deadbeef");
        ResponseEntity<String> res = asyncController.events(
            """
            {"type":"event_callback","team_id":"T1","event":{
              "type":"message","channel_type":"im","channel":"D9","user":"U1","ts":"100.1","text":"hi heph"}}
            """.getBytes(StandardCharsets.UTF_8),
            headers
        );

        // The 200 is returned while the mentor turn (its setStatus Slack call + LLM work) is still only QUEUED.
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verifyNoInteractions(mentorService);
        assertThat(queued).hasSize(1);

        // Draining the executor runs the offloaded mentor work — proving it was deferred, not skipped.
        queued.forEach(Runnable::run);
        verify(mentorService).handleDm("T1", "D9", "U1", "hi heph", "100.1");
    }

    @Test
    void appHomeOpened_isOffloaded_soNoSlackApiCallPrecedesThe200() {
        List<Runnable> queued = new ArrayList<>();
        SlackEventDispatcher asyncDispatcher = new SlackEventDispatcher(
            mentorService,
            onboardingService,
            appHomeService,
            assistantEventHandler,
            uninstallService,
            queued::add,
            queued::add
        );
        SlackEventsController asyncController = new SlackEventsController(
            verifier,
            asyncDispatcher,
            channelEventPublisher,
            JsonMapper.builder().build()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Slack-Request-Timestamp", "1");
        headers.add("X-Slack-Signature", "v0=deadbeef");

        ResponseEntity<String> res = asyncController.events(
            """
            {"type":"event_callback","team_id":"T1","event":{
              "type":"app_home_opened","tab":"home","user":"U1"}}
            """.getBytes(StandardCharsets.UTF_8),
            headers
        );

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verifyNoInteractions(appHomeService, onboardingService);
        assertThat(queued).hasSize(1);

        queued.forEach(Runnable::run);
        verify(appHomeService).onHomeOpened("T1", "U1");
        verify(onboardingService).onHomeOpened("T1", "U1");
    }

    @Test
    void assistantThreadStarted_isOffloaded_soNoSlackApiCallPrecedesThe200() {
        List<Runnable> queued = new ArrayList<>();
        SlackEventDispatcher asyncDispatcher = new SlackEventDispatcher(
            mentorService,
            onboardingService,
            appHomeService,
            assistantEventHandler,
            uninstallService,
            queued::add,
            queued::add
        );
        SlackEventsController asyncController = new SlackEventsController(
            verifier,
            asyncDispatcher,
            channelEventPublisher,
            JsonMapper.builder().build()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Slack-Request-Timestamp", "1");
        headers.add("X-Slack-Signature", "v0=deadbeef");

        ResponseEntity<String> res = asyncController.events(
            """
            {"type":"event_callback","team_id":"T1","event":{
              "type":"assistant_thread_started","assistant_thread":{"channel_id":"D9","thread_ts":"100.0"}}}
            """.getBytes(StandardCharsets.UTF_8),
            headers
        );

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verifyNoInteractions(assistantEventHandler);
        assertThat(queued).hasSize(1);

        queued.forEach(Runnable::run);
        verify(assistantEventHandler).onThreadStarted(eq("T1"), any());
    }

    @Test
    void appHomeOpened_onMessagesTab_isIgnored() {
        List<Runnable> queued = new ArrayList<>();
        SlackEventDispatcher asyncDispatcher = new SlackEventDispatcher(
            mentorService,
            onboardingService,
            appHomeService,
            assistantEventHandler,
            uninstallService,
            queued::add,
            queued::add
        );
        SlackEventsController asyncController = new SlackEventsController(
            verifier,
            asyncDispatcher,
            channelEventPublisher,
            JsonMapper.builder().build()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Slack-Request-Timestamp", "1");
        headers.add("X-Slack-Signature", "v0=deadbeef");

        asyncController.events(
            """
            {"type":"event_callback","team_id":"T1","event":{
              "type":"app_home_opened","tab":"messages","user":"U1"}}
            """.getBytes(StandardCharsets.UTF_8),
            headers
        );

        assertThat(queued).isEmpty();
        verifyNoInteractions(appHomeService, onboardingService);
    }

    @Test
    void directMessage_drivesMentorTurn() {
        post(
            """
            {"type":"event_callback","team_id":"T1","event":{
              "type":"message","channel_type":"im","channel":"D9","user":"U1","ts":"100.1","text":"hi heph"}}
            """
        );

        verify(mentorService).handleDm("T1", "D9", "U1", "hi heph", "100.1");
    }

    @Test
    void urlVerification_echoesChallenge() {
        ResponseEntity<String> res = post("{\"type\":\"url_verification\",\"challenge\":\"abc123\"}");

        assertThat(res.getBody()).isEqualTo("abc123");
    }

    @Test
    void appUninstalled_routesToUninstallService_notDroppedBySubtypeReturn() {
        post(
            """
            {"type":"event_callback","team_id":"T1","event":{"type":"app_uninstalled"}}
            """
        );

        verify(uninstallService).onUninstall("T1", "app_uninstalled");
        verifyNoInteractions(mentorService);
    }

    @Test
    void interactiveDispatchFailure_isSwallowed_returns200_soSlackDoesNotRedeliver() {
        // Uninstall is the one interactive branch that runs SYNCHRONOUSLY (not offloaded), so a throw
        // propagates to the controller's try/catch. The controller must still ACK 200 — Slack does not
        // redeliver after a 200, and a 5xx here would provoke a pointless retry storm.
        doThrow(new RuntimeException("boom")).when(uninstallService).onUninstall(eq("T1"), eq("app_uninstalled"));

        ResponseEntity<String> res = post(
            """
            {"type":"event_callback","team_id":"T1","event":{"type":"app_uninstalled"}}
            """
        );

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verify(uninstallService).onUninstall("T1", "app_uninstalled");
    }

    @Test
    void tokensRevoked_routesToUninstallService() {
        post(
            """
            {"type":"event_callback","team_id":"T1","event":{"type":"tokens_revoked"}}
            """
        );

        verify(uninstallService).onUninstall("T1", "tokens_revoked");
    }
}
