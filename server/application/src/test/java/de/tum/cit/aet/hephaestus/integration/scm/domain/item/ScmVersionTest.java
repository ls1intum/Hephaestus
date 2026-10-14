package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class ScmVersionTest extends BaseUnitTest {
    @Test
    void shouldChangeVersionWhenBaseChangesAtTheSameHead() {
        var first = ScmVersion.pullRequest("a".repeat(40), "b".repeat(40), "Title", null);
        var second = ScmVersion.pullRequest("c".repeat(40), "b".repeat(40), "Title", null);
        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(ScmVersion.reviewedHead(first)).isEqualTo("b".repeat(40));
    }

    @Test
    void shouldDistinguishUnknownBodyAndEmptyBody() {
        assertThat(ScmVersion.ofContentDigest("Title", null)).isNotEqualTo(ScmVersion.ofContentDigest("Title", ""));
    }

    @Test
    void shouldChangeReviewVersionWhenSubmittedWordsChange() {
        var first = ScmVersion.pullRequestReview(
                "a".repeat(40),
                "b".repeat(40),
                "Title",
                null,
                new ScmVersion.ReviewContent("b".repeat(40), "First", "COMMENTED"));
        var second = ScmVersion.pullRequestReview(
                "a".repeat(40),
                "b".repeat(40),
                "Title",
                null,
                new ScmVersion.ReviewContent("b".repeat(40), "Edited", "COMMENTED"));
        assertThat(first).isNotEqualTo(second);
    }
}
