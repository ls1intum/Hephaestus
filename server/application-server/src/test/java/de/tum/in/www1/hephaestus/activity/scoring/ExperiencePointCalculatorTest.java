package de.tum.in.www1.hephaestus.activity.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Unit tests for ExperiencePointCalculator.
 */
@DisplayName("ExperiencePointCalculator")
class ExperiencePointCalculatorTest extends BaseUnitTest {

    @Mock
    private PullRequestRepository pullRequestRepository;

    private ExperiencePointCalculator calculator;

    @BeforeEach
    void setUp() {
        // Use production defaults: only reviews and review comments earn XP
        // PR opened/merged/ready and issue created are 0.0 (to be enabled in future release)
        ExperiencePointProperties properties = new ExperiencePointProperties(
            List.of("Copilot", "dependabot[bot]"),
            new ExperiencePointProperties.ReviewWeights(2.0, 2.5, 1.5),
            new ExperiencePointProperties.XpAwards(0.0, 0.0, 0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            1000.0
        );
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
    @DisplayName("Dismissed Review Handling")
    class DismissedReviewHandling {

        @Test
        @DisplayName("includes dismissed reviews in XP calculation - effort is still valuable")
        void includesDismissedReviews() {
            User author = createUser(1L, "author");
            User reviewer = createUser(2L, "reviewer");

            PullRequest pullRequest = createPullRequest(author);

            PullRequestReview dismissedReview = createApprovedReview(reviewer, pullRequest);
            dismissedReview.setDismissed(true);

            double experiencePoints = calculator.calculateReviewExperiencePoints(List.of(dismissedReview));

            assertThat(experiencePoints)
                .as("Dismissed reviews should earn XP - the effort of providing feedback is valuable")
                .isGreaterThan(0.0);
        }

        @Test
        @DisplayName("dismissed and non-dismissed reviews both earn XP")
        void bothDismissedAndActiveEarnXp() {
            User author = createUser(1L, "author");
            User reviewer = createUser(2L, "reviewer");

            PullRequest pullRequest = createPullRequest(author);

            PullRequestReview activeReview = createApprovedReview(reviewer, pullRequest);
            activeReview.setDismissed(false);

            PullRequestReview dismissedReview = createApprovedReview(reviewer, pullRequest);
            dismissedReview.setDismissed(true);

            double activeXp = calculator.calculateReviewExperiencePoints(List.of(activeReview));
            double dismissedXp = calculator.calculateReviewExperiencePoints(List.of(dismissedReview));

            // Both should earn XP - dismissed status doesn't affect XP
            assertThat(activeXp).isGreaterThan(0.0);
            assertThat(dismissedXp).isGreaterThan(0.0);
            assertThat(dismissedXp).isEqualTo(activeXp);
        }

        @Test
        @DisplayName("mixed dismissed and active reviews all count toward XP")
        void mixedDismissedAndActiveReviewsAllCount() {
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

            // XP from mixed list should be greater than active-only (both reviews count)
            assertThat(mixedXp).isGreaterThan(activeOnlyXp);
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
    @DisplayName("Standalone Review Comment XP")
    class StandaloneReviewCommentXp {

        @Test
        @DisplayName("substantive comment (>50 chars) returns full XP_REVIEW_COMMENT")
        void calculateStandaloneReviewCommentXp_substantiveComment_returnsFullXp() {
            User prAuthor = createUser(10L, "pr-author");
            PullRequest pullRequest = createPullRequest(prAuthor);

            double xp = calculator.calculateStandaloneReviewCommentXp(pullRequest, 99L, 100);

            assertThat(xp).isEqualTo(0.5);
        }

        @Test
        @DisplayName("trivial comment (<=50 chars) returns half XP_REVIEW_COMMENT")
        void calculateStandaloneReviewCommentXp_trivialComment_returnsHalfXp() {
            User prAuthor = createUser(10L, "pr-author");
            PullRequest pullRequest = createPullRequest(prAuthor);

            double xp = calculator.calculateStandaloneReviewCommentXp(pullRequest, 99L, 10);

            assertThat(xp).isEqualTo(0.25);
        }

        @Test
        @DisplayName("self-review (comment author == PR author) returns zero XP")
        void calculateStandaloneReviewCommentXp_selfReview_returnsZero() {
            User prAuthor = createUser(10L, "pr-author");
            PullRequest pullRequest = createPullRequest(prAuthor);

            // Comment author ID matches PR author ID
            double xp = calculator.calculateStandaloneReviewCommentXp(pullRequest, 10L, 100);

            assertThat(xp).isZero();
        }

        @Test
        @DisplayName("null commentAuthorId does not match PR author — returns normal XP")
        void calculateStandaloneReviewCommentXp_nullAuthorId_returnsXp() {
            User prAuthor = createUser(10L, "pr-author");
            PullRequest pullRequest = createPullRequest(prAuthor);

            // null commentAuthorId should not match prAuthor.getId()
            double xp = calculator.calculateStandaloneReviewCommentXp(pullRequest, null, 100);

            assertThat(xp).isEqualTo(0.5);
        }

        @Test
        @DisplayName("boundary: exactly 50 chars returns half XP (> 50 threshold)")
        void calculateStandaloneReviewCommentXp_boundary50Chars_returnsHalfXp() {
            User prAuthor = createUser(10L, "pr-author");
            PullRequest pullRequest = createPullRequest(prAuthor);

            // Exactly 50 chars: NOT > 50, so should get half XP
            double xp50 = calculator.calculateStandaloneReviewCommentXp(pullRequest, 99L, 50);
            assertThat(xp50).as("50 chars (boundary) should return half XP").isEqualTo(0.25);

            // 51 chars: IS > 50, should get full XP
            double xp51 = calculator.calculateStandaloneReviewCommentXp(pullRequest, 99L, 51);
            assertThat(xp51).as("51 chars should return full XP").isEqualTo(0.5);
        }

        @Test
        @DisplayName("bot-authored PR still awards XP to human commenter (no blanket bot exclusion)")
        void calculateStandaloneReviewCommentXp_botAuthoredPr_humanGetsXp() {
            // PR authored by a configured bot login — human commenter should STILL get XP
            User botAuthor = createUser(50L, "copilot[bot]");
            PullRequest pullRequest = createPullRequest(botAuthor);

            // Human commenter (different ID from bot author) — should earn XP
            double xp = calculator.calculateStandaloneReviewCommentXp(pullRequest, 99L, 100);

            assertThat(xp).as("Human reviewing bot PR should still earn XP").isEqualTo(0.5);
        }

        @Test
        @DisplayName("null PR author allows XP (defensive null safety)")
        void calculateStandaloneReviewCommentXp_nullPrAuthor_returnsXp() {
            PullRequest pullRequest = createPullRequest(null);

            double xp = calculator.calculateStandaloneReviewCommentXp(pullRequest, 99L, 100);

            assertThat(xp).as("Null PR author should not block XP").isEqualTo(0.5);
        }

        @Test
        @DisplayName("issue comments on own pull request return zero XP")
        void calculateIssueCommentExperiencePoints_ownPullRequestComment_returnsZero() {
            User prAuthor = createUser(10L, "pr-author");
            PullRequest pullRequest = createPullRequest(prAuthor);

            IssueComment issueComment = new IssueComment();
            issueComment.setId(123L);
            issueComment.setIssue(pullRequest);
            issueComment.setAuthor(prAuthor);
            issueComment.setBody("Author reply to tutor feedback");
            issueComment.setHtmlUrl("https://github.com/test/test-repo/pull/1#issuecomment-123");

            double xp = calculator.calculateIssueCommentExperiencePoints(issueComment);

            assertThat(xp).isZero();
        }
    }

    @Nested
    @DisplayName("XP Constants")
    class ExperiencePointConstants {

        @Test
        @DisplayName("XP_PULL_REQUEST_OPENED is disabled by default (0.0)")
        void pullRequestOpenedConstant() {
            // PR XP will be introduced in a future release
            assertThat(ExperiencePointCalculator.XP_PULL_REQUEST_OPENED).isEqualTo(0.0);
        }

        @Test
        @DisplayName("XP_PULL_REQUEST_MERGED is disabled by default (0.0)")
        void pullRequestMergedConstant() {
            // PR XP will be introduced in a future release
            assertThat(ExperiencePointCalculator.XP_PULL_REQUEST_MERGED).isEqualTo(0.0);
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
