package de.tum.cit.aet.hephaestus.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.scm.github.pullrequest.dto.GitHubPullRequestEventDTO;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.pullrequest.dto.GitLabMergeRequestEventDTO;
import io.nats.client.Message;
import java.io.InputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the tolerant-reader contract for the SCM webhook DTOs against two fixtures per vendor,
 * exercised through the REAL production deserialization path and the REAL production mapper.
 *
 * <p>Production deserializes every NATS webhook payload via {@link NatsMessageDeserializer}, which
 * delegates to the Spring-Boot-autoconfigured Jackson&nbsp;3 {@code tools.jackson.databind.ObjectMapper}
 * bean. This test boots only {@link JacksonAutoConfiguration} (no DB, web, or Docker), so the
 * injected {@link ObjectMapper} is the exact bean production uses — built from the {@code spring.jackson.*}
 * stanza in {@code application.yml} ({@code fail-on-unknown-properties: false},
 * {@code fail-on-null-for-primitives: false}, {@code default-property-inclusion: non_null},
 * UTC time zone, ISO date-times). The test then drives {@link NatsMessageDeserializer} over that
 * bean, exactly as the live message handlers do.
 *
 * <p>Why a context test rather than a hand-built {@code JsonMapper}: the tolerant-reader behaviour
 * here is mapper-configuration, not annotation, behaviour. A hand-rolled builder would re-encode the
 * config the test claims to verify — drifting silently if {@code application.yml} changes, and (as
 * the earlier Jackson&nbsp;2 version did) testing an engine production does not run. Booting the real
 * auto-config makes the assertions track production faithfully.
 *
 * <p>The earlier version of this test built a hand-rolled Jackson&nbsp;<b>2</b>
 * ({@code com.fasterxml.jackson.databind}) mapper and leaned on {@code @JsonIgnoreProperties} for
 * unknown-field tolerance. That was theatre: production runs a different engine (Jackson&nbsp;3),
 * where unknown-field tolerance is the engine default rather than an annotation effect, so the green
 * told us nothing about whether production tolerates the payload.
 *
 * <p>v1 fixtures are minimal "as-shipped today" examples. v2 fixtures are SYNTHETIC
 * forward-compatibility fixtures: the v1 payload with fabricated unknown keys injected at the top
 * level and at every nesting level, simulating a vendor adding fields the DTO does not declare.
 *
 * <p>The behaviour under test, on the real engine, is the tolerant-reader property in two halves:
 * (1) the reader must deserialize the unknown-field payload WITHOUT throwing — the real regression
 * guard against a future flip of {@code fail-on-unknown-properties} to {@code true}; and (2) it must
 * still map the right JSON path to the right DTO field, including deep paths
 * ({@code pull_request.base.ref}) and a typed coercion ({@code created_at} -> {@code Instant}), so a
 * mis-wired {@code @JsonProperty} is caught. The v2 tests additionally assert the fixture genuinely
 * carries injected unknown keys, so the tolerance assertion cannot quietly become vacuous if someone
 * strips the synthetic fields.
 */
@Tag("unit")
@SpringBootTest(classes = JacksonAutoConfiguration.class)
// ConfigDataApplicationContextInitializer makes the slice read application.yml, so the
// autoconfigured mapper binds the real spring.jackson.* stanza (fail-on-unknown-properties,
// fail-on-null-for-primitives, etc.) rather than falling back to bare engine defaults. Without
// it, a regression that flips fail-on-unknown-properties=true would not be caught here.
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
class MultiVersionFixtureTest {

    /** The exact production mapper bean — autoconfigured from {@code spring.jackson.*}. */
    @Autowired
    private ObjectMapper jackson3;

    private NatsMessageDeserializer deserializer;

    private NatsMessageDeserializer deserializer() {
        if (deserializer == null) {
            deserializer = new NatsMessageDeserializer(jackson3);
        }
        return deserializer;
    }

    @Test
    void githubPullRequestV1_extractsKnownFields() throws Exception {
        GitHubPullRequestEventDTO dto = deserialize(
            "/integration-fixtures/github/v1/pull_request.opened.json",
            GitHubPullRequestEventDTO.class
        );
        assertKnownGithubFields(dto);
    }

    @Test
    void githubPullRequestV2_tolerantOnRealEngineAndExtractsKnownFields() throws Exception {
        String classpath = "/integration-fixtures/github/v2/pull_request.opened.json";

        // Guard: the fixture must actually inject unknown fields the DTO does not declare,
        // otherwise the tolerance assertion below would be vacuous.
        JsonNode raw = jackson3.readTree(readBytes(classpath));
        assertThat(raw.has("_future_top_level")).as("fixture must carry an injected unknown top-level key").isTrue();
        assertThat(raw.path("repository").has("_visibility_v2"))
            .as("fixture must carry an injected unknown nested key")
            .isTrue();

        // (1) Tolerance on the REAL mapper: deserializing the unknown-field payload must not throw.
        // This is the live regression guard — it fails if production's mapper ever flips to
        // fail-on-unknown-properties=true, and it would still pass if the DTO dropped
        // @JsonIgnoreProperties (the mapper ignores unknowns by config, not by annotation).
        GitHubPullRequestEventDTO dto = deserialize(classpath, GitHubPullRequestEventDTO.class);

        // (2) Correctness: every known field — including deep paths — is still mapped, unaffected
        // by the unknown keys.
        assertKnownGithubFields(dto);
    }

