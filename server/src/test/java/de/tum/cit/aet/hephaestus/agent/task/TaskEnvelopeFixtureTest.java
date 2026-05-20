package de.tum.cit.aet.hephaestus.agent.task;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Byte-identical snapshot test for {@link TaskEnvelopeWriter} output. The fixture lives at
 * {@code src/test/resources/task-fixtures/v1/practice-review.json} — see the adjacent
 * {@code REGENERATE.md} for the regen workflow.
 *
 * <p>Uses {@link JsonMapper#builder()} to mirror the production bean configuration.
 * A future change to that configuration that alters byte output is intentionally caught here.
 */
@DisplayName("TaskEnvelope fixture (v1)")
class TaskEnvelopeFixtureTest extends BaseUnitTest {

    private static final String FIXTURE_PATH = "task-fixtures/v1/practice-review.json";

    @Test
    @DisplayName("PracticeReview envelope matches the committed fixture byte-for-byte")
    void matchesFixture() throws IOException {
        JsonMapper productionMapper = JsonMapper.builder().build();
        TaskEnvelopeWriter writer = new TaskEnvelopeWriter(productionMapper);

        TaskEnvelope envelope = new TaskEnvelope(
            1,
            UUID.fromString("00000000-0000-0000-0000-00000000abcd"),
            99L,
            new Task.PracticeReview(
                "Review merge request #42 in owner/repo. Read the context files, " +
                    "then persist findings via the report_finding tool and the final MR summary " +
                    "via set_review_summary. Follow .pi/AGENTS.md for the schema and rules.",
                42,
                "owner/repo"
            )
        );

        String actual = writer.writeAsString(envelope);

        if ("true".equals(System.getProperty("hephaestus.snapshot.regenerate"))) {
            Files.writeString(resolveFixturePath(), actual, StandardCharsets.UTF_8);
            return;
        }

        String expected = readFixture();
        assertThat(actual.replace("\r\n", "\n")).isEqualTo(expected.replace("\r\n", "\n"));
    }

    private static String readFixture() throws IOException {
        try (var is = TaskEnvelopeFixtureTest.class.getClassLoader().getResourceAsStream(FIXTURE_PATH)) {
            assertThat(is).as("classpath fixture %s", FIXTURE_PATH).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path resolveFixturePath() {
        Path candidate = Path.of("src/test/resources").resolve(FIXTURE_PATH);
        return Files.exists(candidate) ? candidate : Path.of("server/src/test/resources").resolve(FIXTURE_PATH);
    }
}
