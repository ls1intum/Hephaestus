package de.tum.in.www1.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PullRequestReviewSubmissionRequest")
class PullRequestReviewSubmissionRequestTest extends BaseUnitTest {

    private EventPayload.PullRequestData samplePullRequestData() {
        return new EventPayload.PullRequestData(
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
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should accept valid input")
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
        @DisplayName("should reject null pullRequest")
        void shouldRejectNullPullRequest() {
            assertThatThrownBy(() -> new PullRequestReviewSubmissionRequest(null, "branch", "sha", "main"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pullRequest");
        }

        @Test
        @DisplayName("should reject null headRefName")
        void shouldRejectNullHeadRefName() {
            assertThatThrownBy(() ->
                new PullRequestReviewSubmissionRequest(samplePullRequestData(), null, "sha", "main")
            )
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("headRefName");
        }

        @Test
        @DisplayName("should reject blank headRefName")
        void shouldRejectBlankHeadRefName() {
            assertThatThrownBy(() ->
                new PullRequestReviewSubmissionRequest(samplePullRequestData(), "  ", "sha", "main")
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headRefName");
        }

        @Test
        @DisplayName("should reject null headRefOid")
        void shouldRejectNullHeadRefOid() {
            assertThatThrownBy(() ->
                new PullRequestReviewSubmissionRequest(samplePullRequestData(), "branch", null, "main")
            )
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("headRefOid");
        }

        @Test
        @DisplayName("should reject blank headRefOid")
        void shouldRejectBlankHeadRefOid() {
            assertThatThrownBy(() ->
                new PullRequestReviewSubmissionRequest(samplePullRequestData(), "branch", " ", "main")
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headRefOid");
        }

        @Test
        @DisplayName("should reject null baseRefName")
        void shouldRejectNullBaseRefName() {
            assertThatThrownBy(() ->
                new PullRequestReviewSubmissionRequest(samplePullRequestData(), "branch", "sha", null)
            )
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("baseRefName");
        }

        @Test
        @DisplayName("should reject blank baseRefName")
        void shouldRejectBlankBaseRefName() {
            assertThatThrownBy(() ->
                new PullRequestReviewSubmissionRequest(samplePullRequestData(), "branch", "sha", "  ")
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseRefName");
        }
    }
}