    @Test
    void gitlabMergeRequestV1_extractsKnownFields() throws Exception {
        GitLabMergeRequestEventDTO dto = deserialize(
            "/integration-fixtures/gitlab/v1/merge_request.open.json",
            GitLabMergeRequestEventDTO.class
        );
        assertKnownGitlabFields(dto);
    }

    @Test
    void gitlabMergeRequestV2_tolerantOnRealEngineAndExtractsKnownFields() throws Exception {
        String classpath = "/integration-fixtures/gitlab/v2/merge_request.open.json";

        // Guard: the fixture must actually inject unknown fields the DTO does not declare.
        JsonNode raw = jackson3.readTree(readBytes(classpath));
        assertThat(raw.has("_event_uuid_v2")).as("fixture must carry an injected unknown top-level key").isTrue();
        assertThat(raw.path("object_attributes").has("_blocking_discussions_resolved_v2"))
            .as("fixture must carry an injected unknown nested key")
            .isTrue();

        // (1) Tolerance on the REAL mapper.
        GitLabMergeRequestEventDTO dto = deserialize(classpath, GitLabMergeRequestEventDTO.class);

        // (2) Correctness.
        assertKnownGitlabFields(dto);
    }

    /** Tolerance is the contract under test — assert it never throws for either v2 fixture. */
    @Test
    void v2Fixtures_neverThrowThroughTheRealDeserializer() {
        assertThatCode(() ->
            deserialize("/integration-fixtures/github/v2/pull_request.opened.json", GitHubPullRequestEventDTO.class)
        )
            .as("real Jackson-3 production mapper tolerates unknown GitHub fields")
            .doesNotThrowAnyException();
        assertThatCode(() ->
            deserialize("/integration-fixtures/gitlab/v2/merge_request.open.json", GitLabMergeRequestEventDTO.class)
        )
            .as("real Jackson-3 production mapper tolerates unknown GitLab fields")
            .doesNotThrowAnyException();
    }

    /**
     * Asserts the GitHub DTO mapped the right JSON path to the right field, including a deep nested
     * path and a typed coercion — the assertions that have production meaning (a mis-wired
     * {@code @JsonProperty} or a removed JavaTime handling would break them), as opposed to literal
     * echoes.
     */
    private static void assertKnownGithubFields(GitHubPullRequestEventDTO dto) {
        assertThat(dto.action()).isEqualTo("opened");
        assertThat(dto.number()).isEqualTo(42);
        assertThat(dto.pullRequest()).isNotNull();
        assertThat(dto.pullRequest().title()).isEqualTo("Add feature X");
        // Deep path: pull_request.base.ref / head.ref — proves nested @JsonProperty wiring.
        assertThat(dto.pullRequest().base()).isNotNull();
        assertThat(dto.pullRequest().base().ref()).isEqualTo("main");
        assertThat(dto.pullRequest().head().ref()).isEqualTo("feature-x");
        // Typed coercion: created_at string -> Instant. A removed JavaTime handling or a bad
        // @JsonProperty would surface here, not in a literal echo of a String.
        assertThat(dto.pullRequest().createdAt()).isEqualTo("2026-05-25T10:00:00Z");
        assertThat(dto.repository().fullName()).isEqualTo("HephaestusTest/repo");
    }

    private static void assertKnownGitlabFields(GitLabMergeRequestEventDTO dto) {
        assertThat(dto.objectKind()).isEqualTo("merge_request");
        assertThat(dto.objectAttributes()).isNotNull();
        assertThat(dto.objectAttributes().title()).isEqualTo("Add feature X");
        // Underscored JSON keys -> camelCase fields via @JsonProperty.
        assertThat(dto.objectAttributes().sourceBranch()).isEqualTo("feature-x");
        assertThat(dto.objectAttributes().targetBranch()).isEqualTo("main");
        assertThat(dto.objectAttributes().authorId()).isEqualTo(1L);
        assertThat(dto.project()).isNotNull();
        assertThat(dto.project().pathWithNamespace()).isEqualTo("hephaestustest/smoke");
    }

    /**
     * Drives the real production deserialization path: wrap the fixture bytes in a NATS
     * {@link Message} and call {@link NatsMessageDeserializer#deserialize}, exactly as the live
     * message handlers do.
     */
    private <T> T deserialize(String classpath, Class<T> type) throws Exception {
        byte[] payload = readBytes(classpath);
        Message message = mock(Message.class);
        when(message.getData()).thenReturn(payload);
        return deserializer().deserialize(message, type);
    }

    private byte[] readBytes(String classpath) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("fixture %s must be on the classpath", classpath).isNotNull();
            return in.readAllBytes();
        }
    }
}
