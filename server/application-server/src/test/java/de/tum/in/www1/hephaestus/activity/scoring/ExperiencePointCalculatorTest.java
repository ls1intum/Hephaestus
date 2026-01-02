package de.tum.in.www1.hephaestus.activity.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for ExperiencePointCalculator.
 */
@Tag("unit")
@DisplayName("ExperiencePointCalculator")
@ExtendWith(MockitoExtension.class)
class ExperiencePointCalculatorTest {

    @Mock
    private PullRequestRepository pullRequestRepository;

    private ExperiencePointCalculator calculator;

    @BeforeEach
    void setUp() {
        ExperiencePointProperties properties = new ExperiencePointProperties();
        properties.setSelfReviewAuthorLogins(List.of("Copilot", "dependabot[bot]"));
        calculator = new ExperiencePointCalculator(pullRequestRepository, properties);
    }

    @Nested
    @DisplayName("Self-Review Exclusion")
    class SelfReviewExclusion {

        @Test
        @DisplayName("excludes reviews when pull request author is configured bot and reviewer is assignee")
        void excludesReviewWhenAuthorIsBotAndReviewerIsAssignee() {
            User copilot = createUser(1L, "Copilot");
            User reviewer = createUser(2L, "reviewer");

            PullRequest pullRequest = createPullRequest(copilot);
            pullRequest.getAssignees().add(reviewer);

            PullRequestReview review = createApprovedReview(reviewer, pullRequest);

            double experiencePoints = calculator.calculateReviewExperiencePoints(List.of(review));

            assertThat(experiencePoints).isZero();
        }

        @Test
        @DisplayName("excludes reviews with case-insensitive author login matching")
        void matchesAuthorLoginCaseInsensitively() {
            User copilot = createUser(1L, "COPILOT"); // Uppercase
            User reviewer = createUser(2L, "reviewer");

            PullRequest pullRequest = createPullRequest(copilot);
            pullRequest.getAssignees().add(reviewer);

            PullRequestReview review = createApprovedReview(reviewer, pullRequest);

            double experiencePoints = calculator.calculateReviewExperiencePoints(List.of(review));

            assertThat(experiencePoints).isZero();
        }

        @Test
        @DisplayName("counts reviews when pull request author is not in exclusion list")
        void countsReviewWhenAuthorNotExcluded() {
            User author = createUser(1L, "regular-author");
            User reviewer = createUser(2L, "reviewer");

            PullRequest pullRequest = createPullRequest(author);

            PullRequestReview review = createApprovedReview(reviewer, pullRequest);

            double experiencePoints = calculator.calculateReviewExperiencePoints(List.of(review));

            assertThat(experiencePoints).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("counts reviews when reviewer is not an assignee on bot-authored pull request")
        void countsReviewWhenReviewerNotAssignee() {
            User copilot = createUser(1L, "Copilot");
            User reviewer = createUser(2L, "reviewer");
            User differentAssignee = createUser(3L, "someone-else");

            PullRequest pullRequest = createPullRequest(copilot);
            pullRequest.getAssignees().add(differentAssignee); // Reviewer is NOT assignee

            PullRequestReview review = createApprovedReview(reviewer, pullRequest);

            double experiencePoints = calculator.calculateReviewExperiencePoints(List.of(review));

            assertThat(experiencePoints).isGreaterThan(0.0);
        }
    }

    @Nested
    @DisplayName("Dismissed Review Exclusion")
    class DismissedReviewExclusion {

        @Test
        @DisplayName("excludes dismissed reviews from XP calculation - dismissed reviews yield zero XP")
        void excludesDismissedReviews() {
            User author = createUser(1L, "author");
            User reviewer = createUser(2L, "reviewer");

            PullRequest pullRequest = createPullRequest(author);

            PullRequestReview dismissedReview = createApprovedReview(reviewer, pullRequest);
            dismissedReview.setDismissed(true);

            double experiencePoints = calculator.calculateReviewExperiencePoints(List.of(dismissedReview));

            assertThat(experiencePoints)
                .as("Dismissed reviews should not earn XP - see ExperiencePointCalculator Javadoc for rationale")
                .isZero();
        }

