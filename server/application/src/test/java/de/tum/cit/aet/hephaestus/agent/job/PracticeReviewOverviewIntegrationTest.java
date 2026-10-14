package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import de.tum.cit.aet.hephaestus.core.time.TimeBucketSize;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemEvents;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeGroupRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackBasisRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import de.tum.cit.aet.hephaestus.practices.model.ResultMark;
import de.tum.cit.aet.hephaestus.practices.result.ResultMarkRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewPracticeGroupDTO;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

class PracticeReviewOverviewIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String FROM = "2026-03-10T00:00:00Z";
    private static final String TO = "2026-03-13T00:00:00Z";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReviewJobRepository jobRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeGroupRepository groupRepository;

    @Autowired
    private ResultRepository practiceResultRepository;

    @Autowired
    private ResultMarkRepository invalidationRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackBasisRepository feedbackEvidenceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Workspace workspace;
    private Workspace otherWorkspace;
    private User subject;
    private int nextPosition;

    @BeforeEach
    void setUpWorkspaces() {
        workspace = createWorkspace(
                "review-overview",
                "Review overview",
                "review-overview-org",
                AccountType.ORG,
                persistUser("review-overview-owner"));
        ensureAdminMembership(workspace);
        ensureWorkspaceMembership(workspace, persistUser("testuser"), Membership.WorkspaceRole.MEMBER);
        subject = persistUser("review-overview-subject");
        ensureWorkspaceMembership(workspace, subject, Membership.WorkspaceRole.MEMBER);

        otherWorkspace = createWorkspace(
                "other-review-overview",
                "Other review overview",
                "other-review-overview-org",
                AccountType.ORG,
                persistUser("other-review-overview-owner"));
    }

    @Test
    @WithAdminUser
    void shouldCountReviewsResultsAndFeedbackInTheRangeWhenSeededAroundIt() {
        PracticeGroup group = persistGroup(workspace, "collaboration", "Collaboration");
        Practice described = persistPractice(workspace, group, "pr-description", "PR description");
        Practice tested = persistPractice(workspace, null, "tests", "Tests");
        Practice older = persistPractice(workspace, null, "older", "Older");
        persistPractice(workspace, null, "idle", "Idle");

        ReviewJob review = persistReviewAt(workspace, "2026-03-10T01:00:00Z", ReviewJobStatus.COMPLETED);
        persistReviewAt(workspace, "2026-03-10T05:00:00Z", ReviewJobStatus.FAILED);
        persistReviewAt(workspace, "2026-03-11T00:00:00Z", ReviewJobStatus.RUNNING);
        persistReviewAt(workspace, "2026-03-12T08:00:00Z", ReviewJobStatus.QUEUED);
        // Outside the half-open range, another purpose, another workspace: none of them count.
        persistReviewAt(workspace, "2026-03-09T23:59:59Z", ReviewJobStatus.COMPLETED);
        persistReviewAt(workspace, TO, ReviewJobStatus.COMPLETED);
        ReviewJob mentorTurn = persistReviewAt(workspace, "2026-03-11T00:00:00Z", ReviewJobStatus.COMPLETED);

        jobRepository.save(mentorTurn);
        ReviewJob elsewhere = persistReviewAt(otherWorkspace, "2026-03-11T00:00:00Z", ReviewJobStatus.COMPLETED);

        UUID problem = observe(review, described, "2026-03-10T02:00:00Z", "NOT_MET");
        UUID strength = observe(review, described, "2026-03-11T02:00:00Z", "MET");
        observe(review, described, "2026-03-11T03:00:00Z", "NOT_APPLICABLE");
        UUID undetermined = observe(review, tested, "2026-03-12T02:00:00Z", "UNDETERMINED");
        UUID beforeRange = observe(review, older, "2026-03-09T20:00:00Z", "NOT_MET");
        observe(review, described, TO, "MET");
        observe(
                elsewhere,
                persistPractice(otherWorkspace, null, "elsewhere", "Elsewhere"),
                "2026-03-11T02:00:00Z",
                "NOT_MET");

        invalidate(problem);
        ResultMark restored = invalidate(strength);
        restored.restore(1L, "It was right after all", Instant.parse("2026-03-12T00:00:00Z"));
        invalidationRepository.save(restored);

        // One piece of feedback fusing two results of one practice counts once for it; one bound to two
        // practices counts once for each.
        UUID proposed =
                persistFeedback(workspace, review, "2026-03-10T03:00:00Z", FeedbackState.AWAITING_APPROVAL);
        bind(proposed, problem);
        bind(proposed, strength);
        UUID delivered = persistFeedback(workspace, review, "2026-03-12T03:00:00Z", FeedbackState.DELIVERED);
        bind(delivered, strength);
        bind(delivered, undetermined);
        bind(
                persistFeedback(workspace, review, "2026-03-11T05:00:00Z", FeedbackState.PARTIALLY_DELIVERED),
                strength);
        persistFeedback(workspace, review, "2026-03-12T04:00:00Z", FeedbackState.PARTIALLY_FAILED);
        persistFeedback(workspace, review, "2026-03-11T04:00:00Z", FeedbackState.DISCARDED);
        bind(persistFeedback(workspace, review, "2026-03-11T06:00:00Z", FeedbackState.PREPARED), beforeRange);
        bind(persistFeedback(workspace, review, "2026-03-09T12:00:00Z", FeedbackState.PREPARED), problem);
        persistFeedback(otherWorkspace, elsewhere, "2026-03-11T04:00:00Z", FeedbackState.DELIVERED);

        PracticeReviewOverviewDTO overview = overview(FROM, TO, "UTC");

        assertThat(overview.from()).isEqualTo(Instant.parse(FROM));
        assertThat(overview.to()).isEqualTo(Instant.parse(TO));
        assertThat(overview.bucket()).isEqualTo(TimeBucketSize.DAY);
        assertThat(nonZero(overview.reviews()))
                .isEqualTo(Map.of("queued", 1L, "running", 1L, "completed", 1L, "failed", 1L));
        assertThat(nonZero(overview.practiceResults()))
                .isEqualTo(Map.of("met", 1L, "notMet", 1L, "notApplicable", 1L, "undetermined", 1L));
        assertThat(overview.practiceResultsInvalidated()).isEqualTo(1L);
        assertThat(nonZero(overview.feedback()))
                .isEqualTo(Map.of(
                        "awaitingApproval",
                        1L,
                        "prepared",
                        1L,
                        "partiallyDelivered",
                        1L,
                        "partiallyFailed",
                        1L,
                        "delivered",
                        1L,
                        "discarded",
                        1L));
        assertThat(overview.buckets())
                .extracting(
                        PracticeReviewBucketDTO::start,
                        PracticeReviewBucketDTO::reviews,
                        PracticeReviewBucketDTO::practiceResults,
                        PracticeReviewBucketDTO::feedback)
                .containsExactly(
                        tuple(Instant.parse("2026-03-10T00:00:00Z"), 2L, 1L, 1L),
                        tuple(Instant.parse("2026-03-11T00:00:00Z"), 1L, 2L, 3L),
                        tuple(Instant.parse("2026-03-12T00:00:00Z"), 1L, 1L, 2L));

        assertThat(overview.practices())
                .as("the most observed first; a practice with feedback on an older result still has a row")
                .extracting(PracticeReviewCountsDTO::practiceSlug)
                .containsExactly("pr-description", "tests", "older");
        PracticeReviewCountsDTO describedCounts = overview.practices().get(0);
        assertThat(describedCounts.group())
                .isEqualTo(new ReviewPracticeGroupDTO("collaboration", "Collaboration", null, null));
        assertThat(nonZero(describedCounts.practiceResults()))
                .isEqualTo(Map.of("met", 1L, "notMet", 1L, "notApplicable", 1L));
        assertThat(describedCounts.practiceResultsInvalidated()).isEqualTo(1L);
        assertThat(nonZero(describedCounts.feedback()))
                .isEqualTo(Map.of("awaitingApproval", 1L, "partiallyDelivered", 1L, "delivered", 1L));
        PracticeReviewCountsDTO testedCounts = overview.practices().get(1);
        assertThat(testedCounts.group()).isNull();
        assertThat(nonZero(testedCounts.practiceResults())).isEqualTo(Map.of("undetermined", 1L));
        assertThat(testedCounts.practiceResultsInvalidated()).isZero();
        assertThat(nonZero(testedCounts.feedback())).isEqualTo(Map.of("delivered", 1L));
        PracticeReviewCountsDTO olderCounts = overview.practices().get(2);
        assertThat(nonZero(olderCounts.practiceResults())).isEmpty();
        assertThat(nonZero(olderCounts.feedback())).isEqualTo(Map.of("prepared", 1L));
    }

    /** Each feedback count opens the feedback list filtered to one delivery state, so no state may go uncounted. */
    @Test
    @WithAdminUser
    void shouldCountEveryDeliveryStateOnceWhenEachHasOnePieceOfFeedback() {
        ReviewJob review = persistReviewAt(workspace, "2026-03-10T01:00:00Z", ReviewJobStatus.COMPLETED);
        Map<String, Long> expected = new HashMap<>();
        for (FeedbackState state : FeedbackState.values()) {
            persistFeedback(workspace, review, "2026-03-11T00:00:00Z", state);
            expected.put(camelCase(state.name()), 1L);
        }

        assertThat(nonZero(overview(FROM, TO, "UTC").feedback())).isEqualTo(expected);
    }

    private static String camelCase(String constant) {
        String[] words = constant.toLowerCase(Locale.ROOT).split("_", -1);
        StringBuilder result = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            result.append(words[i].substring(0, 1).toUpperCase(Locale.ROOT)).append(words[i].substring(1));
        }
        return result.toString();
    }

    @Test
    @WithAdminUser
    void shouldStartBucketsAtMidnightInTheZoneWhenAZoneIsGiven() {
        persistReviewAt(workspace, "2026-03-10T23:30:00Z", ReviewJobStatus.COMPLETED);

        PracticeReviewOverviewDTO overview = overview(FROM, TO, "Europe/Berlin");

        assertThat(overview.buckets())
                .extracting(PracticeReviewBucketDTO::start, PracticeReviewBucketDTO::reviews)
                .containsExactly(
                        tuple(Instant.parse("2026-03-09T23:00:00Z"), 0L),
                        tuple(Instant.parse("2026-03-10T23:00:00Z"), 1L),
                        tuple(Instant.parse("2026-03-11T23:00:00Z"), 0L),
                        tuple(Instant.parse("2026-03-12T23:00:00Z"), 0L));
        assertThat(overview.practices()).isEmpty();
    }

    @Test
    @WithUser
    void shouldRejectTheOverviewWhenTheCallerIsAMember() {
        webTestClient
                .get()
                .uri("/workspaces/{slug}/practices/reviews/overview", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
    }

    @Test
    @WithAdminUser
    void shouldRejectTheOverviewWhenTheZoneIsUnknown() {
        webTestClient
                .get()
                .uri(
                        "/workspaces/{slug}/practices/reviews/overview?zone={zone}",
                        workspace.getWorkspaceSlug(),
                        "Mars/Olympus_Mons")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Unknown time zone: Mars/Olympus_Mons");
    }

    /** The counts of a counts record that are not zero, by name, so an assertion names every count it expects. */
    private static Map<String, Long> nonZero(Record counts) {
        Map<String, Long> result = new HashMap<>();
        for (RecordComponent component : counts.getClass().getRecordComponents()) {
            try {
                if (component.getAccessor().invoke(counts) instanceof Long count && count != 0) {
                    result.put(component.getName(), count);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return result;
    }

    private PracticeReviewOverviewDTO overview(String from, String to, String zone) {
        return Objects.requireNonNull(webTestClient
                .get()
                .uri(
                        "/workspaces/{slug}/practices/reviews/overview?from={from}&to={to}&zone={zone}",
                        workspace.getWorkspaceSlug(),
                        from,
                        to,
                        zone)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeReviewOverviewDTO.class)
                .returnResult()
                .getResponseBody());
    }

    private PracticeGroup persistGroup(Workspace targetWorkspace, String slug, String name) {
        PracticeGroup result = new PracticeGroup();
        RuntimeJobTestData.initialize(result, (targetWorkspace).getId());
        result.setSlug(slug);
        result.setName(name);
        return groupRepository.save(result);
    }

    private Practice persistPractice(
            Workspace targetWorkspace, @Nullable PracticeGroup group, String slug, String name) {
        Practice result = new Practice();
        result.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        RuntimeJobTestData.initialize(result, (targetWorkspace).getId());
        result.setSlug(slug);
        result.setName(name);
        result.setGroup(group);
        result.setCriteria("Review the change");
        PracticeTestEvidence.configure(result, ScmItemEvents.PULL_REQUEST_OPENED);
        result.setAutonomy(FeedbackMode.AUTOMATIC);
        return practiceRepository.save(result);
    }

    /** {@code createdAt} is only defaulted when unset, so a review can be seeded at a chosen instant. */
    private ReviewJob persistReviewAt(Workspace targetWorkspace, String createdAt, ReviewJobStatus status) {
        ReviewJob result = new ReviewJob();
        RuntimeJobTestData.initialize(result, (targetWorkspace).getId());


        result.setConfigSnapshot(objectMapper.valueToTree(Map.of("model", "test")));
        result.setAvailableAt(Instant.parse(createdAt));
        result.setStatus(status);
        return jobRepository.save(result);
    }

    /** A recorded result for this practice. */
    private UUID observe(ReviewJob job, Practice practice, String observedAt, String outcome) {
        UUID id = UUID.randomUUID();
        practiceResultRepository.insertIfAbsent(
                id,
                "overview-" + id,
                job.getId(),
                job.getWorkspaceId(),
                practice.getId(),
                null,
                "scm.pull_request".value(),
                7L,
                subject.getId(),
                "Observed " + id,
                outcome,
                "NOT_MET".equals(outcome) ? "MINOR" : null,
                "{}",
                "Reasoning",
                "test-scope",
                null,
                null,
                null,
                Instant.parse(observedAt),
                "LIVE");
        return id;
    }

    private ResultMark invalidate(UUID practiceResultId) {
        return invalidationRepository.save(new ResultMark(
                practiceResultRepository
                        .findByIdAndWorkspaceId(practiceResultId, workspace.getId())
                        .orElseThrow(),
                1L,
                "Wrong when made",
                Instant.parse("2026-03-11T12:00:00Z")));
    }

    private UUID persistFeedback(
            Workspace targetWorkspace, ReviewJob job, String createdAt, FeedbackState state) {
        return feedbackRepository
                .save(Feedback.builder()
                        .agentJobId(job.getId())
                        .workspaceId(targetWorkspace.getId())
                        .itemKind("scm.pull_request")
                        .artifactId(7L)
                        .recipientUserId(subject.getId())
                        .aboutUserId(subject.getId())
                        .channel(FeedbackDestination.IN_CONTEXT)
                        .position(nextPosition++)
                        .deliveryState(state)
                        .body("Feedback")
                        .source(FeedbackSource.AGENT)
                        .createdAt(Instant.parse(createdAt))
                        .deliveredAt(state == FeedbackState.DELIVERED ? Instant.parse(createdAt) : null)
                        .build())
                .getId();
    }

    private void bind(UUID feedbackId, UUID practiceResultId) {
        feedbackEvidenceRepository.insertIfAbsent(feedbackId, practiceResultId, null, "PRIMARY", 0);
    }
}
