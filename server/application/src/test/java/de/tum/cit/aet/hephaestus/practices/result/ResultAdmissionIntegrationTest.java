package de.tum.cit.aet.hephaestus.practices.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemQuery;
import de.tum.cit.aet.hephaestus.practices.review.ReviewAdmissionData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewPracticeRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.AdmissionReceipt;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedResultQuery;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedResultSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.BoundModelSlot;
import de.tum.cit.aet.hephaestus.practices.spi.CandidateRefusal;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustody;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustodyQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.ProcessingStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ResultEndReason;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ResultStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAttemptGuard;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBudget;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewCaptureDecision;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewModelBindings;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewPracticeStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequest;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshotQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import de.tum.cit.aet.hephaestus.practices.spi.WithheldItem;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.node.JsonNodeFactory;

@Import({ReviewIntegrationData.class, ReviewAdmissionData.class})
class ResultAdmissionIntegrationTest extends BaseIntegrationTest {
    @Autowired
    ReviewIntegrationData data;

    @Autowired
    ReviewAdmissionData admissions;

    @Autowired
    ResultStore store;

    @Autowired
    ResultRepository results;

    @Autowired
    PracticeRepository practices;

    @Autowired
    AdmittedResultQuery proof;

    @Autowired
    ReviewRepository reviews;

    @Autowired
    ReviewService service;

    @Autowired
    ReviewAssignmentStore assignments;

    @Autowired
    ReviewPracticeRepository pinnedPractices;

    @Autowired
    ReviewSnapshotQuery snapshots;

    @Autowired
    ReviewAssignmentQuery executable;

    @MockitoSpyBean
    ProductItemQuery productItems;

    @MockitoBean
    ReviewAttemptGuard ownership;

    @MockitoBean
    ReviewRuntimeQuery runtime;

    @MockitoBean
    ReviewModelBindings models;

    @MockitoBean
    ReviewBudget budget;

    @MockitoBean
    HistoryCustodyQuery custody;

    ReviewIntegrationData.Scenario scenario;

    @BeforeEach
    void setup() {
        databaseTestUtils.cleanDatabase();
        scenario = data.scenario();
        when(custody.find(anyLong(), anyList()))
                .thenAnswer(call -> new HistoryCustody(true, List.of(), List.of(), call.getArgument(1)));
    }

    void running(ReviewAdmissionData.Pending p, int attempt) {
        admissions.running(p, attempt);
        when(runtime.runtime(p.review().getWorkspaceId(), p.review().getId()))
                .thenReturn(Optional.of(new ReviewRuntimeSnapshot(List.of(new ReviewJobSnapshot(
                        p.jobId(),
                        ReviewJobStatus.RUNNING,
                        attempt,
                        ProcessingStatus.PENDING,
                        Instant.now(),
                        Instant.now(),
                        null,
                        null,
                        null,
                        "test-trace")))));
    }

    AdmissionReceipt admit(ReviewAdmissionData.Pending p, ResultOutcome outcome) {
        running(p, 1);
        return store.admit(
                p.review().getWorkspaceId(),
                p.review().getId(),
                p.jobId(),
                1,
                List.of(admissions.candidate(p, outcome)));
    }

