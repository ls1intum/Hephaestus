package de.tum.cit.aet.hephaestus.integration.slack.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextRequest;
import de.tum.cit.aet.hephaestus.integration.chat.item.ConversationThreadProjection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ConversationCaptureSourceTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ConversationThreadProjection projection;

    @Test
    void contributesProjectedDeveloperConversationPayload() throws Exception {
        ConversationCaptureSource source = new ConversationCaptureSource(projection, objectMapper);
        when(projection.buildPayload(11L, 22L))
                .thenReturn(objectMapper.createObjectNode().put("source", "slack"));

        Map<String, byte[]> files = new HashMap<>();
        source.contribute(new MentorContextRequest(11L, 22L, UUID.randomUUID(), null), files);

        assertThat(files).containsOnlyKeys(ConversationCaptureSource.OUTPUT_KEY);
        JsonNode payload =
                objectMapper.readTree(Objects.requireNonNull(files.get(ConversationCaptureSource.OUTPUT_KEY)));
        assertThat(payload.path("source").asString()).isEqualTo("slack");
        verify(projection).buildPayload(11L, 22L);
    }
}
