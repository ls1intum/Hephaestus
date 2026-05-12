package de.tum.in.www1.hephaestus.agent.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TaskEnvelopeWriter")
class TaskEnvelopeWriterTest extends BaseUnitTest {

    private TaskEnvelopeWriter writer;
    private ObjectMapper reader;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        writer = new TaskEnvelopeWriter(mapper);
        reader = mapper;
    }

    private TaskEnvelope sampleEnvelope() {
        return TaskEnvelope.of(
            UUID.fromString("00000000-0000-0000-0000-00000000abcd"),
            99L,
            new Task.PracticeReview("Review this PR", 42, "owner/repo")
        );
    }

    @Test
    @DisplayName("writes envelope with kind discriminator and schemaVersion 1")
    void writesKindAndSchemaVersion() throws Exception {
        JsonNode root = reader.readTree(writer.write(sampleEnvelope()));

        assertThat(root.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(root.get("jobId").asText()).isEqualTo("00000000-0000-0000-0000-00000000abcd");
        assertThat(root.get("workspaceId").asLong()).isEqualTo(99L);
        assertThat(root.get("task").get("kind").asText()).isEqualTo("practice_review");
        assertThat(root.get("task").get("prompt").asText()).isEqualTo("Review this PR");
        assertThat(root.get("task").get("pullRequestNumber").asInt()).isEqualTo(42);
        assertThat(root.get("task").get("repositoryFullName").asText()).isEqualTo("owner/repo");
    }

    @Test
    @DisplayName("schemaVersion is on the envelope, not on the task record")
    void schemaVersionOnEnvelopeOnly() throws Exception {
        JsonNode root = reader.readTree(writer.write(sampleEnvelope()));
        assertThat(root.get("task").has("schemaVersion")).isFalse();
    }

    @Test
    @DisplayName("output is deterministic across invocations (sorted map keys)")
    void deterministicOutput() {
        TaskEnvelope env = sampleEnvelope();
        assertThat(writer.write(env)).isEqualTo(writer.write(env));
    }

    @Test
    @DisplayName("round-trips through Jackson with the @JsonSubTypes contract")
    void roundTripDeserialise() throws Exception {
        TaskEnvelope decoded = reader.readValue(writer.write(sampleEnvelope()), TaskEnvelope.class);

        assertThat(decoded.schemaVersion()).isEqualTo(1);
        assertThat(decoded.workspaceId()).isEqualTo(99L);
        assertThat(decoded.task()).isInstanceOf(Task.PracticeReview.class);
        Task.PracticeReview task = (Task.PracticeReview) decoded.task();
        assertThat(task.prompt()).isEqualTo("Review this PR");
        assertThat(task.pullRequestNumber()).isEqualTo(42);
        assertThat(task.repositoryFullName()).isEqualTo("owner/repo");
    }

    @Test
    @DisplayName("PracticeReview rejects non-positive pullRequestNumber")
    void rejectsNonPositivePrNumber() {
        assertThatThrownBy(() -> new Task.PracticeReview("p", 0, "o/r")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Task.PracticeReview("p", -1, "o/r")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PracticeReview rejects blank prompt")
    void rejectsBlankPrompt() {
        assertThatThrownBy(() -> new Task.PracticeReview("", 42, "owner/repo")).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    @DisplayName("TaskEnvelope rejects non-positive schemaVersion and workspaceId")
    void rejectsNonPositiveFields() {
        Task.PracticeReview task = new Task.PracticeReview("p", 1, "o/r");
        UUID jobId = UUID.randomUUID();
        assertThatThrownBy(() -> new TaskEnvelope(0, jobId, 1L, task))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("schemaVersion");
        assertThatThrownBy(() -> new TaskEnvelope(1, jobId, 0L, task))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("workspaceId");
    }

    @Test
    @DisplayName("TaskEnvelope.of stamps the current SCHEMA_VERSION constant")
    void ofUsesCurrentSchemaVersion() {
        TaskEnvelope env = TaskEnvelope.of(UUID.randomUUID(), 1L, new Task.PracticeReview("p", 1, "o/r"));
        assertThat(env.schemaVersion()).isEqualTo(TaskEnvelope.SCHEMA_VERSION);
    }
}
