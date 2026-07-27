package de.tum.cit.aet.hephaestus.integration.scm.github.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.GitHubGraphQlConfig;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHIssueCommentConnection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proves the backwards half of {@code PageInfo} survives decoding into the generated model.
 *
 * <p>{@code GithubFeedbackChannel.findExistingSummary} decides whether an older page exists from
 * {@code GHPageInfo.getHasPreviousPage()} and continues from {@code getStartCursor()}. Every other test of
 * that method stubs {@code ClientResponseField.toEntity} and hands back an already-built connection, so it
 * decodes no JSON: were those two properties dropped on the way in, {@code hasPreviousPage} would read
 * {@code false} and the scan would report a confirmed {@code ABSENT} after one page — licensing a duplicate
 * summary on any thread longer than the page size. That is the seam this test covers.
 *
 * <p>The payload is a verbatim page from api.github.com (issue comments, {@code last: 1}), cursors included.
 */
class NewestCommentsPageDecodingTest extends BaseUnitTest {

    private static final String LIVE_PAGE = """
        {
          "pageInfo": {
            "hasPreviousPage": true,
            "startCursor": "Y3Vyc29yOnYyOpHPAAAAASmmmdk="
          },
          "nodes": [
            { "id": "IC_kwDOL-LvAM8AAAABKaaZ2Q", "body": "a comment body" }
          ]
        }
        """;

    /** Mirrors the {@code spring.jackson.deserialization.*} settings the GitHub codecs inherit. */
    private static JsonMapper productionMapper() {
        JsonMapper base = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
        return GitHubGraphQlConfig.gitHubGraphQlObjectMapper(base);
    }

    @Test
    void backwardsPageInfoDecodesIntoTheGeneratedModel() {
        GHIssueCommentConnection connection = productionMapper().readValue(LIVE_PAGE, GHIssueCommentConnection.class);

        assertThat(connection.getPageInfo()).isNotNull();
        assertThat(connection.getPageInfo().getHasPreviousPage()).isTrue();
        assertThat(connection.getPageInfo().getStartCursor()).isEqualTo("Y3Vyc29yOnYyOpHPAAAAASmmmdk=");
        assertThat(connection.getNodes())
            .singleElement()
            .satisfies(node -> {
                assertThat(node.getId()).isEqualTo("IC_kwDOL-LvAM8AAAABKaaZ2Q");
                assertThat(node.getBody()).isEqualTo("a comment body");
            });
    }
}
