package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackOnboardingService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

/**
 * Event-routing unit tests. The signature verifier is mocked to pass, so these lock the dispatch of the
 * {@code message} subtypes — in particular that {@code message_deleted}/{@code message_changed} are routed to the
 * tombstone/edit path BEFORE the subtype early-return that drops every other subtyped message, and key on the
 * deleted/changed message's own ts (not the event ts).
 */
class SlackEventsControllerTest extends BaseUnitTest {

    @Mock
    private SlackSignatureVerifier verifier;

    @Mock
    private SlackMentorService mentorService;

    @Mock
    private SlackIngestService ingestService;

    @Mock
    private SlackOnboardingService onboardingService;

    @Mock
    private de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackAppHomeService appHomeService;

    @Mock
    private SlackAssistantEventHandler assistantEventHandler;

    @Mock
    private SlackEventDedupService dedupService;

    @Mock
    private SlackUninstallService uninstallService;

    private SlackEventsController controller;

    @BeforeEach
    void setUp() {
        // Real dispatcher over the mocked handlers so the HTTP path still exercises end-to-end event routing;
        // the controller itself is now a thin signature-verify + dedup + ACK entry point.
        // Same-thread executor so the existing routing assertions can verify handleDm synchronously; the
        // ack-before-work ordering is proven separately with a capturing executor.
        SlackEventDispatcher dispatcher = new SlackEventDispatcher(
            mentorService,
            ingestService,
            onboardingService,
            appHomeService,
            assistantEventHandler,
            uninstallService,
            Runnable::run
        );
        controller = new SlackEventsController(verifier, dispatcher, dedupService, JsonMapper.builder().build());
        when(verifier.verify(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
    }

    private ResponseEntity<String> post(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Slack-Request-Timestamp", "1");
        headers.add("X-Slack-Signature", "v0=deadbeef");
        return controller.events(body.getBytes(StandardCharsets.UTF_8), headers);
    }

    @Test
    void channelMessage_isIngested() {
        post(
            """
            {"type":"event_callback","team_id":"T1","event":{
              "type":"message","channel_type":"channel","channel":"C1","user":"U1","ts":"100.1","thread_ts":"100.0","text":"hello"}}
            """
        );

        verify(ingestService).ingestChannelMessage("T1", "C1", "100.1", "100.0", "U1", "hello");
    }

    @Test
    void directMessage_acksBeforeRunningMentorWork_soNoSlackApiOrLlmCallPrecedesThe200() {
        // A capturing executor queues the offloaded task WITHOUT running it, so we can observe the ACK first.
        List<Runnable> queued = new ArrayList<>();
        SlackEventDispatcher asyncDispatcher = new SlackEventDispatcher(
            mentorService,
            ingestService,
            onboardingService,
            appHomeService,
            assistantEventHandler,
            uninstallService,
            queued::add
        );
        SlackEventsController asyncController = new SlackEventsController(
            verifier,
            asyncDispatcher,
            dedupService,
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
        // App Home publishes go through the rate-limit-honoring path (up to 30s Retry-After budget); running them
        // on the ACK thread would blow Slack's 3s window. A capturing executor proves they are queued, not run,
        // before the 200 — then draining it runs both the Home render and the onboarding CTA.
        List<Runnable> queued = new ArrayList<>();
        SlackEventDispatcher asyncDispatcher = new SlackEventDispatcher(
            mentorService,
            ingestService,
            onboardingService,
            appHomeService,
            assistantEventHandler,
            uninstallService,
            queued::add
        );
        SlackEventsController asyncController = new SlackEventsController(
            verifier,
            asyncDispatcher,
            dedupService,
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
            ingestService,
            onboardingService,
            appHomeService,
            assistantEventHandler,
            uninstallService,
            queued::add
        );
        SlackEventsController asyncController = new SlackEventsController(
            verifier,
            asyncDispatcher,
            dedupService,
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
        // The Messages-tab open fires the same event with tab=messages and must NOT re-render or offload anything.
        List<Runnable> queued = new ArrayList<>();
        SlackEventDispatcher asyncDispatcher = new SlackEventDispatcher(
            mentorService,
            ingestService,
            onboardingService,
            appHomeService,
            assistantEventHandler,
            uninstallService,
            queued::add
        );
        SlackEventsController asyncController = new SlackEventsController(
            verifier,
            asyncDispatcher,
            dedupService,
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
        verify(ingestService, never()).ingestChannelMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void messageDeleted_tombstonesOnDeletedTs_notEventTs() {
        ResponseEntity<String> res = post(
            """
            {"type":"event_callback","team_id":"T1","event":{
              "type":"message","subtype":"message_deleted","channel":"C1","ts":"200.9","deleted_ts":"100.1"}}
            """
        );

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verify(ingestService).tombstoneMessage("T1", "C1", "100.1");
        verify(ingestService, never()).ingestChannelMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void messageDeleted_fallsBackToPreviousMessageTs() {
        post(
            """
            {"type":"event_callback","team_id":"T1","event":{
              "type":"message","subtype":"message_deleted","channel":"C1","ts":"200.9","previous_message":{"ts":"150.2"}}}
            """
        );

        verify(ingestService).tombstoneMessage("T1", "C1", "150.2");
    }

    @Test
    void messageChanged_editsFromNestedMessage() {
        post(
            """
            {"type":"event_callback","team_id":"T1","event":{
              "type":"message","subtype":"message_changed","channel":"C1","ts":"200.9",
              "message":{"ts":"100.1","text":"edited body"}}}
            """
        );

        verify(ingestService).editMessage("T1", "C1", "100.1", "edited body");
    }

    @Test
    void otherSubtype_isDropped() {
        post(
            """
            {"type":"event_callback","team_id":"T1","event":{
              "type":"message","subtype":"channel_join","channel":"C1","user":"U1","ts":"100.1","text":"joined"}}
            """
        );

        verifyNoInteractions(ingestService, mentorService);
    }

    @Test
    void urlVerification_echoesChallenge() {
        ResponseEntity<String> res = post("{\"type\":\"url_verification\",\"challenge\":\"abc123\"}");

        assertThat(res.getBody()).isEqualTo("abc123");
    }

    @Test
    void duplicateEvent_isDroppedWhenDedupClaimFails() {
        when(dedupService.claim("Ev123")).thenReturn(false);

        ResponseEntity<String> res = post(
            """
            {"type":"event_callback","event_id":"Ev123","team_id":"T1","event":{
              "type":"message","channel_type":"im","channel":"D9","user":"U1","ts":"100.1","text":"hi heph"}}
            """
        );

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verifyNoInteractions(mentorService, ingestService);
    }

    @Test
    void firstDeliveryOfEvent_isProcessedWhenDedupClaimSucceeds() {
        when(dedupService.claim("Ev123")).thenReturn(true);

        post(
            """
            {"type":"event_callback","event_id":"Ev123","team_id":"T1","event":{
              "type":"message","channel_type":"im","channel":"D9","user":"U1","ts":"100.1","text":"hi heph"}}
            """
        );

        verify(mentorService).handleDm("T1", "D9", "U1", "hi heph", "100.1");
    }

    @Test
    void appUninstalled_routesToUninstallService_notDroppedBySubtypeReturn() {
        post(
            """
            {"type":"event_callback","team_id":"T1","event":{"type":"app_uninstalled"}}
            """
        );

        verify(uninstallService).onUninstall("T1", "app_uninstalled");
        verifyNoInteractions(mentorService, ingestService);
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
