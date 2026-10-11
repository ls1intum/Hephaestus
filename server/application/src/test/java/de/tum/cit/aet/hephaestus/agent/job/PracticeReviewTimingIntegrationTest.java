package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PracticeReviewTimingIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String FROM = "2026-03-10T00:00:00Z";
    private static final String TO = "2026-03-13T00:00:00Z";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private Workspace workspace;
    private Workspace otherWorkspace;
    private User developer;
    private Repository repository;
    private int nextPosition;

    @BeforeEach
    void setUp() {
        workspace = createWorkspace(
                "review-timing", "Review timing", "review-timing-org", AccountType.ORG, persistUser("timing-owner"));
        ensureAdminMembership(workspace);
        ensureWorkspaceMembership(workspace, persistUser("testuser"), WorkspaceMembership.WorkspaceRole.MEMBER);
        developer = persistUser("timing-developer");
        otherWorkspace = createWorkspace(
                "other-review-timing",
                "Other review timing",
                "other-review-timing-org",
                AccountType.ORG,
                persistUser("other-timing-owner"));

        Repository created = new Repository();
        created.setNativeId(5101L);
        created.setProvider(ensureGitHubProvider());
        created.setName("timing-repo");
        created.setNameWithOwner("review-timing-org/timing-repo");
        created.setHtmlUrl("https://github.com/review-timing-org/timing-repo");
        created.setDefaultBranch("main");
        repository = repositoryRepository.save(created);
    }

    @Test
    @WithAdminUser
    void shouldMeasureEachStageAndCountWhatNeedsALookWhenSeededAroundTheRange() {
        long mergedEarly = pullRequest(1, "2026-03-10T10:20:00Z");
        long mergedThenReviewed = pullRequest(2, "2026-03-10T10:59:00Z");
        long open = pullRequest(3, null);

        // Finished before the merge: 120 s waiting, 60 s preparing, 300 s with the model, 60 s until delivered.
        AgentJob onTime = review(workspace, mergedEarly, "2026-03-10T10:00:00Z", AgentJobStatus.COMPLETED);
        times(onTime, "2026-03-10T10:02:00Z", "2026-03-10T10:03:00Z", "2026-03-10T10:08:00Z");
        feedback(workspace, onTime, "2026-03-10T10:08:30Z", "2026-03-10T10:09:00Z", null);
        feedback(workspace, onTime, "2026-03-10T10:08:30Z", "2026-03-10T10:30:00Z", null);
        // Started by the merge, so it finished after it: 240 s waiting, 60 s preparing, 600 s with the model.
        AgentJob afterMerge = review(workspace, mergedThenReviewed, "2026-03-10T11:00:00Z", AgentJobStatus.COMPLETED);
        times(afterMerge, "2026-03-10T11:04:00Z", "2026-03-10T11:05:00Z", "2026-03-10T11:15:00Z");
        signal(workspace, mergedThenReviewed, ScmSignals.PULL_REQUEST_MERGED.value(), afterMerge);
        feedback(workspace, afterMerge, "2026-03-10T11:16:00Z", null, FeedbackSuppressionReason.ARTIFACT_MERGED);
        // Its developer opened the practice page card a day later, and was shown it in a conversation after that.
        feedback(workspace, onTime, FeedbackChannel.IN_APP, "2026-03-10T10:08:30Z", "2026-03-11T10:08:00Z", null);
        feedback(workspace, onTime, FeedbackChannel.IN_CHAT, "2026-03-10T10:08:30Z", "2026-03-12T10:08:00Z", null);
        // Started before the merge and finished after it, without reaching the model: 0 s waiting.
        AgentJob late = review(workspace, mergedEarly, "2026-03-10T10:15:00Z", AgentJobStatus.COMPLETED);
        times(late, "2026-03-10T10:15:00Z", null, "2026-03-10T10:25:00Z");
        signal(workspace, mergedEarly, ScmSignals.PULL_REQUEST_OPENED.value(), late);
        // Posted on the work only after an admin approved it: a person's wait, so it is not timed as posted.
        Feedback waitedForApproval = feedback(
                workspace, late, FeedbackChannel.IN_CONTEXT, "2026-03-10T10:25:00Z", "2026-03-10T12:00:00Z", null);
        approved(workspace, waitedForApproval, "2026-03-10T11:59:00Z");
        // An open pull request, never finished after a merge.
        review(workspace, open, "2026-03-11T00:00:00Z", AgentJobStatus.RUNNING);
        // Queued since March; queued for budget; queued just now: one of each.
        review(workspace, open, "2026-03-12T00:00:00Z", AgentJobStatus.QUEUED);
        AgentJob held = review(workspace, open, "2026-03-12T01:00:00Z", AgentJobStatus.QUEUED);
        held.setHoldReason(AgentJob.HOLD_REASON_BUDGET);
        jobRepository.save(held);
        review(workspace, open, Instant.now().toString(), AgentJobStatus.QUEUED);
        // Requested in the range but requeued a minute ago: it has waited a minute, not since March.
        AgentJob requeued = review(workspace, open, "2026-03-12T02:00:00Z", AgentJobStatus.QUEUED);
        requeued.setAvailableAt(Instant.now().minusSeconds(60));
        jobRepository.save(requeued);
        // Cancelled after the merge: it never could post, so it did not come late.
        AgentJob cancelled = review(workspace, mergedEarly, "2026-03-10T10:16:00Z", AgentJobStatus.CANCELLED);
        cancelled.setCompletedAt(Instant.parse("2026-03-10T10:40:00Z"));
        jobRepository.save(cancelled);

        // Outside the range, another purpose, another workspace: none of them is measured or counted as late.
        AgentJob before = review(workspace, mergedEarly, "2026-03-09T10:00:00Z", AgentJobStatus.COMPLETED);
        times(before, "2026-03-09T12:00:00Z", "2026-03-09T13:00:00Z", "2026-03-11T00:00:00Z");
        AgentJob mentor = review(workspace, mergedEarly, "2026-03-10T10:00:00Z", AgentJobStatus.COMPLETED);
        times(mentor, "2026-03-10T12:00:00Z", "2026-03-10T13:00:00Z", "2026-03-10T14:00:00Z");
        mentor.setPurpose(AgentPurpose.MENTOR);
        jobRepository.save(mentor);
        AgentJob elsewhere = review(otherWorkspace, mergedEarly, "2026-03-10T10:00:00Z", AgentJobStatus.COMPLETED);
        times(elsewhere, "2026-03-10T12:00:00Z", "2026-03-10T13:00:00Z", "2026-03-10T14:00:00Z");
        signal(otherWorkspace, mergedEarly, ScmSignals.PULL_REQUEST_MERGED.value(), elsewhere);
        review(otherWorkspace, open, "2026-03-12T00:00:00Z", AgentJobStatus.QUEUED);
        feedback(otherWorkspace, elsewhere, "2026-03-10T14:00:00Z", null, FeedbackSuppressionReason.ARTIFACT_MERGED);
        feedback(workspace, before, "2026-03-09T14:00:00Z", null, FeedbackSuppressionReason.ARTIFACT_MERGED);

        PracticeReviewTimingDTO timing = timing();

        assertThat(timing.from()).isEqualTo(Instant.parse(FROM));
        assertThat(timing.to()).isEqualTo(Instant.parse(TO));
        assertThat(timing.asked())
                .as("every moment here was recorded after its request, so none counts as waiting to be asked")
                .isEqualTo(new ReviewStageTimingDTO(0L, null, null));
        assertThat(timing.queue()).isEqualTo(new ReviewStageTimingDTO(3L, 120L, 216L));
        assertThat(timing.preparation()).isEqualTo(new ReviewStageTimingDTO(2L, 60L, 60L));
        assertThat(timing.model()).isEqualTo(new ReviewStageTimingDTO(2L, 450L, 570L));
        assertThat(timing.posted())
                .as("the first comment the review posted by itself counts; the approved one waited for a person")
                .isEqualTo(new ReviewStageTimingDTO(1L, 60L, 60L));
        assertThat(timing.total())
                .as("from the request to the first comment posted by itself")
                .isEqualTo(new ReviewStageTimingDTO(1L, 540L, 540L));
        assertThat(timing.totalShares())
                .as("each stage's part of the very reviews the total measures")
                .isEqualTo(new ReviewTimeSharesDTO(0.0, 120 / 540.0, 60 / 540.0, 300 / 540.0, 0.0, 60 / 540.0));
        assertThat(timing.finished())
                .as("one figure per completed review: 480 s, 900 s, and 600 s for the one without an execution start")
                .isEqualTo(new ReviewStageTimingDTO(3L, 600L, 840L));
        assertThat(timing.finishedShares())
                .as("the review without an execution start counts its whole run apart, unsplit")
                .isEqualTo(new ReviewTimeSharesDTO(0.0, 360 / 1980.0, 120 / 1980.0, 900 / 1980.0, 600 / 1980.0, null));
        assertThat(timing.read())
                .as("the first read counts, on the practice page or in a conversation")
                .isEqualTo(new ReviewStageTimingDTO(1L, 86_400L, 86_400L));
        assertThat(timing.waitingLong()).isEqualTo(1L);
        assertThat(timing.finishedAfterMerge()).isEqualTo(2L);
        assertThat(timing.finishedAfterMergeStartedByMerge()).isEqualTo(1L);
        assertThat(timing.feedbackWithheldAsMerged()).isEqualTo(1L);

        Map<String, JsonNode> rows = rows();
        assertThat(row(rows, afterMerge).path("finishedAfterMerge").asBoolean()).isTrue();
        assertThat(row(rows, afterMerge)
                        .path("finishedAfterMergeStartedByMerge")
                        .asBoolean())
                .isTrue();
        assertThat(row(rows, late).path("finishedAfterMerge").asBoolean()).isTrue();
        assertThat(row(rows, late).path("finishedAfterMergeStartedByMerge").asBoolean())
                .as("opened before the merge, so the merge did not start it")
                .isFalse();
        assertThat(row(rows, onTime).path("finishedAfterMerge").asBoolean()).isFalse();
        assertThat(row(rows, cancelled).path("finishedAfterMerge").asBoolean())
                .as("a cancelled review never finished late")
                .isFalse();
        assertThat(row(rows, held).path("holdReason").asString()).isEqualTo(AgentJob.HOLD_REASON_BUDGET);
        assertThat(row(rows, onTime).has("holdReason")).isFalse();
        assertThat(row(rows, onTime).path("reviewsOnWork").asInt())
                .as("on the early-merged pull request: onTime, late and cancelled in the range, and the one before")
                .isEqualTo(4);

        assertThat(reviewIds("&finishedAfterMerge=true"))
                .as("the review list returns the reviews the count counts")
                .containsExactlyInAnyOrder(
                        afterMerge.getId().toString(), late.getId().toString());
        // The list filter (JPQL), the row flag and the count (native) state "finished after the merge" apart.
        assertThat(rows.entrySet().stream()
                        .filter(entry ->
                                entry.getValue().path("finishedAfterMerge").asBoolean())
                        .map(Map.Entry::getKey)
                        .toList())
                .as("the rows flag exactly the reviews the filter returns and the count counts")
                .containsExactlyInAnyOrderElementsOf(reviewIds("&finishedAfterMerge=true"))
                .hasSize(Math.toIntExact(timing.finishedAfterMerge()));

        webTestClient
                .get()
                .uri("/workspaces/{slug}/agents/jobs/{id}", workspace.getWorkspaceSlug(), afterMerge.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.executionStartedAt")
                .isEqualTo("2026-03-10T11:05:00Z")
                .jsonPath("$.workMergedAt")
                .isEqualTo("2026-03-10T10:59:00Z")
                .jsonPath("$.firstFeedbackPostedAt")
                .doesNotExist()
                .jsonPath("$.firstFeedbackReadAt")
                .doesNotExist();
        webTestClient
                .get()
                .uri("/workspaces/{slug}/agents/jobs/{id}", workspace.getWorkspaceSlug(), onTime.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.firstFeedbackPostedAt")
                .isEqualTo("2026-03-10T10:09:00Z")
                .jsonPath("$.firstFeedbackReadAt")
                .isEqualTo("2026-03-11T10:08:00Z");
    }

    /**
     * The overview splits a review's time into Until asked, Queue, Run and To developer, as a review's trace does.
     * Until asked runs from the first moment on the work that names the review to its request; the whole review's
     * time starts there, so its parts add up to it. A review no moment started before its request waited nothing.
     */
    @Test
    @WithAdminUser
    void shouldMeasureTheTimeUntilAReviewWasAskedForFromTheMomentThatStartedIt() {
        long pullRequest = pullRequest(4, null);
        // Opened at 09:58, asked for at 10:00: 120 s until asked, 60 s waiting, 60 s preparing, 480 s with the
        // model, 60 s until posted.
        AgentJob started = review(workspace, pullRequest, "2026-03-10T10:00:00Z", AgentJobStatus.COMPLETED);
        times(started, "2026-03-10T10:01:00Z", "2026-03-10T10:02:00Z", "2026-03-10T10:10:00Z");
        signal(workspace, pullRequest, ScmSignals.PULL_REQUEST_OPENED.value(), started, "2026-03-10T09:58:00Z");
        // A later moment folded into the same review does not move its start.
        signal(workspace, pullRequest, ScmSignals.PULL_REQUEST_SYNCHRONIZED.value(), started, "2026-03-10T09:59:00Z");
        feedback(workspace, started, "2026-03-10T10:10:30Z", "2026-03-10T10:11:00Z", null);
        // Asked for by hand: its only moment came after the request, so it waited nothing to be asked for.
        AgentJob byHand = review(workspace, pullRequest, "2026-03-10T11:00:00Z", AgentJobStatus.COMPLETED);
        times(byHand, "2026-03-10T11:00:00Z", "2026-03-10T11:01:00Z", "2026-03-10T11:05:00Z");
        signal(workspace, pullRequest, ScmSignals.PULL_REQUEST_SYNCHRONIZED.value(), byHand, "2026-03-10T11:30:00Z");

        PracticeReviewTimingDTO timing = timing();

        assertThat(timing.asked()).isEqualTo(new ReviewStageTimingDTO(1L, 120L, 120L));
        assertThat(timing.total())
                .as("from the moment that started it to its first comment: 120 + 60 + 60 + 480 + 60 s")
                .isEqualTo(new ReviewStageTimingDTO(1L, 780L, 780L));
        assertThat(timing.totalShares())
                .isEqualTo(new ReviewTimeSharesDTO(120 / 780.0, 60 / 780.0, 60 / 780.0, 480 / 780.0, 0.0, 60 / 780.0));
        assertThat(timing.finished())
                .as("720 s from the moment for the first, 300 s from the request for the one asked for by hand")
                .isEqualTo(new ReviewStageTimingDTO(2L, 510L, 678L));
        assertThat(timing.finishedShares())
                .isEqualTo(new ReviewTimeSharesDTO(120 / 1020.0, 60 / 1020.0, 120 / 1020.0, 720 / 1020.0, 0.0, null));
    }

    /**
     * A review recorded before the execution start was, or one that ended without reaching the model, has no
     * execution start, so its run cannot be split into preparation and model. It stays in the whole reviews, with its
     * whole run as an unsplit part, so the parts still add up to the reviews' time.
     */
    @Test
    @WithAdminUser
    void shouldCountTheRunOfAReviewWithoutAnExecutionStartAsUnsplit() {
        long pullRequest = pullRequest(8, null);
        // 60 s waiting, 60 s preparing, 240 s with the model, 60 s until posted: 420 s.
        AgentJob split = review(workspace, pullRequest, "2026-03-10T10:00:00Z", AgentJobStatus.COMPLETED);
        times(split, "2026-03-10T10:01:00Z", "2026-03-10T10:02:00Z", "2026-03-10T10:06:00Z");
        feedback(workspace, split, "2026-03-10T10:06:30Z", "2026-03-10T10:07:00Z", null);
        // 120 s waiting, a run of 480 s that cannot be split, 60 s until posted: 660 s.
        AgentJob unsplit = review(workspace, pullRequest, "2026-03-10T11:00:00Z", AgentJobStatus.COMPLETED);
        times(unsplit, "2026-03-10T11:02:00Z", null, "2026-03-10T11:10:00Z");
        feedback(workspace, unsplit, "2026-03-10T11:10:30Z", "2026-03-10T11:11:00Z", null);

        PracticeReviewTimingDTO timing = timing();

        assertThat(timing.preparation())
                .as("only the review with an execution start is split")
                .isEqualTo(new ReviewStageTimingDTO(1L, 60L, 60L));
        assertThat(timing.model()).isEqualTo(new ReviewStageTimingDTO(1L, 240L, 240L));
        assertThat(timing.total())
                .as("both reviews are whole reviews: 420 s and 660 s")
                .isEqualTo(new ReviewStageTimingDTO(2L, 540L, 636L));
        assertThat(timing.totalShares())
                .isEqualTo(new ReviewTimeSharesDTO(
                        0.0, 180 / 1080.0, 60 / 1080.0, 240 / 1080.0, 480 / 1080.0, 120 / 1080.0));
        assertThat(timing.finished()).isEqualTo(new ReviewStageTimingDTO(2L, 480L, 576L));
        ReviewTimeSharesDTO finished = Objects.requireNonNull(timing.finishedShares());
        assertThat(finished)
                .isEqualTo(new ReviewTimeSharesDTO(0.0, 180 / 960.0, 60 / 960.0, 240 / 960.0, 480 / 960.0, null));
        assertThat(finished.asked()
                        + finished.queue()
                        + finished.preparation()
                        + finished.model()
                        + finished.runUnsplit())
                .as("the parts still add up to the whole")
                .isCloseTo(1.0, within(1e-9));
    }

    /**
     * A backfill's signal carries the work's last update, weeks before an admin started the backfill, so it is not a
     * moment that asked for the review: the review waited nothing to be asked for.
     */
    @Test
    @WithAdminUser
    void shouldNotCountTheWorksLastUpdateAsTimeUntilABackfillWasAskedFor() {
        long pullRequest = pullRequest(6, null);
        AgentJob backfilled = review(workspace, pullRequest, "2026-03-10T10:00:00Z", AgentJobStatus.COMPLETED);
        times(backfilled, "2026-03-10T10:01:00Z", "2026-03-10T10:02:00Z", "2026-03-10T10:10:00Z");
        signal(
                workspace,
                pullRequest,
                ScmSignals.PULL_REQUEST_OPENED.value(),
                backfilled,
                "2026-03-01T12:00:00Z",
                "BACKFILL");

        PracticeReviewTimingDTO timing = timing();

        assertThat(timing.asked().count()).isZero();
        assertThat(timing.finished()).isEqualTo(new ReviewStageTimingDTO(1L, 600L, 600L));
    }

    /**
     * A review someone asked for records its signal just before the request, so that signal is not a moment that
     * started the review: the review waited nothing to be asked for.
     */
    @Test
    @WithAdminUser
    void shouldMeasureNoTimeUntilAskedWhenSomeoneRequestedTheReview() {
        long pullRequest = pullRequest(7, null);
        AgentJob requested = review(workspace, pullRequest, "2026-03-10T10:00:00Z", AgentJobStatus.COMPLETED);
        times(requested, "2026-03-10T10:01:00Z", "2026-03-10T10:02:00Z", "2026-03-10T10:10:00Z");
        signal(
                workspace,
                pullRequest,
                ScmSignals.PULL_REQUEST_SYNCHRONIZED.value(),
                requested,
                "2026-03-10T09:59:59.950Z",
                "MANUAL");

        PracticeReviewTimingDTO timing = timing();

        assertThat(timing.asked().count()).isZero();
        assertThat(timing.finished()).isEqualTo(new ReviewStageTimingDTO(1L, 600L, 600L));
    }

    /**
     * "All time" on the overview has no lower bound: with {@code allTime}, the overview and the review time start at
     * the workspace's first review, whatever {@code from} says, even when that is more than 400 days back, and the
     * overview is split into months. Another workspace's older review does not move the start.
     */
    @Test
    @WithAdminUser
    void shouldCountAndMeasureAllTimeFromTheWorkspacesFirstReview() {
        long pullRequest = pullRequest(5, null);
        AgentJob first = review(workspace, pullRequest, "2024-01-15T09:00:00Z", AgentJobStatus.COMPLETED);
        times(first, "2024-01-15T09:01:00Z", "2024-01-15T09:02:00Z", "2024-01-15T09:10:00Z");
        AgentJob latest = review(workspace, pullRequest, "2026-03-10T10:00:00Z", AgentJobStatus.COMPLETED);
        times(latest, "2026-03-10T10:01:00Z", "2026-03-10T10:02:00Z", "2026-03-10T10:06:00Z");
        review(otherWorkspace, pullRequest, "2023-05-01T00:00:00Z", AgentJobStatus.COMPLETED);

        webTestClient
                .get()
                .uri(
                        "/workspaces/{slug}/practices/reviews/overview?allTime=true&from={from}&to={to}&zone=UTC",
                        workspace.getWorkspaceSlug(),
                        FROM,
                        TO)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.from")
                .isEqualTo("2024-01-15T09:00:00Z")
                .jsonPath("$.to")
                .isEqualTo(TO)
                .jsonPath("$.bucket")
                .isEqualTo("MONTH")
                .jsonPath("$.buckets[0].start")
                .isEqualTo("2024-01-01T00:00:00Z")
                .jsonPath("$.reviews.completed")
                .isEqualTo(2);

        PracticeReviewTimingDTO timing = Objects.requireNonNull(webTestClient
                .get()
                .uri(
                        "/workspaces/{slug}/practices/reviews/timing?allTime=true&to={to}",
                        workspace.getWorkspaceSlug(),
                        TO)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeReviewTimingDTO.class)
                .returnResult()
                .getResponseBody());
        assertThat(timing.from()).isEqualTo(Instant.parse("2024-01-15T09:00:00Z"));
        assertThat(timing.finished())
                .as("both reviews, 600 s and 360 s from request to end")
                .isEqualTo(new ReviewStageTimingDTO(2L, 480L, 576L));

        // A to in the far future ends at now, so the history, not the request, bounds the buckets.
        JsonNode farFuture = Objects.requireNonNull(webTestClient
                .get()
                .uri(
                        "/workspaces/{slug}/practices/reviews/overview?allTime=true&to={to}&zone=UTC",
                        workspace.getWorkspaceSlug(),
                        "9999-12-31T00:00:00Z")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody());
        assertThat(Instant.parse(farFuture.path("to").asString())).isBeforeOrEqualTo(Instant.now());

        // Without allTime, the same span is still refused as too wide.
        webTestClient
                .get()
                .uri(
                        "/workspaces/{slug}/practices/reviews/timing?from={from}&to={to}",
                        workspace.getWorkspaceSlug(),
                        "2024-01-15T09:00:00Z",
                        TO)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    /** A workspace without a review yet has an empty all-time range, not an error. */
    @Test
    @WithAdminUser
    void shouldAnswerAnEmptyAllTimeWhenTheWorkspaceHasNoReview() {
        webTestClient
                .get()
                .uri(
                        "/workspaces/{slug}/practices/reviews/overview?allTime=true&to={to}",
                        workspace.getWorkspaceSlug(),
                        TO)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.from")
                .isEqualTo(TO)
                .jsonPath("$.buckets.length()")
                .isEqualTo(0);
    }

    @Test
    @WithAdminUser
    void shouldMeasureNothingWhenTheRangeHasNoReviews() {
        PracticeReviewTimingDTO timing = timing();

        assertThat(timing.queue()).isEqualTo(new ReviewStageTimingDTO(0L, null, null));
        assertThat(timing.posted()).isEqualTo(new ReviewStageTimingDTO(0L, null, null));
        assertThat(timing.read()).isEqualTo(new ReviewStageTimingDTO(0L, null, null));
        assertThat(timing.total()).isEqualTo(new ReviewStageTimingDTO(0L, null, null));
        assertThat(timing.totalShares()).isNull();
        assertThat(timing.finished()).isEqualTo(new ReviewStageTimingDTO(0L, null, null));
        assertThat(timing.finishedShares()).isNull();
        assertThat(timing.finishedAfterMerge()).isZero();
        assertThat(timing.waitingLong()).isZero();
    }

    @Test
    @WithUser
    void shouldRejectTheTimingWhenTheCallerIsAMember() {
        webTestClient
                .get()
                .uri("/workspaces/{slug}/practices/reviews/timing", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
    }

    private PracticeReviewTimingDTO timing() {
        return Objects.requireNonNull(webTestClient
                .get()
                .uri(
                        "/workspaces/{slug}/practices/reviews/timing?from={from}&to={to}",
                        workspace.getWorkspaceSlug(),
                        FROM,
                        TO)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeReviewTimingDTO.class)
                .returnResult()
                .getResponseBody());
    }

    private List<String> reviewIds(String query) {
        var body = webTestClient
                .get()
                .uri(
                        "/workspaces/{slug}/practices/reviews?from={from}&to={to}" + query,
                        workspace.getWorkspaceSlug(),
                        FROM,
                        TO)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        List<String> ids = new ArrayList<>();
        for (JsonNode row : Objects.requireNonNull(body).path("content")) {
            ids.add(row.path("id").asString());
        }
        return ids;
    }

    private static JsonNode row(Map<String, JsonNode> rows, AgentJob job) {
        return Objects.requireNonNull(rows.get(job.getId().toString()));
    }

    /** Every review of the range by id, as the admin review list returns it. */
    private Map<String, JsonNode> rows() {
        var body = webTestClient
                .get()
                .uri(
                        "/workspaces/{slug}/practices/reviews?from={from}&to={to}&size=100",
                        workspace.getWorkspaceSlug(),
                        FROM,
                        TO)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        Map<String, JsonNode> rows = new HashMap<>();
        for (JsonNode row : Objects.requireNonNull(body).path("content")) {
            rows.put(row.path("id").asString(), row);
        }
        return rows;
    }

    private long pullRequest(int number, @Nullable String mergedAt) {
        Instant now = Instant.parse("2026-03-01T00:00:00Z");
        pullRequestRepository.upsertCore(
                5200L + number,
                Objects.requireNonNull(repository.getProvider().getId()),
                number,
                "Pull request " + number,
                null,
                mergedAt == null ? "OPEN" : "MERGED",
                null,
                "https://github.com/review-timing-org/timing-repo/pull/" + number,
                false,
                mergedAt == null ? null : Instant.parse(mergedAt),
                0,
                now,
                now,
                now,
                developer.getId(),
                repository.getId(),
                null,
                mergedAt == null ? null : Instant.parse(mergedAt),
                false,
                mergedAt != null,
                1,
                1,
                1,
                1,
                null,
                null,
                null,
                "feature/" + number,
                "main",
                "head-" + number,
                "base",
                null,
                null);
        return pullRequestRepository
                .findByRepositoryIdAndNumber(repository.getId(), number)
                .orElseThrow()
                .getId();
    }

    /** {@code createdAt} is only defaulted when unset, so a review can be seeded at a chosen instant. */
    private AgentJob review(Workspace target, long pullRequestId, String createdAt, AgentJobStatus status) {
        AgentJob job = new AgentJob();
        job.setWorkspace(target);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setMetadata(objectMapper.valueToTree(Map.of("pull_request_id", pullRequestId)));
        job.setConfigSnapshot(objectMapper.valueToTree(Map.of("model", "test")));
        job.setCreatedAt(Instant.parse(createdAt));
        job.setStatus(status);
        return jobRepository.save(job);
    }

    private void times(AgentJob job, String startedAt, @Nullable String executionStartedAt, String completedAt) {
        job.setStartedAt(Instant.parse(startedAt));
        job.setExecutionStartedAt(executionStartedAt == null ? null : Instant.parse(executionStartedAt));
        job.setCompletedAt(Instant.parse(completedAt));
        jobRepository.save(job);
    }

    /** An admin approved this feedback before it was posted, so its wait was a person's. */
    private void approved(Workspace target, Feedback feedback, String decidedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO feedback_approval (id, feedback_id, workspace_id, decision, content_digest, decided_at)
                VALUES (?, ?, ?, 'APPROVED', ?, ?)
                """,
                UUID.randomUUID(),
                feedback.getId(),
                target.getId(),
                "a".repeat(64),
                Timestamp.from(Instant.parse(decidedAt)));
    }

    private void signal(Workspace target, long pullRequestId, String signalName, AgentJob job) {
        signal(target, pullRequestId, signalName, job, null);
    }

    /** A moment on the pull request that names the job, at {@code occurredAt}, or now when null. */
    private void signal(
            Workspace target, long pullRequestId, String signalName, AgentJob job, @Nullable String occurredAt) {
        signal(target, pullRequestId, signalName, job, occurredAt, "EVENT");
    }

    private void signal(
            Workspace target,
            long pullRequestId,
            String signalName,
            AgentJob job,
            @Nullable String occurredAt,
            String discoveredVia) {
        Timestamp at = Timestamp.from(occurredAt == null ? Instant.now() : Instant.parse(occurredAt));
        jdbcTemplate.update(
                """
                INSERT INTO artifact_signal (id, workspace_id, artifact_kind, artifact_id, signal_name, revision,
                    occurred_at, discovered_via, state, state_changed_at, job_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'TRIGGERED', now(), ?)
                """,
                UUID.randomUUID(),
                target.getId(),
                ArtifactKinds.PULL_REQUEST.value(),
                pullRequestId,
                signalName,
                "rev-" + UUID.randomUUID(),
                at,
                discoveredVia,
                job.getId());
    }

    private void feedback(
            Workspace target,
            AgentJob job,
            String createdAt,
            @Nullable String deliveredAt,
            @Nullable FeedbackSuppressionReason withheld) {
        feedback(target, job, FeedbackChannel.IN_CONTEXT, createdAt, deliveredAt, withheld);
    }

    private Feedback feedback(
            Workspace target,
            AgentJob job,
            FeedbackChannel channel,
            String createdAt,
            @Nullable String deliveredAt,
            @Nullable FeedbackSuppressionReason withheld) {
        return feedbackRepository.save(Feedback.builder()
                .agentJobId(job.getId())
                .workspaceId(target.getId())
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(7L)
                .recipientUserId(developer.getId())
                .aboutUserId(developer.getId())
                .channel(channel)
                .position(nextPosition++)
                .deliveryState(
                        withheld != null
                                ? FeedbackDeliveryState.SUPPRESSED
                                : deliveredAt != null
                                        ? FeedbackDeliveryState.DELIVERED
                                        : FeedbackDeliveryState.PREPARED)
                .suppressionReason(withheld)
                .body("Feedback")
                .source(FeedbackSource.AGENT)
                .createdAt(Instant.parse(createdAt))
                .deliveredAt(deliveredAt == null ? null : Instant.parse(deliveredAt))
                .build());
    }
}
