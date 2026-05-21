package de.tum.cit.aet.hephaestus.gitprovider.sync;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.gitprovider.webhook.GitLabSubjectBuilder;
import de.tum.cit.aet.hephaestus.gitprovider.webhook.github.GitHubSubjectBuilder;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * For every committed fixture, asserts the publisher-side subject built by
 * {@link GitLabSubjectBuilder}/{@link GitHubSubjectBuilder} starts with the consumer-side prefix
 * that {@link NatsConsumerService#buildSubjectPrefix(String, String)} subscribes to. Catches the
 * producer-consumer drift class that per-side unit tests cannot see in isolation.
 *
 * <p>Where namespace/project can't be reliably extracted (instance-level fallback to {@code ?}),
 * the consumer prefix doesn't apply; those fixtures are skipped.
 */
class SubjectGrammarRoundTripTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path GITLAB_DIR = Paths.get("src/test/resources/gitlab");
    private static final Path GITHUB_DIR = Paths.get("src/test/resources/github");

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
        String publisherSubject = GitLabSubjectBuilder.build(payload);
        String pathWithNamespace = payload.path("project").path("path_with_namespace").asText("");
        if (pathWithNamespace.isEmpty()) {
            pathWithNamespace = payload.path("path_with_namespace").asText("");
        }
        if (pathWithNamespace.contains("/")) {
            String consumerPrefix = NatsConsumerService.buildSubjectPrefix("gitlab", pathWithNamespace);
            assertThat(publisherSubject)
                .as("publisher %s should start with consumer prefix '%s.'", fixture.getFileName(), consumerPrefix)
                .startsWith(consumerPrefix + ".");
        }
    }

    @ParameterizedTest(name = "GitHub fixture {0}")
    @MethodSource("githubFixtures")
    void githubFixtureSubjectMatchesConsumerPrefix(Path fixture) throws IOException {
        JsonNode payload = MAPPER.readTree(Files.readAllBytes(fixture));
        JsonNode repository = payload.path("repository");
        if (repository.isMissingNode() || repository.isNull()) {
            return;
        }
        String owner = repository.path("owner").path("login").asText("");
        String repo = repository.path("name").asText("");
        if (owner.isEmpty() || repo.isEmpty()) {
            return;
        }
        String publisherSubject = GitHubSubjectBuilder.build(payload, eventTypeFromFilename(fixture));
        String consumerPrefix = NatsConsumerService.buildSubjectPrefix("github", owner + "/" + repo);
        assertThat(publisherSubject)
            .as("publisher %s should start with consumer prefix '%s.'", fixture.getFileName(), consumerPrefix)
            .startsWith(consumerPrefix + ".");
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
