package de.tum.cit.aet.hephaestus.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import de.tum.cit.aet.hephaestus.integration.github.pullrequest.dto.GitHubPullRequestEventDTO;
import de.tum.cit.aet.hephaestus.integration.gitlab.pullrequest.dto.GitLabMergeRequestEventDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the tolerant-reader contract against captured-and-augmented webhook fixtures.
 *
 * <p>v1 fixtures are minimal "as-shipped today" examples. v2 fixtures take the same
 * shape and add unknown vendor fields at every nesting level — the reader must
 * deserialize without throwing and produce a payload structurally equivalent to v1.
 *
 * <p>This is the canary against vendor schema drift. If a vendor ships a new
 * top-level field and Jackson starts throwing, this test fails before the change
 * reaches a real webhook.
 */
@DisplayName("Multi-version webhook fixture tolerance")
class MultiVersionFixtureTest extends BaseUnitTest {

    // Match the production mapper: find-and-register JSR-310 + jdk8 + ignore-unknown-properties.
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    @DisplayName("GitHub pull_request v1 deserializes to a populated DTO")
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
    @DisplayName("GitHub pull_request v2 (with unknown vendor fields) still deserializes")
    void githubPullRequestV2() throws Exception {
        GitHubPullRequestEventDTO dto = readFixture(
            "/integration-fixtures/github/v2/pull_request.opened.json",
            GitHubPullRequestEventDTO.class
        );
        // Identity assertions identical to v1 — the unknown fields must be ignored, not
        // dropped silently and broken.
        assertThat(dto.action()).isEqualTo("opened");
        assertThat(dto.number()).isEqualTo(42);
        assertThat(dto.pullRequest()).isNotNull();
        assertThat(dto.pullRequest().title()).isEqualTo("Add feature X");
        assertThat(dto.repository().fullName()).isEqualTo("HephaestusTest/repo");
    }

    @Test
    @DisplayName("GitLab merge_request v1 deserializes to a populated DTO")
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
    @DisplayName("GitLab merge_request v2 (with unknown vendor fields) still deserializes")
    void gitlabMergeRequestV2() throws Exception {
        GitLabMergeRequestEventDTO dto = readFixture(
            "/integration-fixtures/gitlab/v2/merge_request.open.json",
            GitLabMergeRequestEventDTO.class
        );
        assertThat(dto.objectKind()).isEqualTo("merge_request");
        assertThat(dto.objectAttributes()).isNotNull();
        assertThat(dto.objectAttributes().title()).isEqualTo("Add feature X");
        assertThat(dto.project().pathWithNamespace()).isEqualTo("hephaestustest/smoke");
    }

    private <T> T readFixture(String classpath, Class<T> type) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("fixture %s must be on the classpath", classpath).isNotNull();
            return mapper.readValue(in, type);
        }
    }
}
