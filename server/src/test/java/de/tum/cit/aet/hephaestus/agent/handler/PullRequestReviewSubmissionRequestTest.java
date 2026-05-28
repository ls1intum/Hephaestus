package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.core.events.RepositoryRef;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PullRequestReviewSubmissionRequestTest extends BaseUnitTest {

    private ScmEventPayload.PullRequestData samplePullRequestData() {
        return new ScmEventPayload.PullRequestData(
            456L,
            42,
            "Fix bug",
            "Body",
            Issue.State.OPEN,
            false,
            false,
            10,
            5,
            3,
            "https://github.com/owner/repo/pull/42",
            new RepositoryRef(123L, "owner/repo", "main"),
            789L,
            Instant.now(),
            Instant.now(),
            null,
            null,
            null
        );
    }

    @Nested
    class Construction {

        @Test
        void shouldAcceptValidInput() {
            var request = new PullRequestReviewSubmissionRequest(
                samplePullRequestData(),
                "feature/x",
                "abc123",
                "main"
            );

            assertThat(request.pullRequest()).isNotNull();
            assertThat(request.headRefName()).isEqualTo("feature/x");
            assertThat(request.headRefOid()).isEqualTo("abc123");
            assertThat(request.baseRefName()).isEqualTo("main");
        }

        @Test
        void shouldRejectNullPullRequest() {
            assertThatThrownBy(() -> new PullRequestReviewSubmissionRequest(null, "branch", "sha", "main"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pullRequest");
        }

        @Test
        void shouldRejectNullRepository() {
            var prDataNoRepo = new ScmEventPayload.PullRequestData(
                456L,
                42,
                "Fix bug",
                "Body",
                Issue.State.OPEN,
                false,
                false,
                10,
                5,
                3,
                "https://github.com/owner/repo/pull/42",
                null, // null repository
                789L,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null
            );

            assertThatThrownBy(() -> new PullRequestReviewSubmissionRequest(prDataNoRepo, "branch", "sha", "main"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repository");
        }

        @Test
        void shouldRejectNullHeadRefName() {
            assertThatThrownBy(() ->
                new PullRequestReviewSubmissionRequest(samplePullRequestData(), null, "sha", "main")
            )
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("headRefName");
        }

        @Test
        void shouldRejectBlankHeadRefName() {
            assertThatThrownBy(() ->
                new PullRequestReviewSubmissionRequest(samplePullRequestData(), "  ", "sha", "main")
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headRefName");
        }

        @Test
        void shouldRejectNullHeadRefOid() {
            assertThatThrownBy(() ->
                new PullRequestReviewSubmissionRequest(samplePullRequestData(), "branch", null, "main")
            )
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("headRefOid");
        }

        @Test
        void shouldRejectBlankHeadRefOid() {
            assertThatThrownBy(() ->
                new PullRequestReviewSubmissionRequest(samplePullRequestData(), "branch", " ", "main")
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headRefOid");
        }

        @Test
        void shouldRejectNullBaseRefName() {
            assertThatThrownBy(() ->
                new PullRequestReviewSubmissionRequest(samplePullRequestData(), "branch", "sha", null)
            )
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("baseRefName");
        }

        @Test
        void shouldRejectBlankBaseRefName() {
            assertThatThrownBy(() ->
                new PullRequestReviewSubmissionRequest(samplePullRequestData(), "branch", "sha", "  ")
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseRefName");
        }
    }
}
