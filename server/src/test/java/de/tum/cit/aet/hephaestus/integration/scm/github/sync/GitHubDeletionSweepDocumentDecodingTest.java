package de.tum.cit.aet.hephaestus.integration.scm.github.sync;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.GitHubGraphQlConfig;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHIssueConnection;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHPullRequestConnection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proves the deletion sweep's listing documents actually decode into the model types the sweep asks
 * for, using the production Jackson configuration.
 *
 * <h2>Why this test exists</h2>
 *
 * {@code GetRepositoryPullRequestNumbers} shipped without selecting {@code __typename} and failed on
 * every repository with pull requests. {@link GHPullRequestConnection}'s nodes are
 * {@code GHPullRequest}, which implements {@code GHProjectV2ItemContent}, whose Jackson mixin
 * declares {@code @JsonTypeInfo(use = NAME, property = "__typename", defaultImpl = GHIssue.class)}.
 * Jackson inherits that type resolver onto the concrete node type, so a node without
 * {@code __typename} resolves to the default {@code GHIssue} — not a {@code GHPullRequest} — and
 * throws {@code InvalidTypeIdException}. The sweep is fail-closed, so it swallowed this as
 * "cannot verify, delete nothing" and the pull-request half of the feature was silently dead.
 *
 * <p>The pre-existing {@code GitHubDeletionSweepServiceTest} could not catch it: it mocks
 * {@code ClientResponseField.toEntity} to hand back an already-built connection object, so no JSON is
 * ever decoded and the document's selection set is never exercised. The failure lives exactly in the
 * seam that mock replaces — between the committed document text and the mixin configuration.
 *
 * <h2>Why it is not theatre</h2>
 *
 * The JSON is not a hand-written fixture that a developer would keep in sync with the document by
 * hand. It is <em>derived from the committed document's own node selection set</em>, and decoded with
 * {@link GitHubGraphQlConfig#gitHubGraphQlObjectMapper} — the same factory the production WebClient
 * uses. Delete {@code __typename} from either document and the derived JSON loses it too, and these
 * tests go red with the identical exception seen in production. The coupling runs document → JSON →
 * production mixins, with nothing restated by hand in between.
 */
class GitHubDeletionSweepDocumentDecodingTest extends BaseUnitTest {

    /** Captures the field names selected inside the first {@code nodes { ... }} block. */
    private static final Pattern NODES_BLOCK = Pattern.compile("nodes\\s*\\{([^}]*)\\}");

    /**
     * Sample values for every field these documents are allowed to select, keyed by GraphQL type name.
     * An unknown field fails the test rather than being silently skipped: a new selection must be
     * proven decodable here too, which is the whole point.
     */
    private static Object sampleValueFor(String graphQlTypeName, String fieldName) {
        return switch (fieldName) {
            case "__typename" -> graphQlTypeName;
            case "number" -> 42;
            default -> throw new IllegalStateException(
                "Document selects field '" +
                    fieldName +
                    "' that this test has no sample value for. Add one so the new selection is proven " +
                    "to decode, or drop the field from the document."
            );
        };
    }

    /**
     * Mirrors the two {@code spring.jackson.deserialization.*} settings from {@code application.yml}
     * that the GitHub codecs inherit through the application-wide mapper.
     */
    private static JsonMapper productionMapper() {
        JsonMapper base = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
        return GitHubGraphQlConfig.gitHubGraphQlObjectMapper(base);
    }

    @Test
    void shouldDecodePullRequestNumbersDocumentSelectionIntoPullRequestConnection() {
        // Arrange
        String json = responseJsonForDocument("GetRepositoryPullRequestNumbers", "PullRequest");

        // Act
        GHPullRequestConnection connection = productionMapper().readValue(json, GHPullRequestConnection.class);

        // Assert
        assertThat(connection.getNodes()).extracting("number").containsExactly(42);
    }

    @Test
    void shouldDecodeIssueNumbersDocumentSelectionIntoIssueConnection() {
        // Arrange
        String json = responseJsonForDocument("GetRepositoryIssueNumbers", "Issue");

        // Act
        GHIssueConnection connection = productionMapper().readValue(json, GHIssueConnection.class);

        // Assert
        assertThat(connection.getNodes()).extracting("number").containsExactly(42);
    }

    @Test
    void shouldReconcileNodeCountWithTotalCountSoTheSweepCanAuthorizeDeletion() {
        // The sweep refuses to delete unless node count equals the server's totalCount, so a document
        // whose connection drops totalCount on decode would disarm the sweep just as thoroughly as a
        // decode failure — silently, and while reporting success.

        // Arrange
        String json = responseJsonForDocument("GetRepositoryPullRequestNumbers", "PullRequest");

        // Act
        GHPullRequestConnection connection = productionMapper().readValue(json, GHPullRequestConnection.class);

        // Assert
        assertThat(connection.getTotalCount()).isEqualTo(connection.getNodes().size());
        assertThat(connection.getPageInfo().getHasNextPage()).isFalse();
    }

    /**
     * Builds the JSON GitHub would return for {@code documentName}'s node selection — one node, with
     * exactly the fields the committed document asks for and nothing else.
     */
    private static String responseJsonForDocument(String documentName, String graphQlTypeName) {
        Set<String> selectedFields = nodeSelectionOf(readDocument(documentName));
        assertThat(selectedFields)
            .as("document %s selects no node fields — the parser or the document changed shape", documentName)
            .isNotEmpty();

        Map<String, Object> node = new LinkedHashMap<>();
        for (String field : selectedFields) {
            node.put(field, sampleValueFor(graphQlTypeName, field));
        }

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("totalCount", 1);
        connection.put("pageInfo", Map.of("hasNextPage", false, "endCursor", "Y3Vyc29yOnYyOpHOAA"));
        connection.put("nodes", List.of(node));

        return JsonMapper.builder().build().writeValueAsString(connection);
    }

    /** Extracts the flat field names inside the document's {@code nodes { ... }} selection. */
    private static Set<String> nodeSelectionOf(String document) {
        Matcher matcher = NODES_BLOCK.matcher(stripComments(document));
        assertThat(matcher.find()).as("document has no nodes { ... } selection").isTrue();

        Set<String> fields = new LinkedHashSet<>();
        for (String line : matcher.group(1).split("\\s+")) {
            if (!line.isBlank()) {
                fields.add(line.trim());
            }
        }
        return fields;
    }

    /** Comment lines may mention node selections or field names in prose; they are not selections. */
    private static String stripComments(String document) {
        return document
            .lines()
            .filter(line -> !line.strip().startsWith("#"))
            .reduce("", (a, b) -> a + "\n" + b);
    }

    private static String readDocument(String documentName) {
        ClassPathResource resource = new ClassPathResource("graphql/github/operations/" + documentName + ".graphql");
        try (InputStream stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read GraphQL document: " + documentName, e);
        }
    }
}
