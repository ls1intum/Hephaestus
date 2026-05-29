package de.tum.cit.aet.hephaestus.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import de.tum.cit.aet.hephaestus.integration.scm.github.pullrequest.dto.GitHubPullRequestEventDTO;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.pullrequest.dto.GitLabMergeRequestEventDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * Pins the tolerant-reader contract for the SCM webhook DTOs against two fixtures per vendor.
 *
 * <p>v1 fixtures are minimal "as-shipped today" examples. v2 fixtures are NOT captured from a
 * real future vendor release — they are SYNTHETIC forward-compatibility fixtures: the v1 payload
 * with fabricated unknown keys injected at the top level and at every nesting level. They simulate
 * a vendor adding fields the DTO does not declare.
 *
 * <p>The behaviour under test is the tolerant-reader property in two halves: (1) the reader must
 * deserialize the unknown-field payload WITHOUT throwing, and (2) it must still extract the known
 * fields correctly. The v2 tests assert both, and additionally assert the fixture genuinely carries
 * injected unknown keys so the tolerance assertion cannot quietly become vacuous if someone strips
 * the synthetic fields.
 */
class MultiVersionFixtureTest extends BaseUnitTest {

    // Match the production mapper: find-and-register JSR-310 + jdk8 + ignore-unknown-properties.
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void githubPullRequestV1() throws Exception {
        GitHubPullRequestEventDTO dto = readFixture(
            "/integration-fixtures/github/v1/pull_request.opened.json",
            GitHubPullRequestEventDTO.class
        );
        assertThat(dto.action()).isEqualTo("opened");
        assertThat(dto.number()).isEqualTo(42);
        assertThat(dto.pullRequest()).isNotNull();
        assertThat(dto.pullRequest().title()).isEqualTo("Add feature X");
        assertThat(dto.repository().fullName()).isEqualTo("HephaestusTest/repo");
    }

    @Test
    void githubPullRequestV2_ignoresUnknownFieldsAndExtractsKnownFields() throws Exception {
        String classpath = "/integration-fixtures/github/v2/pull_request.opened.json";

        // Guard: the fixture must actually inject unknown fields the DTO does not declare,
        // otherwise the tolerance assertion below would be vacuous.
        JsonNode raw = readTree(classpath);
        assertThat(raw.has("_future_top_level")).as("fixture must carry an injected unknown top-level key").isTrue();
        assertThat(raw.path("repository").has("_visibility_v2"))
            .as("fixture must carry an injected unknown nested key")
            .isTrue();

        // (1) Tolerance: deserializing the unknown-field payload must not throw.
        GitHubPullRequestEventDTO dto = readFixture(classpath, GitHubPullRequestEventDTO.class);

        // (2) Correctness: every known field is still extracted, unaffected by the unknown keys.
        assertThat(dto.action()).isEqualTo("opened");
        assertThat(dto.number()).isEqualTo(42);
        assertThat(dto.pullRequest()).isNotNull();
        assertThat(dto.pullRequest().title()).isEqualTo("Add feature X");
        assertThat(dto.repository().fullName()).isEqualTo("HephaestusTest/repo");
    }

    @Test
    void gitlabMergeRequestV1() throws Exception {
        GitLabMergeRequestEventDTO dto = readFixture(
            "/integration-fixtures/gitlab/v1/merge_request.open.json",
            GitLabMergeRequestEventDTO.class
        );
        assertThat(dto.objectKind()).isEqualTo("merge_request");
        assertThat(dto.objectAttributes()).isNotNull();
        assertThat(dto.objectAttributes().title()).isEqualTo("Add feature X");
        assertThat(dto.project()).isNotNull();
        assertThat(dto.project().pathWithNamespace()).isEqualTo("hephaestustest/smoke");
    }

    @Test
    void gitlabMergeRequestV2_ignoresUnknownFieldsAndExtractsKnownFields() throws Exception {
        String classpath = "/integration-fixtures/gitlab/v2/merge_request.open.json";

        // Guard: the fixture must actually inject unknown fields the DTO does not declare.
        JsonNode raw = readTree(classpath);
        assertThat(raw.has("_event_uuid_v2")).as("fixture must carry an injected unknown top-level key").isTrue();
        assertThat(raw.path("object_attributes").has("_blocking_discussions_resolved_v2"))
            .as("fixture must carry an injected unknown nested key")
            .isTrue();

        // (1) Tolerance: deserializing the unknown-field payload must not throw.
        GitLabMergeRequestEventDTO dto = readFixture(classpath, GitLabMergeRequestEventDTO.class);

        // (2) Correctness: every known field is still extracted, unaffected by the unknown keys.
        assertThat(dto.objectKind()).isEqualTo("merge_request");
        assertThat(dto.objectAttributes()).isNotNull();
        assertThat(dto.objectAttributes().title()).isEqualTo("Add feature X");
        assertThat(dto.project()).isNotNull();
        assertThat(dto.project().pathWithNamespace()).isEqualTo("hephaestustest/smoke");
    }

    private <T> T readFixture(String classpath, Class<T> type) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("fixture %s must be on the classpath", classpath).isNotNull();
            return mapper.readValue(in, type);
        }
    }

    private JsonNode readTree(String classpath) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("fixture %s must be on the classpath", classpath).isNotNull();
            return mapper.readTree(in);
        }
    }
}
