package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.ReviewRunTargetMapper.WorkKey;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * PostgreSQL uses {@code idx_agent_job_review_work} only for a query that states its expression, so the expression
 * the queries use and the one the index was built on must not drift apart, and both must name the field the run's
 * work is read from.
 */
@Tag("unit")
class ReviewWorkIdSqlTest {

    private static final String CHANGELOG = "db/changelog/1791557968341_changelog.xml";

    @Test
    void shouldStateTheWorkExactlyAsTheIndexWhenAQueryCountsTheReviewsOfAWork() throws IOException {
        String changelog;
        try (InputStream in =
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(CHANGELOG), CHANGELOG)) {
            changelog = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher indexed =
                Pattern.compile("\\((CASE job_type.*?END)\\)", Pattern.DOTALL).matcher(changelog);

        assertThat(indexed.find())
                .as("the index states the work as a CASE over job_type")
                .isTrue();
        assertThat(normalized(indexed.group(1))).isEqualTo(normalized(ReviewRunTargetMapper.WORK_ID_SQL));
    }

    @Test
    void shouldReadEveryReviewTypesWorkFromTheFieldTheTargetMapperNamesWhenTheSqlStatesIt() {
        List<WorkKey> keys = List.of(
                        ArtifactKinds.PULL_REQUEST,
                        ArtifactKinds.ISSUE,
                        ArtifactKinds.CONVERSATION_THREAD,
                        ArtifactKinds.DOCUMENT)
                .stream()
                .map(kind -> Objects.requireNonNull(ReviewRunTargetMapper.workKey(kind)))
                .toList();

        assertThat(keys).extracting(WorkKey::jobType).containsExactlyInAnyOrder(AgentJobType.values());
        assertThat(Arrays.stream(ReviewRunTargetMapper.WORK_ID_SQL.split(" WHEN "))
                        .skip(1))
                .hasSize(AgentJobType.values().length);
        assertThat(keys)
                .allSatisfy(key -> assertThat(ReviewRunTargetMapper.WORK_ID_SQL)
                        .contains("WHEN '" + key.jobType().name() + "' THEN metadata ->> '" + key.idField() + "'"));
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