    @Test
    void recordsOneResultAndAllocatedChildProofForItsExactAttempt() {
        var pending =
                admissions.pending(scenario, Instant.now(), scenario.account().getId());
        var receipt = admit(pending, ResultOutcome.NOT_MET);
        assertThat(receipt.resultIds()).hasSize(1);
        var snapshots = proof.forAttempt(
                pending.review().getWorkspaceId(), pending.review().getId(), pending.jobId(), 1);
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().id()).isEqualTo(receipt.resultIds().getFirst());
        assertThat(snapshots.getFirst().problems()).hasSize(1);
        assertThat(snapshots.getFirst().problems().getFirst().id()).isNotNull();
        assertThat(proof.forAttempt(
                        pending.review().getWorkspaceId(), pending.review().getId(), pending.jobId(), 2))
                .isEmpty();
        assertThat(proof.forAttempt(
                        pending.review().getWorkspaceId(), pending.review().getId(), UUID.randomUUID(), 1))
                .isEmpty();
        assertThat(proof.forAttempt(
                        pending.review().getWorkspaceId() + 1, pending.review().getId(), pending.jobId(), 1))
                .isEmpty();
    }

    @Test
    void sameBodyReturnsSameIdsAndDifferentBodyIsConflict() {
        var pending =
                admissions.pending(scenario, Instant.now(), scenario.account().getId());
        var first = admit(pending, ResultOutcome.NOT_MET);
        var repeated = admit(pending, ResultOutcome.NOT_MET);
        assertThat(repeated).isEqualTo(first);
        assertThatThrownBy(() -> admit(pending, ResultOutcome.MET)).isInstanceOf(ResponseStatusException.class);
        assertThat(results.findByWorkspaceIdAndReviewIdOrderByRecordedAtAscIdAsc(
                        pending.review().getWorkspaceId(), pending.review().getId()))
                .hasSize(1);
    }

    @Test
    void olderLateAdmissionIsEndedOutrankedWithoutReplacingTheNewerClaim() {
        var older = admissions.pending(
                scenario, Instant.now().minusSeconds(120), scenario.account().getId());
        var newer =
                admissions.pending(scenario, Instant.now(), scenario.account().getId());
        var current = admit(newer, ResultOutcome.MET).resultIds().getFirst();
        var late = admit(older, ResultOutcome.NOT_MET).resultIds().getFirst();
        assertThat(results.findByIdAndWorkspaceId(late, scenario.workspace().getId())
                        .orElseThrow()
                        .getEndReason())
                .isEqualTo(ResultEndReason.OUTRANKED);
        assertThat(results.findCurrentClaim(
                                scenario.workspace().getId(),
                                scenario.practice().getId(),
                                scenario.item().getId(),
                                scenario.account().getId())
                        .orElseThrow()
                        .getId())
                .isEqualTo(current);
    }

    @Test
    void markedCurrentClaimStillOccupiesUntilANewerAdmissionReplacesIt() {
        var first = admissions.pending(
                scenario, Instant.now().minusSeconds(120), scenario.account().getId());
        var current = admit(first, ResultOutcome.NOT_MET).resultIds().getFirst();
        store.mark(
                scenario.workspace().getId(),
                current,
                ResultMarkKind.INCORRECT,
                scenario.account().getId(),
                "The claim is incorrect");
        var next =
                admissions.pending(scenario, Instant.now(), scenario.account().getId());
        var replacement = admit(next, ResultOutcome.MET).resultIds().getFirst();
        assertThat(results.findById(current).orElseThrow().getEndReason()).isEqualTo(ResultEndReason.REPLACED);
        assertThat(results.findById(replacement).orElseThrow().getEndedAt()).isNull();
    }

    @Test
    void cancellationFenceRejectsLaterAdmissionAndKeepsAlreadyAdmittedProof() {
        var pending =
                admissions.pending(scenario, Instant.now(), scenario.account().getId());
        var saved = admit(pending, ResultOutcome.NOT_MET).resultIds().getFirst();
        service.cancel(scenario.workspace().getId(), pending.review().getId(), "CANCELLED_BY_ADMIN");
        assertThatThrownBy(() -> admit(pending, ResultOutcome.NOT_MET)).isInstanceOf(ResponseStatusException.class);
        assertThat(results.findById(saved)).isPresent();
        assertThat(reviews.findById(pending.review().getId()).orElseThrow().getStatus())
                .isEqualTo(ReviewStatus.RUNNING);
    }

    @Test
    void shouldKeepItemWithholdingSeparateFromResults() {
        var pending =
                admissions.pending(scenario, Instant.now(), scenario.account().getId());
        var assignment = new ReviewAssignmentId(
                pending.review().getId(),
                scenario.practice().getId(),
                scenario.item().getId());
        assertThat(executable.executable(
                        scenario.workspace().getId(),
                        pending.review().getId(),
                        scenario.item().getId()))
                .containsExactly(assignment);
        assignments.withhold(
                scenario.workspace().getId(),
                assignment,
                CandidateRefusal.QUOTE_UNVERIFIED,
                "The captured text cannot verify this quote.");
        assertThat(executable.executable(
                        scenario.workspace().getId(),
                        pending.review().getId(),
                        scenario.item().getId()))
                .isEmpty();
        var receipt = admit(pending, ResultOutcome.NOT_MET);
        assertThat(receipt.resultIds()).isEmpty();
        assertThat(receipt.withheldItems())
                .containsExactly(new WithheldItem(
                        scenario.practice().getId(), scenario.item().getId(), "quote_unverified"));
        var pinned = pinnedPractices
                .findByWorkspaceIdAndReviewIdAndPracticeId(
                        scenario.workspace().getId(),
                        pending.review().getId(),
                        scenario.practice().getId())
                .orElseThrow();
        assertThat(pinned.getStatus()).isEqualTo(ReviewPracticeStatus.WITHHELD);
        assertThat(pinned.getReason()).isEqualTo("quote_unverified");
        assertThat(results.findByWorkspaceIdAndReviewIdOrderByRecordedAtAscIdAsc(
                        scenario.workspace().getId(), pending.review().getId()))
                .isEmpty();
        assertThat(reviews.findById(pending.review().getId()).orElseThrow().getAdmittedAt())
                .isNull();
    }

    @Test
    void shouldKeepTheFirstPublicAcceptanceOnTheProductReview() {
        var pending =
                admissions.pending(scenario, Instant.now(), scenario.account().getId());
        admit(pending, ResultOutcome.MET);
        var acceptedAt = Instant.now();
        assertThat(assignments.publicReviewed(
                        scenario.workspace().getId(), pending.review().getId(), pending.jobId(), 1, acceptedAt))
                .isTrue();
        var recorded = snapshots
                .publicReviewedAt(scenario.workspace().getId(), pending.review().getId())
                .orElseThrow();
        assertThat(assignments.publicReviewed(
                        scenario.workspace().getId(),
                        pending.review().getId(),
                        pending.jobId(),
                        1,
                        acceptedAt.plusSeconds(60)))
                .isFalse();
        admissions.removeRuntime(pending);
        assertThat(snapshots.publicReviewedAt(
                        scenario.workspace().getId(), pending.review().getId()))
                .contains(recorded);
        assertThat(snapshots.publicReviewedAt(
                        scenario.workspace().getId() + 1, pending.review().getId()))
                .isEmpty();
        assertThat(reviews.findById(pending.review().getId()).orElseThrow().getCompletedAt())
                .isNull();
    }

    @Test
    void shouldRecordCaptureFailureWithoutCreatingAMissingEvidenceResult() {
        var pending =
                admissions.pending(scenario, Instant.now(), scenario.account().getId());
        running(pending, 1);
        assignments.capture(
                scenario.workspace().getId(),
                pending.review().getId(),
                pending.jobId(),
                1,
                List.of(new ReviewCaptureDecision(
                        scenario.practice().getId(),
                        ReviewPracticeStatus.FAILED,
                        "CAPTURE_FAILED",
                        JsonNodeFactory.instance.objectNode().put("status", "FAILED"))));
        var receipt = admit(pending, ResultOutcome.COULD_NOT_DETERMINE);
        assertThat(receipt.resultIds()).isEmpty();
        assertThat(receipt.withheldItems())
                .containsExactly(new WithheldItem(
                        scenario.practice().getId(), scenario.item().getId(), "CAPTURE_FAILED"));
        var pinned = pinnedPractices
                .findByWorkspaceIdAndReviewIdAndPracticeId(
                        scenario.workspace().getId(),
                        pending.review().getId(),
                        scenario.practice().getId())
                .orElseThrow();
        assertThat(pinned.getStatus()).isEqualTo(ReviewPracticeStatus.FAILED);
        var capture = pinned.getCaptureDecision();
        assertThat(capture).isNotNull();
        assertThat(capture.path("status").asString()).isEqualTo("FAILED");
        assertThat(results.findByWorkspaceIdAndReviewIdOrderByRecordedAtAscIdAsc(
                        scenario.workspace().getId(), pending.review().getId()))
                .isEmpty();
    }

    @Test
    void shouldRetainMissingRoleWithholdingWithoutGrantingAnonymousModelAuthority() {
        var workspace = scenario.workspace().getId();
        var ref = productItems.require(workspace, scenario.item().getId());
        var pinned = new ItemSnapshot(
                ref,
                new ItemVersion("selected-version", null),
                new IntegrationRef(IntegrationKind.GITHUB, workspace, "https://github.com", null),
                Map.of("state", "OPEN", "draft", "false"),
                List.of(),
                new ItemLabel(
                        ref.itemId(), "Selected pull request", "pull request", 1, null, null, IntegrationKind.GITHUB));
        doReturn(pinned).when(productItems).pin(eq(ref), isNull());
        when(budget.canRequest(workspace, ReviewKind.ITEM, null)).thenReturn(true);
        when(models.models(workspace, List.of()))
                .thenReturn(List.of(
                        new BoundModelSlot("chat", ModelKind.CHAT, null),
                        new BoundModelSlot("decision", ModelKind.DECISION, null),
                        new BoundModelSlot("embedding", ModelKind.EMBEDDING, null),
                        new BoundModelSlot("reranking", ModelKind.RERANKING, null),
                        new BoundModelSlot("review", ModelKind.CHAT, null)));
        var id = service.request(new ReviewRequest(
                workspace,
                ref.itemId(),
                ReviewKind.ITEM,
                null,
                ReviewOrigin.REQUEST,
                null,
                scenario.account().getId(),
                null,
                null,
                null,
                null,
                List.of(scenario.practice().getId()),
                false));
        assertThat(reviews.findById(id).orElseThrow().getStatus()).isEqualTo(ReviewStatus.PARTIAL);
        var held = pinnedPractices
                .findByWorkspaceIdAndReviewIdAndPracticeId(
                        workspace, id, scenario.practice().getId())
                .orElseThrow();
        assertThat(held.getStatus()).isEqualTo(ReviewPracticeStatus.WITHHELD);
        assertThat(held.getReason()).isEqualTo("ROLE_UNAVAILABLE");
        assertThat(executable.executable(workspace, id, ref.itemId())).isEmpty();
        assertThat(results.findByWorkspaceIdAndReviewIdOrderByRecordedAtAscIdAsc(workspace, id))
                .isEmpty();
    }

    @Test
    void legitimateNullDeveloperIsKeptRatherThanInvented() {
        var original = DefinitionTestFixtures.definition();
        scenario = data.scenario(new PracticeDefinition(
                original.name(),
                original.reviewOn(),
                original.appliesWhen(),
                null,
                original.reads(),
                original.reviewedBy(),
                original.criteria(),
                original.script(),
                original.scan(),
                original.lineNotes(),
                original.guidance()));
        var pending = admissions.pending(scenario, Instant.now(), null);
        var id = admit(pending, ResultOutcome.MET).resultIds().getFirst();
        assertThat(results.findById(id).orElseThrow().getDeveloperId()).isNull();
    }

    @Test
    void zeroAttemptIsAnExactValidExecutionAttempt() {
        var pending =
                admissions.pending(scenario, Instant.now(), scenario.account().getId());
        running(pending, 0);
        var receipt = store.admit(
                scenario.workspace().getId(),
                pending.review().getId(),
                pending.jobId(),
                0,
                List.of(admissions.candidate(pending, ResultOutcome.MET)));
        assertThat(proof.forAttempt(
                        scenario.workspace().getId(), pending.review().getId(), pending.jobId(), 0))
                .extracting(AdmittedResultSnapshot::id)
                .containsExactlyElementsOf(receipt.resultIds());
    }

    @Test
    void feedbackModeOffStillRecordsTheResult() {
        var pending =
                admissions.pending(scenario, Instant.now(), scenario.account().getId());
        scenario.practice().setFeedbackMode(FeedbackMode.OFF);
        var id = admit(pending, ResultOutcome.NOT_MET).resultIds().getFirst();
        assertThat(results.findById(id).orElseThrow().getOutcome()).isEqualTo(ResultOutcome.NOT_MET);
    }
}