        @Test
        @DisplayName("includes non-dismissed reviews in XP calculation")
        void includesNonDismissedReviews() {
            User author = createUser(1L, "author");
            User reviewer = createUser(2L, "reviewer");

            PullRequest pullRequest = createPullRequest(author);

            PullRequestReview activeReview = createApprovedReview(reviewer, pullRequest);
            activeReview.setDismissed(false);

            double experiencePoints = calculator.calculateReviewExperiencePoints(List.of(activeReview));

            assertThat(experiencePoints).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("only counts non-dismissed reviews when mix of dismissed and active exist")
        void mixedDismissedAndActiveReviews() {
            User author = createUser(1L, "author");
            User reviewer1 = createUser(2L, "reviewer1");
            User reviewer2 = createUser(3L, "reviewer2");

            PullRequest pullRequest = createPullRequest(author);

            PullRequestReview dismissedReview = createApprovedReview(reviewer1, pullRequest);
            dismissedReview.setDismissed(true);

            PullRequestReview activeReview = createApprovedReview(reviewer2, pullRequest);
            activeReview.setDismissed(false);

            double mixedXp = calculator.calculateReviewExperiencePoints(List.of(dismissedReview, activeReview));
            double activeOnlyXp = calculator.calculateReviewExperiencePoints(List.of(activeReview));

            // XP from mixed list should equal XP from active-only list (dismissed is excluded)
            assertThat(mixedXp).isEqualTo(activeOnlyXp);
        }
    }

    @Nested
    @DisplayName("Complexity Scoring")
    class ComplexityScoring {

        @Test
        @DisplayName("returns complexity score 1 for simple pull requests")
        void simpleComplexity() {
            PullRequest pullRequest = new PullRequest();
            pullRequest.setChangedFiles(1);
            pullRequest.setCommits(1);
            pullRequest.setAdditions(10);
            pullRequest.setDeletions(5);

            int score = calculator.calculateComplexityScore(pullRequest);

            assertThat(score).isEqualTo(1);
        }

        @Test
        @DisplayName("returns complexity score 33 for overly complex pull requests")
        void overlyComplexComplexity() {
            PullRequest pullRequest = new PullRequest();
            pullRequest.setChangedFiles(100);
            pullRequest.setCommits(50);
            pullRequest.setAdditions(3000);
            pullRequest.setDeletions(2000);

            int score = calculator.calculateComplexityScore(pullRequest);

            assertThat(score).isEqualTo(33);
        }
    }

    @Nested
    @DisplayName("XP Constants")
    class ExperiencePointConstants {

        @Test
        @DisplayName("XP_PULL_REQUEST_OPENED has expected value")
        void pullRequestOpenedConstant() {
            assertThat(ExperiencePointCalculator.XP_PULL_REQUEST_OPENED).isEqualTo(1.0);
        }

        @Test
        @DisplayName("XP_PULL_REQUEST_MERGED has expected value")
        void pullRequestMergedConstant() {
            assertThat(ExperiencePointCalculator.XP_PULL_REQUEST_MERGED).isEqualTo(1.0);
        }

        @Test
        @DisplayName("XP_REVIEW_COMMENT has expected value")
        void reviewCommentConstant() {
            assertThat(ExperiencePointCalculator.XP_REVIEW_COMMENT).isEqualTo(0.5);
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private User createUser(Long id, String login) {
        User user = new User();
        user.setId(id);
        user.setLogin(login);
        return user;
    }

    private PullRequest createPullRequest(User author) {
        PullRequest pullRequest = new PullRequest();
        pullRequest.setAuthor(author);
        pullRequest.setCommits(1);
        pullRequest.setAdditions(1);
        pullRequest.setDeletions(1);
        pullRequest.setChangedFiles(1);
        return pullRequest;
    }

    private PullRequestReview createApprovedReview(User reviewer, PullRequest pullRequest) {
        PullRequestReview review = new PullRequestReview();
        review.setAuthor(reviewer);
        review.setPullRequest(pullRequest);
        review.setState(PullRequestReview.State.APPROVED);
        review.setHtmlUrl("https://github.com/test/review");
        review.setSubmittedAt(Instant.now());
        return review;
    }
}
