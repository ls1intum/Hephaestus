package de.tum.in.www1.hephaestus.gitprovider.contribution;

import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ContributionEventSyncServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ContributionEventSyncService contributionEventSyncService;

    @Autowired
    private ContributionEventRepository contributionEventRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    /**
     * Given a new pull request review with an author and submitted time,<br>
     * When processed by processPullRequestReviewContribution,<br>
     * Then a ContributionEvent is persisted with the same actor and occurredAt.<br>
     */
    @Test
    @DisplayName("processPullRequestReviewContribution persists ContributionEvent with correct actor and timestamp")
    void processPullRequestReviewContribution_createsContributionEvent() {
        User author = new User();
        author.setId(9L);
        author.setLogin("mnemosyne");

        Instant submittedAt = Instant.parse("2025-01-01T10:00:00Z");

        PullRequestReview review = new PullRequestReview();
        review.setId(1L);
        review.setAuthor(author);
        review.setSubmittedAt(submittedAt);

        contributionEventSyncService.processPullRequestReviewContribution(review);

        Optional<ContributionEvent> maybe = contributionEventRepository.findBySourceTypeAndSourceId(
            ContributionSourceType.PULL_REQUEST_REVIEW, review.getId());

        assertThat(maybe).isPresent();
        ContributionEvent event = maybe.get();
        assertThat(event.getActor()).isNotNull();
        assertThat(event.getActor().getId()).isEqualTo(author.getId());
        assertThat(event.getActor().getLogin()).isEqualTo(author.getLogin());
        assertThat(event.getOccurredAt()).isEqualTo(submittedAt);
    }

    /**
     * Given an existing ContributionEvent and a pull request review with the same source id,<br>
     * When processed by processPullRequestReviewContribution,<br>
     * Then the existing ContributionEvent is updated with the new occurredAt timestamp.<br>
     */
    @Test
    @DisplayName("processPullRequestReviewContribution updates existing ContributionEvent with new timestamp")
    void processPullRequestReviewContribution_updatesExistingContributionEvent() {
        User author = new User();
        author.setId(9L);
        author.setLogin("mnemosyne");

        long reviewId = 2L;
        Instant submittedAt = Instant.parse("2025-01-02T10:00:00Z");

        PullRequestReview review = new PullRequestReview();
        review.setId(reviewId);
        review.setAuthor(author);
        review.setSubmittedAt(submittedAt);

        Instant occurredAt = submittedAt.minus(Duration.ofDays(1));
        ContributionEvent existing = new ContributionEvent();
        existing.setSourceType(ContributionSourceType.PULL_REQUEST_REVIEW);
        existing.setSourceId(reviewId);
        existing.setActor(author);
        existing.setOccurredAt(occurredAt);
        contributionEventRepository.save(existing);

        contributionEventSyncService.processPullRequestReviewContribution(review);

        Optional<ContributionEvent> maybe = contributionEventRepository.findBySourceTypeAndSourceId(
            ContributionSourceType.PULL_REQUEST_REVIEW, reviewId);

        assertThat(maybe).isPresent();
        ContributionEvent updated = maybe.get();
        assertThat(updated.getOccurredAt()).isEqualTo(review.getSubmittedAt());
    }

//    @Test
//    void processPullRequestReviewContribution_ignoresReviewWithNullAuthor() {
//        PullRequestReview review = new PullRequestReview();
//        review.setId(300L);
//        review.setAuthor(null);
//        review.setSubmittedAt(ZonedDateTime.now());
//
//        long before = contributionEventRepository.count();
//        contributionEventSyncService.processPullRequestReviewContribution(review);
//        long after = contributionEventRepository.count();
//
//        assertThat(after).isEqualTo(before);
//        Optional<ContributionEvent> maybe = contributionEventRepository.findBySourceTypeAndSourceId(
//            ContributionSourceType.PULL_REQUEST_REVIEW, review.getId());
//        assertThat(maybe).isEmpty();
//    }
//
//    @Test
//    void processPullRequestReviewContribution_handlesServiceErrorsWithoutPersisting() {
//        User author = new User();
//        author.setId(9L);
//        author.setLogin("mnemosyne");
//
//        PullRequestReview review = new PullRequestReview();
//        // Intentionally leave id null to simulate malformed input that should not persist an event
//        review.setId(null);
//        review.setAuthor(author);
//        review.setSubmittedAt(ZonedDateTime.now());
//
//        long before = contributionEventRepository.count();
//        // Should not throw, should not persist a new event
//        contributionEventSyncService.processPullRequestReviewContribution(review);
//        long after = contributionEventRepository.count();
//
//        assertThat(after).isEqualTo(before);
//    }
}
