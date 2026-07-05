package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.consumer.ConsumerSubjectMath;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Producer↔consumer subject-grammar agreement for Slack, the analogue of
 * {@link de.tum.cit.aet.hephaestus.integration.core.consumer.SubjectGrammarRoundTripTest} for the
 * SCM kinds (CLAUDE.md §8 requires the divergence be test-pinned). Slack has no repository fan-out,
 * so instead of {@code ConsumerSubjectMath.buildSubjectPrefix} the consumer subscribes to the flat
 * {@code slack.>} filter and routes with {@link SlackSubjectParser}; this test pins that the
 * subject the PRODUCER ({@link SlackSubjectKeyDeriver}) emits is exactly what the CONSUMER stack
 * (prefix→kind, filter, parser) agrees to deliver to {@link SlackChannelMessageHandler}.
 *
 * <p>A drift that this catches: if the producer stopped sanitizing dots (or emitted a different
 * event token) the four-segment grammar would break, the parser would extract the wrong event
 * token, and the handler key would no longer match — the message would publish into the stream but
 * never dispatch. The dotted-id case is the mutant guard for the {@code '.' → '~'} sanitize.
 */
class SlackSubjectRoundTripTest extends BaseUnitTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final SlackSubjectKeyDeriver DERIVER = new SlackSubjectKeyDeriver();
    private static final SlackSubjectParser PARSER = new SlackSubjectParser();

    /** The handler's own registered key — ties the round-trip to what actually dispatches, not a literal. */
    private static final EventTypeKey HANDLER_KEY = new SlackChannelMessageHandler(null, null, null).key();

    private static JsonNode payload(String teamId, String channelId) {
        return MAPPER.readTree(
            "{\"type\":\"event_callback\",\"team_id\":\"" +
                teamId +
                "\",\"event\":{\"type\":\"message\",\"channel_type\":\"channel\",\"channel\":\"" +
                channelId +
                "\",\"ts\":\"100.1\",\"text\":\"hi\"}}"
        );
    }

    @ParameterizedTest(name = "team={0} channel={1}")
    @ValueSource(strings = { "T0123,C0456", "T.dotted,C.dotted", "TABCDEF12,GXYZ" })
    void producerSubjectIsDeliveredToTheHandler(String teamAndChannel) {
        String[] tc = teamAndChannel.split(",", -1);
        JsonNode payload = payload(tc[0], tc[1]);

        // Producer side: the subject the publisher stamps onto JetStream.
        String producerSubject = DERIVER.subjectFor(payload);

        // Consumer side, step 1: the fleet-wide filter the Slack consumer subscribes to must match.
        assertThat(producerSubject)
            .as(
                "subject %s must be covered by the consumer filter %s",
                producerSubject,
                ConsumerSubjectMath.flatStreamSubjectFilter(IntegrationKind.SLACK)
            )
            .startsWith("slack.");

        // Consumer side, step 2: prefix → kind resolves to SLACK (never reflects on input).
        assertThat(ConsumerSubjectMath.kindFromSubjectPrefix(producerSubject)).contains(IntegrationKind.SLACK);

        // Consumer side, step 3: the SubjectParser folds the subject into exactly the handler's registered key.
        assertThat(PARSER.parse(producerSubject)).isEqualTo(HANDLER_KEY);

        // The four-segment dot grammar holds even when the team/channel carried dots (sanitized to '~').
        assertThat(producerSubject.split("\\.", -1)).hasSize(4);
    }

    @Test
    void streamAndFilterAreWiredForSlack() {
        assertThat(ConsumerSubjectMath.streamNameFor(IntegrationKind.SLACK)).contains("slack");
        assertThat(ConsumerSubjectMath.flatStreamSubjectFilter(IntegrationKind.SLACK)).isEqualTo("slack.>");
    }
}
