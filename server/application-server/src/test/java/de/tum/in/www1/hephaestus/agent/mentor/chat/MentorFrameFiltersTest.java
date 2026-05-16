package de.tum.in.www1.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract for {@link MentorFrameFilters#forThread(UUID)} — the predicate is what the SPI's
 * {@code subscribe(Cursor, Predicate, Consumer)} call site uses to route a shared sandbox's
 * frame stream to the right thread. Two invariants:
 *
 * <ul>
 *   <li>Frames carrying a different {@code params.threadId} are rejected.</li>
 *   <li>Broadcast frames (no params, null threadId, missing threadId, or response frames with
 *       no {@code params} at all) pass — they're cross-cutting state needed by every client.</li>
 * </ul>
 */
@DisplayName("MentorFrameFilters")
class MentorFrameFiltersTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectNode notification(String threadId) {
        ObjectNode frame = MAPPER.createObjectNode();
        frame.put("jsonrpc", "2.0");
        frame.put("method", "event");
        ObjectNode params = frame.putObject("params");
        if (threadId == null) {
            params.putNull("threadId");
        } else {
            params.put("threadId", threadId);
        }
        params.set("event", MAPPER.createObjectNode().put("type", "text_delta"));
        return frame;
    }

    @Test
    @DisplayName("forThread(null) is the identity filter — accepts every frame")
    void nullThreadIsIdentity() {
        Predicate<JsonNode> filter = MentorFrameFilters.forThread(null);
        assertThat(filter.test(notification(UUID.randomUUID().toString()))).isTrue();
        assertThat(filter.test(notification(null))).isTrue();
        assertThat(filter.test(MAPPER.createObjectNode().put("id", 5))).isTrue();
    }

    @Test
    @DisplayName("Frame's threadId matches → accepted")
    void matchingThreadIdAccepted() {
        UUID bound = UUID.randomUUID();
        Predicate<JsonNode> filter = MentorFrameFilters.forThread(bound);
        assertThat(filter.test(notification(bound.toString()))).isTrue();
    }

    @Test
    @DisplayName("Frame's threadId differs → rejected")
    void mismatchedThreadIdRejected() {
        UUID bound = UUID.randomUUID();
        Predicate<JsonNode> filter = MentorFrameFilters.forThread(bound);
        assertThat(filter.test(notification(UUID.randomUUID().toString()))).isFalse();
    }

    @Test
    @DisplayName("Broadcast notification (threadId null) crosses the filter — runner_ready et al")
    void nullThreadIdIsBroadcast() {
        Predicate<JsonNode> filter = MentorFrameFilters.forThread(UUID.randomUUID());
        assertThat(filter.test(notification(null))).isTrue();
    }

    @Test
    @DisplayName("Broadcast notification (no threadId field at all) crosses the filter")
    void missingThreadIdIsBroadcast() {
        ObjectNode frame = MAPPER.createObjectNode();
        frame.put("jsonrpc", "2.0");
        frame.put("method", "event");
        frame.putObject("params"); // no threadId
        Predicate<JsonNode> filter = MentorFrameFilters.forThread(UUID.randomUUID());
        assertThat(filter.test(frame)).isTrue();
    }

    @Test
    @DisplayName("Response frame (no params) passes through — id correlation routes it later")
    void responseFramesPassUntouched() {
        ObjectNode frame = MAPPER.createObjectNode();
        frame.put("jsonrpc", "2.0");
        frame.put("id", 42);
        frame.set("result", MAPPER.createObjectNode().put("ok", true));
        Predicate<JsonNode> filter = MentorFrameFilters.forThread(UUID.randomUUID());
        assertThat(filter.test(frame)).isTrue();
    }

    @Test
    @DisplayName("Non-object frame (defensive) passes")
    void nonObjectPasses() {
        Predicate<JsonNode> filter = MentorFrameFilters.forThread(UUID.randomUUID());
        assertThat(filter.test(MAPPER.nullNode())).isTrue();
    }
}
