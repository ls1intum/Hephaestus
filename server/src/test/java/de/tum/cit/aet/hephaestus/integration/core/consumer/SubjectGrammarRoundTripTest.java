package de.tum.cit.aet.hephaestus.integration.core.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.github.webhook.GithubSubjectKeyDeriver;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.webhook.GitlabSubjectKeyDeriver;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * For every committed fixture, asserts the publisher-side subject built by the per-kind
 * {@code SubjectKeyDeriver} starts with the consumer-side prefix that
 * {@link ConsumerSubjectMath#buildSubjectPrefix(String, String)} subscribes to. This is the
 * producer↔consumer agreement guard documented in CLAUDE.md §8 — it catches the drift class
 * that per-side unit tests cannot see in isolation (a deriver that emits a subject no consumer
 * filter matches publishes into the stream but is never delivered).
 *
 * <p>Where namespace/project can't be reliably extracted (instance-level fallback to {@code ?}),
 * the consumer prefix doesn't apply; those fixtures are skipped.
 */
class SubjectGrammarRoundTripTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path GITLAB_DIR = Paths.get("src/test/resources/gitlab");
    private static final Path GITHUB_DIR = Paths.get("src/test/resources/github");

    private static final GithubSubjectKeyDeriver GITHUB = new GithubSubjectKeyDeriver();
    private static final GitlabSubjectKeyDeriver GITLAB = new GitlabSubjectKeyDeriver();

    static Stream<Path> gitlabFixtures() throws IOException {
        return listJson(GITLAB_DIR);
    }

    static Stream<Path> githubFixtures() throws IOException {
        return listJson(GITHUB_DIR);
    }

    private static Stream<Path> listJson(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return Stream.empty();
        }
        return Files.list(dir)
            .filter(p -> p.getFileName().toString().endsWith(".json"))
            .sorted();
    }

    @ParameterizedTest(name = "GitLab fixture {0}")
    @MethodSource("gitlabFixtures")
    void gitlabFixtureSubjectMatchesConsumerPrefix(Path fixture) throws IOException {
        JsonNode payload = MAPPER.readTree(Files.readAllBytes(fixture));
        String pathWithNamespace = payload.path("project").path("path_with_namespace").asString("");
        if (pathWithNamespace.isEmpty()) {
            pathWithNamespace = payload.path("path_with_namespace").asString("");
        }
        if (!pathWithNamespace.contains("/")) {
            return;
        }
        String publisherSubject = GITLAB.deriveSubject(payload, Map.of());
        String consumerPrefix = ConsumerSubjectMath.buildSubjectPrefix("gitlab", pathWithNamespace);
        assertThat(publisherSubject)
            .as("publisher %s should start with consumer prefix '%s.'", fixture.getFileName(), consumerPrefix)
            .startsWith(consumerPrefix + ".");
    }

    @ParameterizedTest(name = "GitHub fixture {0}")
    @MethodSource("githubFixtures")
    void githubFixtureSubjectMatchesConsumerPrefix(Path fixture) throws IOException {
        JsonNode payload = MAPPER.readTree(Files.readAllBytes(fixture));
        JsonNode repository = payload.path("repository");
        String owner = repository.path("owner").path("login").asString("");
        String repo = repository.path("name").asString("");
        if (owner.isEmpty() || repo.isEmpty()) {
            return;
        }
        String publisherSubject = GITHUB.deriveSubject(
            payload,
            Map.of("X-GitHub-Event", eventTypeFromFilename(fixture))
        );
        String consumerPrefix = ConsumerSubjectMath.buildSubjectPrefix("github", owner + "/" + repo);
        assertThat(publisherSubject)
            .as("publisher %s should start with consumer prefix '%s.'", fixture.getFileName(), consumerPrefix)
            .startsWith(consumerPrefix + ".");
    }

    /**
     * Explicit mixed-case guard: no committed fixture is guaranteed to carry uppercase, but GitLab
     * paths are case-sensitive and may. If the producer ever lowercases path segments while the
     * consumer (fed the case-preserved stored {@code nameWithOwner}) does not, this fails.
     */
    @Test
    void gitlabMixedCasePathRoundTrips() throws IOException {
        String pathWithNamespace = "MyGroup/Sub-Group/My.Project";
        JsonNode payload = MAPPER.readTree(
            "{\"object_kind\":\"merge_request\",\"project\":{\"path_with_namespace\":\"" + pathWithNamespace + "\"}}"
        );
        String publisherSubject = GITLAB.deriveSubject(payload, Map.of());
        String consumerPrefix = ConsumerSubjectMath.buildSubjectPrefix("gitlab", pathWithNamespace);
        assertThat(publisherSubject).startsWith(consumerPrefix + ".");
    }

    private static String eventTypeFromFilename(Path fixture) {
        String name = fixture.getFileName().toString();
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - ".json".length());
        }
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
