package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

class PracticeReviewQueueIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReviewJobRepository jobRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Workspace workspace;
    private Workspace otherWorkspace;
    private User developer;
    private Instant now;
    private int nextNumber = 1;

    @BeforeEach
    void setUp() {
        workspace = createWorkspace(
                "review-queue", "Review queue", "review-queue-org", AccountType.ORG, persistUser("queue-owner"));
        ensureAdminMembership(workspace);
        ensureWorkspaceMembership(workspace, persistUser("testuser"), Membership.WorkspaceRole.MEMBER);
        developer = persistUser("queue-developer");
        otherWorkspace = createWorkspace(
                "other-review-queue",
                "Other review queue",
                "other-review-queue-org",
                AccountType.ORG,
                persistUser("other-queue-owner"));
        ensureAdminMembership(otherWorkspace);
        now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    @Test
    @WithAdminUser
    void shouldCountTheQueueAndListItsHeadWhenReviewsRunWaitAndFinish() {
        // Two running on one worker, the longer running one first in the head.
        ReviewJob runningLong = review(workspace, ReviewJobStatus.QUEUED, ago(5));
        running(runningLong, ago(3), "worker-1");
        ReviewJob runningShort = review(workspace, ReviewJobStatus.QUEUED, ago(2));
        running(runningShort, ago(1), "worker-1");
        // Two waiting and one held for budget, the hold oldest of all but last in the head.
        ReviewJob waitingLong = review(workspace, ReviewJobStatus.QUEUED, ago(12));
        ReviewJob waitingShort = review(workspace, ReviewJobStatus.QUEUED, ago(2));
        ReviewJob held = review(workspace, ReviewJobStatus.QUEUED, ago(30));
        held.setHoldReason(ReviewJob.HOLD_REASON_BUDGET);
        // A hold moves the review's availability to the next check of the cap, a time still to come.
        held.setAvailableAt(now.plus(Duration.ofMinutes(30)));
        jobRepository.save(held);
        // Finished: one in the last hour, two more in the last day; cancelled and older ones are not counted.
        finished(workspace, ReviewJobStatus.COMPLETED, ago(30));
        finished(workspace, ReviewJobStatus.FAILED, ago(120));
        finished(workspace, ReviewJobStatus.TIMED_OUT, ago(300));
        finished(workspace, ReviewJobStatus.CANCELLED, ago(10));
        finished(workspace, ReviewJobStatus.COMPLETED, ago(30 * 60));
        // Another purpose and another workspace: none of them is counted or listed.
        ReviewJob mentor = review(workspace, ReviewJobStatus.QUEUED, ago(40));

        jobRepository.save(mentor);
        ReviewJob elsewhereRunning = review(otherWorkspace, ReviewJobStatus.QUEUED, ago(50));
        running(elsewhereRunning, ago(45), "worker-2");
        review(otherWorkspace, ReviewJobStatus.QUEUED, ago(60));
        finished(otherWorkspace, ReviewJobStatus.COMPLETED, ago(5));

        PracticeReviewQueueDTO queue = queue(workspace);

        assertThat(queue.running()).isEqualTo(2L);
        assertThat(queue.activeWorkers())
                .as("distinct workers on the running reviews")
                .isEqualTo(1L);
        assertThat(queue.waiting()).isEqualTo(2L);
        assertThat(queue.oldestWaitingSince())
                .as("the budget hold waits for the cap, not for a worker")
                .isEqualTo(ago(12));
        assertThat(queue.heldForBudget()).isEqualTo(1L);
        assertThat(queue.finishedLastHour()).isEqualTo(1L);
        assertThat(queue.finishedLastDay()).isEqualTo(3L);
        assertThat(queue.finishedPerHourLastDay()).isCloseTo(3 / 24.0, within(1e-9));
        assertThat(queue.head())
                .extracting(PracticeReviewQueueItemDTO::id)
                .containsExactly(
                        runningLong.getId(),
                        runningShort.getId(),
                        waitingLong.getId(),
                        waitingShort.getId(),
                        held.getId());

        PracticeReviewQueueItemDTO first = queue.head().getFirst();
        assertThat(first.status()).isEqualTo(ReviewJobStatus.RUNNING);
        assertThat(first.since()).as("a running review counts from its start").isEqualTo(ago(3));
        assertThat(first.heldForBudget()).isFalse();
        assertThat(first.target().title()).isEqualTo("Pull request 1");
        assertThat(Objects.requireNonNull(first.target().reviewedWork()).label())
                .isEqualTo("#1");
        assertThat(Objects.requireNonNull(first.developer()).login()).isEqualTo("queue-developer");

        PracticeReviewQueueItemDTO waiting = queue.head().get(2);
        assertThat(waiting.status()).isEqualTo(ReviewJobStatus.QUEUED);
        assertThat(waiting.since())
                .as("a queued review counts from its request")
                .isEqualTo(ago(12));
        PracticeReviewQueueItemDTO onHold = queue.head().getLast();
        assertThat(onHold.heldForBudget()).isTrue();
        assertThat(onHold.since())
                .as("a review held for budget counts from its request, not from the next check of the cap")
                .isEqualTo(ago(30));

        PracticeReviewQueueDTO other = queue(otherWorkspace);
        assertThat(other.running()).isEqualTo(1L);
        assertThat(other.waiting()).isEqualTo(1L);
        assertThat(other.finishedLastHour()).isEqualTo(1L);
        assertThat(other.head()).extracting(PracticeReviewQueueItemDTO::id).doesNotContain(runningLong.getId());
    }

    @Test
    @WithAdminUser
    void shouldCountARequeuedReviewAsWaitingFromItsRequeue() {
        ReviewJob fresh = review(workspace, ReviewJobStatus.QUEUED, ago(4));
        // Requested long ago, ran, and was put back in the queue a minute ago after its worker was lost.
        ReviewJob requeued = review(workspace, ReviewJobStatus.QUEUED, ago(40));
        requeued.setAvailableAt(ago(1));
        jobRepository.save(requeued);

        PracticeReviewQueueDTO queue = queue(workspace);

        assertThat(queue.waiting()).isEqualTo(2L);
        assertThat(queue.oldestWaitingSince())
                .as("the requeued review waits from its requeue, not from its request")
                .isEqualTo(ago(4));
        assertThat(queue.head())
                .extracting(PracticeReviewQueueItemDTO::id)
                .containsExactly(fresh.getId(), requeued.getId());
        PracticeReviewQueueItemDTO last = queue.head().getLast();
        assertThat(last.since()).isEqualTo(ago(1));
    }

    @Test
    @WithAdminUser
    void shouldNotCountAReviewBackingOffBeforeARetryAsWaiting() {
        ReviewJob waiting = review(workspace, ReviewJobStatus.QUEUED, ago(4));
        // Its run crashed and it was requeued to retry in five minutes; no worker can take it yet.
        ReviewJob backingOff = review(workspace, ReviewJobStatus.QUEUED, ago(10));
        backingOff.setAvailableAt(now.plus(Duration.ofMinutes(5)));
        jobRepository.save(backingOff);

        PracticeReviewQueueDTO queue = queue(workspace);

        assertThat(queue.waiting()).isEqualTo(1L);
        assertThat(queue.oldestWaitingSince()).isEqualTo(ago(4));
        assertThat(queue.head()).extracting(PracticeReviewQueueItemDTO::id).containsExactly(waiting.getId());
    }

    @Test
    @WithAdminUser
    void shouldReportAnEmptyQueueWhenNothingRunsOrWaits() {
        PracticeReviewQueueDTO queue = queue(workspace);

        assertThat(queue.running()).isZero();
        assertThat(queue.waiting()).isZero();
        assertThat(queue.heldForBudget()).isZero();
        assertThat(queue.activeWorkers()).isZero();
        assertThat(queue.oldestWaitingSince()).isNull();
        assertThat(queue.head()).isEmpty();
        assertThat(queue.finishedLastDay()).isZero();
        assertThat(queue.finishedPerHourLastDay()).isZero();
    }

    @Test
    @WithUser
    void shouldRejectTheQueueWhenTheCallerIsAMember() {
        webTestClient
                .get()
                .uri("/workspaces/{slug}/practices/reviews/queue", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
    }

    private PracticeReviewQueueDTO queue(Workspace target) {
        return Objects.requireNonNull(webTestClient
                .get()
                .uri("/workspaces/{slug}/practices/reviews/queue", target.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeReviewQueueDTO.class)
                .returnResult()
                .getResponseBody());
    }

    private Instant ago(long minutes) {
        return now.minus(Duration.ofMinutes(minutes));
    }

    /** {@code createdAt} is only defaulted when unset, so a review can be seeded at a chosen instant. */
    private ReviewJob review(Workspace target, ReviewJobStatus status, Instant createdAt) {
        int number = nextNumber++;
        ReviewJob job = new ReviewJob();
        RuntimeJobTestData.initialize(job, (target).getId());


        job.setIntegrationKind(IntegrationKind.GITHUB);
        job.setMetadata(objectMapper.valueToTree(Map.of(
                "pull_request_id",
                900_000L + number,
                "pr_number",
                number,
                "title",
                "Pull request " + number,
                "about_user_id",
                developer.getId())));
        job.setConfigSnapshot(objectMapper.valueToTree(Map.of("model", "test")));
        job.setAvailableAt(createdAt);
        job.setStatus(status);
        return jobRepository.save(job);
    }

    private void running(ReviewJob job, Instant startedAt, @Nullable String workerId) {
        job.setStatus(ReviewJobStatus.RUNNING);
        job.setStartedAt(startedAt);
        job.setWorkerId(workerId);
        jobRepository.save(job);
    }

    private void finished(Workspace target, ReviewJobStatus status, Instant completedAt) {
        ReviewJob job = review(target, status, completedAt.minus(Duration.ofMinutes(5)));
        job.setStartedAt(completedAt.minus(Duration.ofMinutes(4)));
        job.setCompletedAt(completedAt);
        jobRepository.save(job);
    }
}
