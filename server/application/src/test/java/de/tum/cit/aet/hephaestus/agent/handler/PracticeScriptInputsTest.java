package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PracticeScriptInputsTest extends BaseUnitTest {
    private final JsonMapper mapper = new JsonMapper();

    @Test
    void shouldWriteTheExactPinnedScopeAndDecimalDeveloper() {
        var assignment = RuntimeReviewTestData.assignment(Long.MAX_VALUE, null);
        var files = new PracticeScriptInputs(mapper)
                .stage(
                        RuntimeReviewTestData.review(ReviewKind.ITEM, assignment),
                        assignment,
                        mapper.createObjectNode().put("title", "A document"),
                        Instant.EPOCH,
                        null);
        var input = mapper.readTree(files.values().iterator().next());
        assertThat(input.path("contractVersion").asInt()).isEqualTo(3);
        assertThat(input.path("scope").path("item").path("id").asString()).isEqualTo("2");
        assertThat(input.path("scope").path("version").asString()).isEqualTo("docs:one");
        assertThat(input.path("about").path("developerId").asString()).isEqualTo(Long.toString(Long.MAX_VALUE));
        assertThat(input.path("practice").path("versionId").asString()).isEqualTo("32");
        assertThat(input.path("previous").isNull()).isTrue();
    }

    @Test
    void shouldKeepExplicitNobodyForAScanEvenWhenTheItemPracticeHasAnAuthor() {
        var assignment = RuntimeReviewTestData.assignment(5L, null);
        var files = new PracticeScriptInputs(mapper)
                .stage(
                        RuntimeReviewTestData.review(ReviewKind.SCAN, assignment),
                        assignment,
                        mapper.createObjectNode(),
                        Instant.EPOCH,
                        null);
        var input = mapper.readTree(files.values().iterator().next());
        assertThat(input.path("about").isNull()).isTrue();
        assertThat(input.path("scope").path("kind").asString()).isEqualTo("SCAN");
        assertThat(input.path("scope").has("baseVersion")).isFalse();
    }

    @Test
    void shouldRejectAnAssignmentFromAnotherReview() {
        var assigned = RuntimeReviewTestData.assignment(5L, null);
        var foreign = new ReviewAssignmentSnapshot(
                new ReviewAssignmentId(UUID.randomUUID(), 31, 2),
                assigned.item(),
                assigned.version(),
                5L,
                assigned.practiceSlug(),
                assigned.practice());
        assertThatThrownBy(() -> new PracticeScriptInputs(mapper)
                        .stage(
                                RuntimeReviewTestData.review(ReviewKind.ITEM, assigned),
                                foreign,
                                mapper.createObjectNode(),
                                Instant.EPOCH,
                                null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
