package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
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

    @Test
    void shouldKeepTheApprovedCommitSeparateFromTheReviewedHead() {
        var version = ScmVersion.pullRequestReview(
                "a".repeat(40),
                "b".repeat(40),
                "Title",
                null,
                new ScmVersion.ReviewContent("c".repeat(40), "Looks good", "APPROVED"));
        var parsed = ScmVersion.parse(version).orElseThrow();
        assertThat(parsed.base()).isEqualTo("a".repeat(40));
        assertThat(parsed.head()).isEqualTo("b".repeat(40));
        assertThat(parsed.reviewCommit()).isEqualTo("c".repeat(40));
    }

    @Test
    void shouldRefuseAReviewVersionWhoseRecordedBaseContradictsItsRange() {
        var version = ScmVersion.pullRequest("a".repeat(40), "b".repeat(40), "Title", null);
        var contradictory = new ItemVersion(version.value(), "c".repeat(40));
        assertThat(ScmVersion.parse(contradictory)).isEmpty();
        assertThatThrownBy(() -> ScmVersion.reviewedHead(contradictory)).isInstanceOf(IllegalArgumentException.class);
    }
}
