package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.tum.cit.aet.hephaestus.integration.core.webhook.JetStreamPublisher;
import de.tum.cit.aet.hephaestus.integration.core.webhook.PublishRequest;
import de.tum.cit.aet.hephaestus.integration.slack.webhook.SlackChannelEventPublisher.PublishOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises the REAL body of {@link SlackChannelEventPublisher#publishIfChannelMessage}. The
 * {@code SlackEventsControllerTest} fully mocks this collaborator, so its classify → derive →
 * publish logic (and the four {@link PublishOutcome} arms) is otherwise uncovered. Uses a real
 * {@link SlackSubjectKeyDeriver} + real {@link JsonMapper} so the derived subject/dedup contract
 * is pinned end-to-end, with only the NATS {@link JetStreamPublisher} mocked.
 */
class SlackChannelEventPublisherTest extends BaseUnitTest {

    private final SlackSubjectKeyDeriver deriver = new SlackSubjectKeyDeriver();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Mock
    private JetStreamPublisher publisher;

    private SlackChannelEventPublisher classifier;

    @BeforeEach
    void setUp() {
        classifier = new SlackChannelEventPublisher(publisher, deriver);
    }

    private JsonNode json(String body) {
        return mapper.readTree(body);
    }

    @Test
    void channelMessage_publishes_withDerivedSubjectAndDedupHeader() {
        String body = """
            {"team_id":"T1","event_id":"Ev9","event":{
              "type":"message","channel_type":"channel","channel":"C1","ts":"100.1"}}
            """;
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);

        PublishOutcome outcome = classifier.publishIfChannelMessage(json(body), raw);

        assertThat(outcome).isEqualTo(PublishOutcome.PUBLISHED);
        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(publisher).publish(captor.capture());
        PublishRequest request = captor.getValue();
        assertThat(request.subject()).isEqualTo("slack.T1.C1.message");
        assertThat(request.dedupId()).isEqualTo("slack-Ev9");
        assertThat(request.headers()).containsExactly(entry("Nats-Msg-Id", "slack-Ev9"));
        // The raw body flows through untouched — same reference, no copy/re-serialization.
        assertThat(request.body()).isSameAs(raw);
    }

    @Test
    void groupMessage_isTreatedAsChannel() {
        String body = """
            {"team_id":"T1","event_id":"Ev9","event":{
              "type":"message","channel_type":"group","channel":"C1","ts":"100.1"}}
            """;

        PublishOutcome outcome = classifier.publishIfChannelMessage(json(body), body.getBytes(StandardCharsets.UTF_8));

        assertThat(outcome).isEqualTo(PublishOutcome.PUBLISHED);
        verify(publisher).publish(any());
    }

    @Test
    void directMessage_isNotChannel() {
        String body = """
            {"team_id":"T1","event_id":"Ev9","event":{
              "type":"message","channel_type":"im","channel":"D9","ts":"100.1"}}
            """;

        PublishOutcome outcome = classifier.publishIfChannelMessage(json(body), body.getBytes(StandardCharsets.UTF_8));

        assertThat(outcome).isEqualTo(PublishOutcome.NOT_CHANNEL_MESSAGE);
        verifyNoInteractions(publisher);
    }

    @Test
    void mpimMessage_isNotChannel() {
        // Multi-party DM must stay on the in-process mentor path, never published — reject-arm guard.
        String body = """
            {"team_id":"T1","event_id":"Ev9","event":{
              "type":"message","channel_type":"mpim","channel":"G9","ts":"100.1"}}
            """;

        PublishOutcome outcome = classifier.publishIfChannelMessage(json(body), body.getBytes(StandardCharsets.UTF_8));

        assertThat(outcome).isEqualTo(PublishOutcome.NOT_CHANNEL_MESSAGE);
        verifyNoInteractions(publisher);
    }

    @Test
    void messageWithoutChannelType_isNotChannel() {
        // An absent channel_type must not default into the channel arm — reject-arm guard.
        String body = """
            {"team_id":"T1","event_id":"Ev9","event":{
              "type":"message","channel":"C1","ts":"100.1"}}
            """;

        PublishOutcome outcome = classifier.publishIfChannelMessage(json(body), body.getBytes(StandardCharsets.UTF_8));

        assertThat(outcome).isEqualTo(PublishOutcome.NOT_CHANNEL_MESSAGE);
        verifyNoInteractions(publisher);
    }

    @Test
    void nonMessageType_isNotChannel() {
        String body = """
            {"team_id":"T1","event_id":"Ev9","event":{
              "type":"reaction_added","channel_type":"channel","channel":"C1","ts":"100.1"}}
            """;

        PublishOutcome outcome = classifier.publishIfChannelMessage(json(body), body.getBytes(StandardCharsets.UTF_8));

        assertThat(outcome).isEqualTo(PublishOutcome.NOT_CHANNEL_MESSAGE);
        verifyNoInteractions(publisher);
    }

    @Test
    void channelMessage_butNullPublisher_returnsPublisherUnavailable() {
        SlackChannelEventPublisher noPublisher = new SlackChannelEventPublisher(null, deriver);
        String body = """
            {"team_id":"T1","event_id":"Ev9","event":{
              "type":"message","channel_type":"channel","channel":"C1","ts":"100.1"}}
            """;

        PublishOutcome outcome = noPublisher.publishIfChannelMessage(json(body), body.getBytes(StandardCharsets.UTF_8));

        assertThat(outcome).isEqualTo(PublishOutcome.PUBLISHER_UNAVAILABLE);
    }

    @Test
    void publishThrows_returnsPublishFailed() {
        doThrow(new JetStreamPublisher.PublishFailedException("boom", new RuntimeException()))
            .when(publisher)
            .publish(any());
        String body = """
            {"team_id":"T1","event_id":"Ev9","event":{
              "type":"message","channel_type":"channel","channel":"C1","ts":"100.1"}}
            """;

        PublishOutcome outcome = classifier.publishIfChannelMessage(json(body), body.getBytes(StandardCharsets.UTF_8));

        assertThat(outcome).isEqualTo(PublishOutcome.PUBLISH_FAILED);
        verify(publisher).publish(any());
    }
}
