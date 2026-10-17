package de.tum.cit.aet.hephaestus.agent.task;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** JSON contract fixture shared with the TypeScript runner tests. */
class TaskEnvelopeFixtureTest extends BaseUnitTest {

    private static final String FIXTURE_PATH = "task-fixtures/practice-review.json";

    @Test
    void shouldMatchTheSharedEnvelopeWhenTheWriterSerializesAReview() throws IOException {
        JsonMapper mapper = JsonMapper.builder().build();
        TaskEnvelopeWriter writer = new TaskEnvelopeWriter(mapper);

        TaskEnvelope envelope = TaskEnvelope.of(
                UUID.fromString("00000000-0000-0000-0000-000000001234"),
                UUID.fromString("00000000-0000-0000-0000-00000000abcd"),
                0,
                99L,
                new Task("Review the pinned item. Read the staged inputs and submit results."));

        String actual = writer.writeAsString(envelope);

        String expected = readFixture();
        assertThat(mapper.readTree(actual)).isEqualTo(mapper.readTree(expected));
    }

    private static String readFixture() throws IOException {
        try (var is = TaskEnvelopeFixtureTest.class.getClassLoader().getResourceAsStream(FIXTURE_PATH)) {
            assertThat(is).as("classpath fixture %s", FIXTURE_PATH).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
