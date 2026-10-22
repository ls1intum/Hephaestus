package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.handler.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackMentorService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.nats.client.Message;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import tools.jackson.databind.json.JsonMapper;

/**
 * How the handler reads Slack's {@code agent_session_stopped} envelope, which arrives untrusted. Which turn a stop
 * names, and whether it may stop it, is the service's part ({@code SlackMentorServiceIntegrationTest}).
 */
class SlackAgentSessionStoppedMessageHandlerTest extends BaseUnitTest {

    private static final String FIELDS =
            "\"user\":\"U1\",\"channel\":\"D1\",\"thread_ts\":\"100.1\",\"event_ts\":\"1790000000.000123\"";

    @Mock
    private SlackMentorService mentorService;

    private SlackAgentSessionStoppedMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SlackAgentSessionStoppedMessageHandler(
                mentorService, new NatsMessageDeserializer(JsonMapper.builder().build()));
    }

    private static Message nats(String body) {
        Message message = mock(Message.class);
        when(message.getData()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return message;
    }

    /** An envelope of team {@code T1} whose event has {@code fields}. */
    private static String envelope(String fields) {
        return "{\"team_id\":\"T1\",\"event\":{\"type\":\"agent_session_stopped\"," + fields + "}}";
    }

    @Test
    void shouldHandAStopBeforeTheFirstWordsToTheService() {
        handler.onMessage(nats(envelope(FIELDS + ",\"streaming_message_ts\":[]")));

        verify(mentorService).handleSessionStop("T1", "D1", "U1", "100.1", "1790000000.000123", List.of());
    }

    @Test
    void shouldHandTheStreamsThatSlackNamesToTheServiceInTheirOrder() {
        handler.onMessage(nats(envelope(FIELDS + ",\"streaming_message_ts\":[\"300.2\",\"300.1\"]")));

        verify(mentorService)
                .handleSessionStop("T1", "D1", "U1", "100.1", "1790000000.000123", List.of("300.2", "300.1"));
    }

    @Test
    void shouldTakeTheTeamFromTheAuthorizationWhenTheEnvelopeNamesNone() {
        handler.onMessage(
                nats("{\"authorizations\":[{\"team_id\":\"T2\"}],\"event\":{\"type\":\"agent_session_stopped\","
                        + FIELDS
                        + ",\"streaming_message_ts\":[]}}"));

        verify(mentorService).handleSessionStop("T2", "D1", "U1", "100.1", "1790000000.000123", List.of());
    }

    @Test
    void shouldIgnoreAnotherEventType() {
        handler.onMessage(nats(
                "{\"team_id\":\"T1\",\"event\":{\"type\":\"message\"," + FIELDS + ",\"streaming_message_ts\":[]}}"));

        verifyNoInteractions(mentorService);
    }

    @Test
    void shouldIgnoreAnEnvelopeWithoutATeam() {
        handler.onMessage(
                nats("{\"event\":{\"type\":\"agent_session_stopped\"," + FIELDS + ",\"streaming_message_ts\":[]}}"));

        verifyNoInteractions(mentorService);
    }

    /** Each event lacks a field that the stop needs, or has it in another type or form; it is acknowledged unused. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                // A required string missing, blank, or not a string.
                "\"channel\":\"D1\",\"thread_ts\":\"100.1\",\"event_ts\":\"1790000000.000123\",\"streaming_message_ts\":[]",
                "\"user\":\"U1\",\"channel\":\" \",\"thread_ts\":\"100.1\",\"event_ts\":\"1790000000.000123\",\"streaming_message_ts\":[]",
                "\"user\":\"U1\",\"channel\":\"D1\",\"thread_ts\":100.1,\"event_ts\":\"1790000000.000123\",\"streaming_message_ts\":[]",
                "\"user\":\"U1\",\"channel\":\"D1\",\"thread_ts\":\"100.1\",\"streaming_message_ts\":[]",
                // A time that is not a Slack ts.
                "\"user\":\"U1\",\"channel\":\"D1\",\"thread_ts\":\"100.1\",\"event_ts\":\"later\",\"streaming_message_ts\":[]",
                "\"user\":\"U1\",\"channel\":\"D1\",\"thread_ts\":\"100.1\",\"event_ts\":\"1790000000.0001234\",\"streaming_message_ts\":[]",
                // The streams missing, not an array, or with an entry that is not a non-blank string.
                "\"user\":\"U1\",\"channel\":\"D1\",\"thread_ts\":\"100.1\",\"event_ts\":\"1790000000.000123\"",
                "\"user\":\"U1\",\"channel\":\"D1\",\"thread_ts\":\"100.1\",\"event_ts\":\"1790000000.000123\",\"streaming_message_ts\":\"300.1\"",
                "\"user\":\"U1\",\"channel\":\"D1\",\"thread_ts\":\"100.1\",\"event_ts\":\"1790000000.000123\",\"streaming_message_ts\":[\"300.1\",\"\"]",
                "\"user\":\"U1\",\"channel\":\"D1\",\"thread_ts\":\"100.1\",\"event_ts\":\"1790000000.000123\",\"streaming_message_ts\":[300.1]",
                "\"user\":\"U1\",\"channel\":\"D1\",\"thread_ts\":\"100.1\",\"event_ts\":\"1790000000.000123\",\"streaming_message_ts\":[null]"
            })
    void shouldIgnoreAStopWithoutUsableFields(String fields) {
        handler.onMessage(nats(envelope(fields)));

        verifyNoInteractions(mentorService);
    }
}
