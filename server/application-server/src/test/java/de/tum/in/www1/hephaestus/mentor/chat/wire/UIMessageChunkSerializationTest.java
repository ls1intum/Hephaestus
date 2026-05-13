package de.tum.in.www1.hephaestus.mentor.chat.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Wire fixtures pinned against the AI SDK {@code UIMessageChunk} schema. A protocol bump in
 * Vercel's package surfaces here before reaching the webapp.
 *
 * <p>Reference: https://github.com/vercel/ai/blob/main/packages/ai/src/ui-message-stream/ui-message-chunks.ts
 */
@DisplayName("UIMessageChunk wire serialisation")
class UIMessageChunkSerializationTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /**
     * Each row pins the JSON shape of one chunk variant. Covers every chunk the translator emits
     * (one representative per discriminator). The single test is a smoke-screen against schema
     * drift; deeper per-chunk semantics live in {@code MentorEventTranslatorTest}.
     */
    static List<Object[]> chunkFixtures() {
        UUID messageId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID findingId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        ObjectNode finishMeta = NODES.objectNode().put("costUsd", 0.0042);
        ObjectNode toolOutput = NODES.objectNode().put("status", "ok").put("count", 7);
        return List.of(
            new Object[] {
                new UIMessageChunk.Start(messageId, null),
                "{\"type\":\"start\",\"messageId\":\"" + messageId + "\"}",
            },
            new Object[] { new UIMessageChunk.StartStep(), "{\"type\":\"start-step\"}" },
            new Object[] { new UIMessageChunk.FinishStep(), "{\"type\":\"finish-step\"}" },
            new Object[] { new UIMessageChunk.TextStart("t-0"), "{\"type\":\"text-start\",\"id\":\"t-0\"}" },
            new Object[] {
                new UIMessageChunk.TextDelta("t-0", "Hello"),
                "{\"type\":\"text-delta\",\"id\":\"t-0\",\"delta\":\"Hello\"}",
            },
            new Object[] { new UIMessageChunk.TextEnd("t-0"), "{\"type\":\"text-end\",\"id\":\"t-0\"}" },
            new Object[] {
                new UIMessageChunk.ToolOutputAvailable("call-1", toolOutput),
                "{\"type\":\"tool-output-available\",\"toolCallId\":\"call-1\",\"output\":{\"status\":\"ok\",\"count\":7}}",
            },
            new Object[] {
                new UIMessageChunk.Finish(null, finishMeta),
                "{\"type\":\"finish\",\"messageMetadata\":{\"costUsd\":0.0042}}",
            },
            // NON_NULL: finishReason omitted, but messageMetadata kept.
            // data-* envelope shape per AI SDK strict-object schema:
            // {type, id?, data: unknown, transient?: boolean}. status is transient (banner only);
            // finding is permanent (linked chip in message history).
            new Object[] {
                UIMessageChunk.DataMentorStatus.of("warming-up", "container-cold"),
                "{\"type\":\"data-mentor-status\",\"data\":{\"state\":\"warming-up\",\"reason\":\"container-cold\"},\"transient\":true}",
            },
            new Object[] {
                UIMessageChunk.DataFinding.of(findingId),
                "{\"type\":\"data-finding\",\"id\":\"" +
                findingId +
                "\",\"data\":{\"findingId\":\"" +
                findingId +
                "\"}}",
            },
            new Object[] {
                new UIMessageChunk.Error("container died mid-turn"),
                "{\"type\":\"error\",\"errorText\":\"container died mid-turn\"}",
            }
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chunkFixtures")
    @DisplayName("chunk → JSON shape matches AI SDK upstream")
    void serialisesMatchesUpstreamShape(UIMessageChunk chunk, String expectedJson) throws Exception {
        String json = MAPPER.writeValueAsString(chunk);
        JsonNode actual = MAPPER.readTree(json);
        JsonNode expected = MAPPER.readTree(expectedJson);
        assertThat(actual).as("chunk %s", chunk).isEqualTo(expected);
    }

    @Test
    @DisplayName("data-* discriminators preserve kebab-case (NOT data<Pascal>)")
    void dataChunkDiscriminatorSpelling() throws Exception {
        // The AI SDK protocol pins the kebab-case spelling — a Jackson default name strategy
        // change (e.g. SNAKE_CASE on a future bean override) would silently rename this to
        // `dataMentorStatus` and break the webapp router. Pin both data-* variants explicitly.
        UIMessageChunk status = UIMessageChunk.DataMentorStatus.of("ok", "ok");
        UIMessageChunk finding = UIMessageChunk.DataFinding.of(UUID.randomUUID());
        assertThat(MAPPER.readTree(MAPPER.writeValueAsString(status)).get("type").asText()).isEqualTo(
            "data-mentor-status"
        );
        assertThat(MAPPER.readTree(MAPPER.writeValueAsString(finding)).get("type").asText()).isEqualTo("data-finding");
    }
}
