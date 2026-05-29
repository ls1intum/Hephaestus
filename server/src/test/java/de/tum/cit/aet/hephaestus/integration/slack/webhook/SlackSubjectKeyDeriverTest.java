package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SlackSubjectKeyDeriverTest extends BaseUnitTest {

    private final SlackSubjectKeyDeriver deriver = new SlackSubjectKeyDeriver();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode json(String raw) throws Exception {
        return objectMapper.readTree(raw);
    }

    @Test
    void buildsChannelScopedSubjectFromEventsApiEnvelope() throws Exception {
        JsonNode payload = json("{\"team_id\":\"T123\",\"event\":{\"channel\":\"C456\",\"type\":\"message\"}}");
        assertThat(deriver.deriveSubject(payload, Map.of())).isEqualTo("slack.T123.C456.message");
    }

    @Test
    void channelCollapsesToPlaceholderForTeamLevelEvent() throws Exception {
        JsonNode payload = json("{\"team_id\":\"T123\",\"event\":{\"type\":\"app_uninstalled\"}}");
        assertThat(deriver.deriveSubject(payload, Map.of())).isEqualTo("slack.T123.?.app_uninstalled");
    }

    @Test
    void fallsBackToTopLevelTypeWhenNoInnerEvent() throws Exception {
        JsonNode payload = json("{\"team_id\":\"T1\",\"type\":\"url_verification\"}");
        assertThat(deriver.deriveSubject(payload, Map.of())).isEqualTo("slack.T1.?.url_verification");
    }

    @Test
    void sanitizesUnsafeSubjectChars() throws Exception {
        JsonNode payload = json("{\"team_id\":\"T 1.2\",\"event\":{\"channel\":\"C*3\",\"type\":\"message\"}}");
        // Spaces, dots, and NATS wildcards collapse to underscores.
        assertThat(deriver.deriveSubject(payload, Map.of())).isEqualTo("slack.T_1_2.C_3.message");
    }

    @Test
    void placeholdersWhenTeamAndTypeMissing() throws Exception {
        assertThat(deriver.deriveSubject(json("{}"), Map.of())).isEqualTo("slack.?.?.?");
    }

    @Test
    void dedupKeyPrefersRequestIdHeaderCaseInsensitively() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThat(deriver.deriveDedupKey(body, Map.of("x-slack-request-id", "Req-9"))).isEqualTo("slack-Req-9");
    }

    @Test
    void dedupKeyFallsBackToTruncatedBodyHash() {
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        String key = deriver.deriveDedupKey(body, Map.of());

        assertThat(key).startsWith("slack-");
        assertThat(key.substring("slack-".length())).hasSize(16).matches("[0-9a-f]+");
    }

    @Test
    void deriverIdentifiesAsSlackKind() {
        assertThat(deriver.kind()).isEqualTo(IntegrationKind.SLACK);
    }
}
