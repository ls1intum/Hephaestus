package de.tum.in.www1.hephaestus.leaderboard;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("ScoringService")
@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    @Mock
    private PullRequestRepository pullRequestRepository;

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        LeaderboardProperties properties = new LeaderboardProperties();
        properties.setSelfReviewAuthorLogins(List.of("Copilot", "dependabot[bot]"));
        scoringService = new ScoringService(pullRequestRepository, properties);
    }

    @Nested
    @DisplayName("Self-Review Exclusion")
    class SelfReviewExclusion {

        @Test
        @DisplayName("excludes reviews when PR author is configured bot and reviewer is assignee")
        void excludesReviewWhenAuthorIsBotAndReviewerIsAssignee() {
            User copilot = createUser(1L, "Copilot");
            User reviewer = createUser(2L, "reviewer");

            PullRequest pullRequest = createPullRequest(copilot);
            pullRequest.getAssignees().add(reviewer);

            PullRequestReview review = createApprovedReview(reviewer, pullRequest);

            double score = scoringService.calculateReviewScore(List.of(review));

            assertThat(score).isZero();
        }

        @Test
        @DisplayName("excludes reviews with case-insensitive author login matching")
        void matchesAuthorLoginCaseInsensitively() {
            User copilot = createUser(1L, "COPILOT"); // Uppercase
            User reviewer = createUser(2L, "reviewer");

            PullRequest pullRequest = createPullRequest(copilot);
            pullRequest.getAssignees().add(reviewer);

            PullRequestReview review = createApprovedReview(reviewer, pullRequest);

            double score = scoringService.calculateReviewScore(List.of(review));

            assertThat(score).isZero();
        }

        @Test
        @DisplayName("counts reviews when PR author is not in exclusion list")
        void countsReviewWhenAuthorNotExcluded() {
            User author = createUser(1L, "regular-author");
            User reviewer = createUser(2L, "reviewer");

            PullRequest pullRequest = createPullRequest(author);

            PullRequestReview review = createApprovedReview(reviewer, pullRequest);

            double score = scoringService.calculateReviewScore(List.of(review));

            assertThat(score).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("counts reviews when reviewer is not an assignee on bot-authored PR")
        void countsReviewWhenReviewerNotAssignee() {
            User copilot = createUser(1L, "Copilot");
            User reviewer = createUser(2L, "reviewer");
            User differentAssignee = createUser(3L, "someone-else");

            PullRequest pullRequest = createPullRequest(copilot);
            pullRequest.getAssignees().add(differentAssignee); // Reviewer is NOT assignee

            PullRequestReview review = createApprovedReview(reviewer, pullRequest);

            double score = scoringService.calculateReviewScore(List.of(review));

            assertThat(score).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("counts reviews when bot-authored PR has no assignees")
        void countsReviewWhenNoAssignees() {
            User copilot = createUser(1L, "Copilot");
            User reviewer = createUser(2L, "reviewer");

            PullRequest pullRequest = createPullRequest(copilot);
            // No assignees added

            PullRequestReview review = createApprovedReview(reviewer, pullRequest);

            double score = scoringService.calculateReviewScore(List.of(review));

            assertThat(score).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("matches assignee by ID when login differs")
        void matchesAssigneeById() {
            User copilot = createUser(1L, "Copilot");
            User reviewer = createUser(2L, "reviewer");
            User assigneeWithSameId = createUser(2L, "different-login");

            PullRequest pullRequest = createPullRequest(copilot);
            pullRequest.getAssignees().add(assigneeWithSameId);

            PullRequestReview review = createApprovedReview(reviewer, pullRequest);

            double score = scoringService.calculateReviewScore(List.of(review));

            assertThat(score).isZero();
        }

        @Test
        @DisplayName("excludes reviews for multiple configured bots")
        void excludesForMultipleBots() {
            User dependabot = createUser(1L, "dependabot[bot]");
            User reviewer = createUser(2L, "reviewer");

            PullRequest pullRequest = createPullRequest(dependabot);
            pullRequest.getAssignees().add(reviewer);

            PullRequestReview review = createApprovedReview(reviewer, pullRequest);

            double score = scoringService.calculateReviewScore(List.of(review));

            assertThat(score).isZero();
        }

        @Test
        @DisplayName("handles mixed scenarios: filters self-assigned, counts non-assigned")
        void handlesMixedScenarios() {
            User copilot = createUser(1L, "Copilot");
            User assignedReviewer = createUser(2L, "assigned-reviewer");
            User externalReviewer = createUser(3L, "external-reviewer");

            PullRequest pullRequest = createPullRequest(copilot);
            pullRequest.getAssignees().add(assignedReviewer); // Only assignedReviewer is excluded

            PullRequestReview excludedReview = createApprovedReview(assignedReviewer, pullRequest);
            PullRequestReview countedReview = createApprovedReview(externalReviewer, pullRequest);

            double score = scoringService.calculateReviewScore(List.of(excludedReview, countedReview));

            // Only externalReviewer's review should count
            assertThat(score).isGreaterThan(0.0);
        }
    }

    @Nested
    @DisplayName("Empty Exclusion List")
    class EmptyExclusionList {

        @BeforeEach
        void setUp() {
            LeaderboardProperties properties = new LeaderboardProperties();
            // Empty list - no exclusions
            scoringService = new ScoringService(pullRequestRepository, properties);
        }

        @Test
        @DisplayName("counts all reviews when exclusion list is empty")
        void countsAllReviewsWhenNoExclusions() {
            User copilot = createUser(1L, "Copilot");
            User reviewer = createUser(2L, "reviewer");

            PullRequest pullRequest = createPullRequest(copilot);
            pullRequest.getAssignees().add(reviewer);

            PullRequestReview review = createApprovedReview(reviewer, pullRequest);

            double score = scoringService.calculateReviewScore(List.of(review));

            assertThat(score).isGreaterThan(0.0);
        }
    }

    // Helper methods for test readability
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
