package de.tum.cit.aet.hephaestus.practices.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonCopyIdentity;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentityResolver;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonScope;
import de.tum.cit.aet.hephaestus.integration.core.item.Item;
import de.tum.cit.aet.hephaestus.integration.core.item.ItemRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.adapter.PracticesPersonDataCatalog;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultMarkService;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewItemRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewOverviewService;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewItem;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanReportService;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ReusedScanReportItemDTO;
import de.tum.cit.aet.hephaestus.practices.spi.CandidateRefusal;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustody;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustodyQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ProcessingStatus;
import de.tum.cit.aet.hephaestus.practices.spi.RemovalGuardState;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAttemptGuard;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBudget;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewCaptureDecision;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewCompletion;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewModelBindings;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewPracticeStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewScanStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStopReason;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Import({ReviewIntegrationData.class, ScanReportTestData.class})
class ScanReportIntegrationTest extends BaseIntegrationTest {
    @Autowired
    ScanReportTestData data;

    @Autowired
    ScanQueryService query;

    @Autowired
    ReviewScanStore store;

    @Autowired
    ReviewAssignmentStore assignments;

    @Autowired
    ReviewOverviewService overview;

    @Autowired
    ReviewService reviews;

    @Autowired
    ScanReportService reports;

    @Autowired
    ResultMarkService marks;

    @Autowired
    ItemRepository items;

    @MockitoBean
    EvidenceAuthorization authorization;

    @MockitoBean
    ReviewRuntimeQuery runtime;

    @MockitoBean
    ReviewModelBindings models;

    @MockitoBean
    ReviewBudget budget;

    @MockitoBean
    HistoryCustodyQuery custody;

    @MockitoBean
    ReviewAttemptGuard ownership;

    @Autowired
    PracticesPersonDataCatalog personData;

    @Autowired
    ReviewItemRepository membership;

    @Autowired
    JsonMapper json;

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    PersonIdentityResolver people;

    ScanReportTestData.Scenario scenario;

    long scenarioAccountId;

    @BeforeEach
    void setup() {
        databaseTestUtils.cleanDatabase();
        scenario = data.scenario();
        scenarioAccountId = Objects.requireNonNull(scenario.owner().account().getId());
        when(custody.find(anyLong(), anyList()))
                .thenAnswer(call -> new HistoryCustody(false, List.of(), List.of(), call.getArgument(1)));
        when(authorization.permitsAll(anyLong(), anyCollection(), any())).thenAnswer(call -> {
            long workspace = call.getArgument(0);
            Collection<Result> rows = call.getArgument(1);
            return rows.stream()
                    .filter(r -> r.getWorkspaceId() == workspace)
                    .map(Result::getId)
                    .collect(Collectors.toSet());
        });
        when(authorization.permits(anyLong(), any(), any()))
                .thenAnswer(call -> ((Result) call.getArgument(1)).getWorkspaceId() == (long) call.getArgument(0));
    }

    @Test
    void oneResultWithTwoProblemsHasOneReviewedPairAndTwoProblems() {
        var scan = data.scan(scenario, ReviewStatus.COMPLETE, Instant.now().minusSeconds(60));
        data.result(scenario, scan);
        var report = query.report(scan.getWorkspaceId(), scan.getId());
        assertThat(report.counts().reviewed()).isOne();
        assertThat(report.counts().problems()).isEqualTo(2);
        var recorded = report.recordedCounts();
        assertThat(recorded).isNotNull();
        assertThat(recorded.problems()).isEqualTo(2);
        assertThat(report.problemChanges().added()).isEqualTo(2);
    }

    @Test
    void shouldExposeDurablePendingScanFactsAndRequireTheTenantLockTransaction() {
        var scan = data.scan(scenario, ReviewStatus.RUNNING, Instant.now());
        assertThat(store.active(100))
                .filteredOn(s -> s.review().id().equals(scan.getId()))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.inventoryRecorded()).isTrue();
                    assertThat(s.inventoryComplete()).isTrue();
                    assertThat(s.listedPracticeIds())
                            .containsExactly(scenario.owner().practice().getId());
                    assertThat(s.listed()).isOne();
                    assertThat(s.judged()).isZero();
                });
        assertThat(store.pending(scan.getWorkspaceId(), scan.getId(), 1))
                .extracting(s -> s.id().itemId())
                .containsExactly(scenario.file().getId());
        assertThatThrownBy(() -> store.lock(scan.getWorkspaceId(), scan.getId()))
                .isInstanceOf(IllegalTransactionStateException.class);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertThat(store.lock(scan.getWorkspaceId(), scan.getId()).review().id())
                    .isEqualTo(scan.getId());
        });
        assertThatThrownBy(() -> new TransactionTemplate(transactions)
                        .executeWithoutResult(tx -> store.lock(scan.getWorkspaceId() + 1, scan.getId())))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void shouldExcludeLiveInvocationsAndRebindUnjudgedMembershipAfterTheOldInvocationEnds() {
        var scan = data.scan(scenario, ReviewStatus.RUNNING, Instant.now());
        var assignment = new ReviewAssignmentId(
                scan.getId(),
                scenario.owner().practice().getId(),
                scenario.file().getId());
        var first = invocation(scan.getWorkspaceId(), scan.getId(), ReviewJobStatus.QUEUED);
        assignments.bind(scan.getWorkspaceId(), scan.getId(), first, List.of(assignment));
        when(runtime.activeJobIds(scan.getWorkspaceId(), scan.getId())).thenReturn(Set.of(first));
        assertThat(store.pending(scan.getWorkspaceId(), scan.getId(), 10)).isEmpty();
        var next = invocation(scan.getWorkspaceId(), scan.getId(), ReviewJobStatus.FAILED);
        assertThatThrownBy(() -> assignments.bind(scan.getWorkspaceId(), scan.getId(), next, List.of(assignment)))
                .isInstanceOf(IllegalStateException.class);
        jdbc.sql("UPDATE review_job SET status='FAILED' WHERE workspace_id=:ws AND id=:job")
                .param("ws", scan.getWorkspaceId())
                .param("job", first)
                .update();
        jdbc.sql("UPDATE review_job SET status='QUEUED' WHERE workspace_id=:ws AND id=:job")
                .param("ws", scan.getWorkspaceId())
                .param("job", next)
                .update();
        when(runtime.activeJobIds(scan.getWorkspaceId(), scan.getId())).thenReturn(Set.of(next));
        assertThat(store.pending(scan.getWorkspaceId(), scan.getId(), 10))
                .extracting(s -> s.id())
                .containsExactly(assignment);
        assignments.bind(scan.getWorkspaceId(), scan.getId(), next, List.of(assignment));
        var bound = membership
                .findByWorkspaceIdAndReviewIdAndPracticeIdAndItemId(
                        scan.getWorkspaceId(), scan.getId(), assignment.practiceId(), assignment.itemId())
                .orElseThrow();
        assertThat(bound.getJobId()).isEqualTo(next);
        assertThat(bound.getResultId()).isNull();
    }

    private UUID invocation(long workspaceId, UUID reviewId, ReviewJobStatus status) {
        var id = UUID.randomUUID();
        jdbc.sql("INSERT INTO"
            + " review_job(id,review_id,workspace_id,status,attempt,processing_status,trace_id,input,config_snapshot)"
            + " VALUES(:id,:review,:ws,:status,0,'PENDING',:trace,CAST(:input AS jsonb),'{}'::jsonb)")
                .param("id", id)
                .param("review", reviewId)
                .param("ws", workspaceId)
                .param("status", status.name())
                .param(
                        "input",
                        json.writeValueAsString(Map.of(
                                "itemId",
                                scenario.file().getId(),
                                "assignments",
                                List.of(new ReviewAssignmentId(
                                        reviewId,
                                        scenario.owner().practice().getId(),
                                        scenario.file().getId())))))
                .param("trace", id.toString())
                .update();
        return id;
    }

    @Test
    void shouldFreezeCancellationCountsOnlyAfterTerminalProcessingRecordsTheFailure() {
        var scan = data.scan(scenario, ReviewStatus.RUNNING, Instant.now());
        long workspaceId = scan.getWorkspaceId();
        UUID reviewId = scan.getId();
        var jobId = invocation(workspaceId, reviewId, ReviewJobStatus.RUNNING);
        var assignment = new ReviewAssignmentId(
                reviewId, scenario.owner().practice().getId(), scenario.file().getId());
        assignments.bind(workspaceId, reviewId, jobId, List.of(assignment));
        jdbc.sql("UPDATE review_job SET"
                        + " status='CANCELLED',started_at=now(),execution_started_at=now(),completed_at=now()"
                        + " WHERE workspace_id=:ws AND id=:id")
                .param("ws", workspaceId)
                .param("id", jobId)
                .update();
        when(runtime.activeJobIds(workspaceId, reviewId)).thenReturn(Set.of());
        when(runtime.unsettledJobIds(workspaceId, reviewId)).thenReturn(Set.of(jobId));
        assertThat(reviews.cancel(workspaceId, reviewId, "CANCELLED_BY_ADMIN")).isTrue();
        assertThat(jdbc.sql("SELECT report IS NULL FROM review WHERE workspace_id=:ws AND id=:id")
                        .param("ws", workspaceId)
                        .param("id", reviewId)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assignments.fail(
                workspaceId,
                assignment,
                ReviewStopReason.CANCELLED,
                "The invoked execution stopped before admitting a result.");
        jdbc.sql("UPDATE review_job SET processing_status='FAILED' WHERE workspace_id=:ws AND id=:id")
                .param("ws", workspaceId)
                .param("id", jobId)
                .update();
        when(runtime.unsettledJobIds(workspaceId, reviewId)).thenReturn(Set.of());
        reviews.complete(
                workspaceId,
                reviewId,
                new ReviewCompletion(
                        ReviewStatus.CANCELLED, ReviewStopReason.CANCELLED, Instant.now(), scan.getOrderedAt()));
        var report = query.report(workspaceId, reviewId);
        assertThat(report.scan().status()).isEqualTo(ReviewStatus.CANCELLED);
        var recorded = report.recordedCounts();
        assertThat(recorded).isNotNull();
        assertThat(recorded.withheld()).isOne();
        assertThat(recorded.notReviewed()).isZero();
        assertThat(recorded.reviewed()).isZero();
        var evidence = overview.evidenceOutcomes(workspaceId).getFirst();
        assertThat(evidence.withheld()).isOne();
        assertThat(evidence.notReviewed()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM result WHERE workspace_id=:ws AND review_id=:review")
                        .param("ws", workspaceId)
                        .param("review", reviewId)
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    void shouldRetainFileCaptureRefusalWithoutWithholdingTheWholeScanPractice() {
        var scan = data.scan(scenario, ReviewStatus.RUNNING, Instant.now());
        var other = Objects.requireNonNull(new TransactionTemplate(transactions).execute(tx -> {
            var created = new Item();
            created.setWorkspaceId(scan.getWorkspaceId());
            created.setKind(new ItemKind("scm.file"));
            created.setParentId(scenario.repository().getId());
            created.setPath("src/Other.java");
            created.setCreatedAt(Instant.now());
            items.saveAndFlush(created);
            membership.saveAndFlush(ReviewItem.builder()
                    .workspaceId(scan.getWorkspaceId())
                    .reviewId(scan.getId())
                    .practiceId(scenario.owner().practice().getId())
                    .itemId(created.getId())
                    .itemVersion("file-version-two")
                    .build());
            jdbc.sql("UPDATE review_practice SET items_listed=2 WHERE workspace_id=:ws AND review_id=:review AND"
                            + " practice_id=:practice")
                    .param("ws", scan.getWorkspaceId())
                    .param("review", scan.getId())
                    .param("practice", scenario.owner().practice().getId())
                    .update();
            return created;
        }));
        var job = invocation(scan.getWorkspaceId(), scan.getId(), ReviewJobStatus.RUNNING);
        assignments.bind(
                scan.getWorkspaceId(),
                scan.getId(),
                job,
                List.of(new ReviewAssignmentId(
                        scan.getId(),
                        scenario.owner().practice().getId(),
                        scenario.file().getId())));
        when(runtime.runtime(scan.getWorkspaceId(), scan.getId()))
                .thenReturn(Optional.of(new ReviewRuntimeSnapshot(List.of(new ReviewJobSnapshot(
                        job,
                        ReviewJobStatus.RUNNING,
                        0,
                        ProcessingStatus.PENDING,
                        Instant.now(),
                        Instant.now(),
                        null,
                        null,
                        null,
                        job.toString())))));
        when(runtime.activeJobIds(scan.getWorkspaceId(), scan.getId())).thenReturn(Set.of(job));
        var copiedPerson = new PersonCopyIdentity("GITHUB", "https://github.com", "copied-from-refused-file", null);
        when(custody.find(scan.getWorkspaceId(), List.of(job)))
                .thenReturn(new HistoryCustody(false, List.of(copiedPerson), List.of(), List.of(job)));
        assignments.capture(
                scan.getWorkspaceId(),
                scan.getId(),
                job,
                0,
                List.of(new ReviewCaptureDecision(
                        scenario.owner().practice().getId(),
                        ReviewPracticeStatus.FAILED,
                        "CAPTURE_FAILED",
                        json.createObjectNode().put("status", "FAILED"))));
        var refused = membership
                .findByWorkspaceIdAndReviewIdAndPracticeIdAndItemId(
                        scan.getWorkspaceId(),
                        scan.getId(),
                        scenario.owner().practice().getId(),
                        scenario.file().getId())
                .orElseThrow();
        var detail = refused.getDetail();
        assertThat(detail).isNotNull();
        assertThat(detail.path("captureDecision").path("status").asString()).isEqualTo("FAILED");
        assertThat(detail.path("custody").path("complete").asBoolean()).isFalse();
        assertThat(detail.path("custody").path("sourceJobIds").get(0).asString())
                .isEqualTo(job.toString());
        assertThat(refused.getResultId()).isNull();
        assertThat(store.pending(scan.getWorkspaceId(), scan.getId(), 10))
                .extracting(s -> s.id().itemId())
                .containsExactly(other.getId());
        when(people.providers())
                .thenReturn(List.of(new PersonIdentityResolver.Provider(8, "GITHUB", "https://github.com")));
        jdbc.sql("UPDATE review_item SET job_id=NULL WHERE workspace_id=:ws AND job_id=:job")
                .param("ws", scan.getWorkspaceId())
                .param("job", job)
                .update();
        jdbc.sql("DELETE FROM review_job WHERE workspace_id=:ws AND id=:job")
                .param("ws", scan.getWorkspaceId())
                .param("job", job)
                .update();
        var capturedStore = personData.contributors().stream()
                .filter(c -> c.store().equals("review_item_result_reference"))
                .findFirst()
                .orElseThrow();
        var capturedRows = capturedStore
                .select(new PersonScope(
                        null, List.of(new PersonIdentity(8, "copied-from-refused-file", null)), List.of()))
                .rows();
        assertThat(capturedRows)
                .anySatisfy(key -> assertThat(key.columns().get("item_id"))
                        .isEqualTo(Long.toString(scenario.file().getId())));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var snapshot = store.lock(scan.getWorkspaceId(), scan.getId());
            assertThat(snapshot.review().practices()).hasSize(1);
            assertThat(snapshot.listed()).isEqualTo(2);
            assertThat(snapshot.withheld()).isOne();
        });
    }

    @Test
    void acceptedChangesOpenCountsAndPagesWithoutChangingRecordedFacts() {
        var scan = data.scan(scenario, ReviewStatus.COMPLETE, Instant.now().minusSeconds(60));
        var result = data.result(scenario, scan);
        var before = query.report(scan.getWorkspaceId(), scan.getId());
        marks.create(
                scan.getWorkspaceId(),
                result.getId(),
                ResultMarkKind.ACCEPTED,
                scenarioAccountId,
                "This tradeoff is accepted");
        var after = query.report(scan.getWorkspaceId(), scan.getId());
        assertThat(after.counts().openProblems()).isZero();
        assertThat(after.recordedCounts()).isEqualTo(before.recordedCounts());
        assertThat(after.problemChanges()).isEqualTo(before.problemChanges());
        assertThat(after.practices().getFirst().counts().openProblems()).isZero();
        assertThat(query.problems(
                                scan.getWorkspaceId(),
                                scan.getId(),
                                null,
                                20,
                                new ScanQueryService.ProblemFilter(null, null, null, null, true))
                        .content())
                .isEmpty();
        assertThat(query.problems(
                                scan.getWorkspaceId(),
                                scan.getId(),
                                null,
                                20,
                                new ScanQueryService.ProblemFilter(null, null, null, null, false))
                        .content())
                .hasSize(2);
    }

    @Test
    void reusedMembershipKeepsItsOriginalProducerAndAcceptedVerdictIsReusable() {
        var original = data.scan(scenario, ReviewStatus.COMPLETE, Instant.now().minusSeconds(120));
        var result = data.result(scenario, original);
        marks.create(
                original.getWorkspaceId(),
                result.getId(),
                ResultMarkKind.ACCEPTED,
                scenarioAccountId,
                "Accept the known tradeoff");
        var current = data.scan(scenario, ReviewStatus.RUNNING, Instant.now().minusSeconds(10));
        var id = new ReviewAssignmentId(current.getId(), result.getPracticeId(), result.getItemId());
        assertThat(store.reuse(current.getWorkspaceId(), id)).contains(result.getId());
        data.terminal(current, ReviewStatus.COMPLETE, null);
        var page = query.items(
                current.getWorkspaceId(),
                current.getId(),
                null,
                10,
                new ScanQueryService.ItemFilter(null, null, null, null, null));
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst()).isInstanceOf(ReusedScanReportItemDTO.class);
        var report = query.report(current.getWorkspaceId(), current.getId());
        assertThat(report.counts().reviewed()).isOne();
        assertThat(report.counts().reused()).isOne();
        assertThat(report.counts().openProblems()).isZero();
    }

    @Test
    void runtimeWithheldAndClosedUnreachedRemainSeparate() {
        var scan = data.scan(scenario, ReviewStatus.RUNNING, Instant.now());
        assignments.withhold(
                scan.getWorkspaceId(),
                new ReviewAssignmentId(
                        scan.getId(),
                        scenario.owner().practice().getId(),
                        scenario.file().getId()),
                CandidateRefusal.QUOTE_UNVERIFIED,
                "The captured text cannot verify this quote.");
        data.terminal(scan, ReviewStatus.PARTIAL, ReviewStopReason.RESULT_WITHHELD);
        assertThat(query.report(scan.getWorkspaceId(), scan.getId()).counts().withheld())
                .isOne();
        var next = data.scan(scenario, ReviewStatus.RUNNING, Instant.now());
        data.terminal(next, ReviewStatus.CANCELLED, ReviewStopReason.CANCELLED);
        var report = query.report(next.getWorkspaceId(), next.getId());
        assertThat(report.counts().withheld()).isZero();
        assertThat(report.counts().notReviewed()).isOne();
    }

    @Test
    void inaccessibleProofIsRedactedAndReportedUnavailable() {
        var scan = data.scan(scenario, ReviewStatus.COMPLETE, Instant.now().minusSeconds(60));
        data.result(scenario, scan);
        when(authorization.permitsAll(anyLong(), anyCollection(), any())).thenReturn(Set.of());
        var report = query.report(scan.getWorkspaceId(), scan.getId());
        assertThat(report.availability().withheldProofPairs()).isOne();
        assertThat(report.counts().problems()).isEqualTo(2);
        assertThat(report.counts().openProblems()).isZero();
        assertThat(query.problems(
                                scan.getWorkspaceId(),
                                scan.getId(),
                                null,
                                20,
                                new ScanQueryService.ProblemFilter(null, null, null, null, false))
                        .content())
                .isEmpty();
    }

    @Test
    void rawExpiryKeepsAggregateAndReturnsGoneForProofPages() {
        var scan = data.scan(scenario, ReviewStatus.COMPLETE, Instant.now().minusSeconds(401L * 86400));
        data.result(scenario, scan);
        reports.expire(scan.getWorkspaceId(), scan.getId());
        var report = query.report(scan.getWorkspaceId(), scan.getId());
        assertThat(report.availability().expired()).isTrue();
        var recorded = report.recordedCounts();
        assertThat(recorded).isNotNull();
        assertThat(recorded.problems()).isEqualTo(2);
        assertThatThrownBy(() -> query.items(
                        scan.getWorkspaceId(),
                        scan.getId(),
                        null,
                        20,
                        new ScanQueryService.ItemFilter(null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error)
                                .getStatusCode()
                                .value())
                        .isEqualTo(410));
    }

    @Test
    void problemCursorCannotBeReplayedAgainstAnotherScan() {
        var first = data.scan(scenario, ReviewStatus.COMPLETE, Instant.now().minusSeconds(60));
        data.result(scenario, first);
        var page = query.problems(
                first.getWorkspaceId(),
                first.getId(),
                null,
                1,
                new ScanQueryService.ProblemFilter(null, null, null, null, false));
        assertThat(page.hasNext()).isTrue();
        var second = data.scan(scenario, ReviewStatus.COMPLETE, Instant.now());
        assertThatThrownBy(() -> query.problems(
                        second.getWorkspaceId(),
                        second.getId(),
                        page.nextCursor(),
                        1,
                        new ScanQueryService.ProblemFilter(null, null, null, null, false)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void erasureRemovesProofReferencesAndKeepsLabelledCloseCounts() {
        var scan = data.scan(scenario, ReviewStatus.COMPLETE, Instant.now().minusSeconds(60));
        var result = data.result(scenario, scan);
        var before = query.report(scan.getWorkspaceId(), scan.getId());
        assertThat(reports.eraseResultReferences(scan.getWorkspaceId(), List.of(result.getId())))
                .isOne();
        var after = query.report(scan.getWorkspaceId(), scan.getId());
        assertThat(after.recordedCounts()).isEqualTo(before.recordedCounts());
        assertThat(after.availability().withheldProofPairs()).isOne();
        assertThat(query.items(
                                scan.getWorkspaceId(),
                                scan.getId(),
                                null,
                                20,
                                new ScanQueryService.ItemFilter(null, null, null, null, null))
                        .content())
                .isEmpty();
    }

    @Test
    void exactlyTwentyPercentRemovalIsSuspectAndIncompleteListingNeverRemoves() {
        var present = new ArrayList<Long>();
        present.add(scenario.file().getId());
        for (int n = 0; n < 4; n++) {
            var file = new Item();
            file.setWorkspaceId(scenario.owner().workspace().getId());
            file.setKind(new ItemKind("scm.file"));
            file.setParentId(scenario.repository().getId());
            file.setPath("src/Other" + n + ".java");
            file.setCreatedAt(Instant.now().minusSeconds(7200));
            items.saveAndFlush(file);
            present.add(file.getId());
        }
        var scan = data.scan(scenario, ReviewStatus.RUNNING, Instant.now());
        store.inventory(scan.getWorkspaceId(), scan.getId(), present.subList(0, 4), true);
        var guard = query.report(scan.getWorkspaceId(), scan.getId()).removalGuard();
        assertThat(guard.previousFiles()).isEqualTo(5);
        assertThat(guard.wouldRemove()).isOne();
        assertThat(guard.state()).isEqualTo(RemovalGuardState.SUSPECT_LISTING);
        assertThat(items.findById(present.getLast()).orElseThrow().getRemovedAt())
                .isNull();
        store.inventory(scan.getWorkspaceId(), scan.getId(), List.of(), false);
        assertThat(query.report(scan.getWorkspaceId(), scan.getId())
                        .removalGuard()
                        .state())
                .isEqualTo(RemovalGuardState.INCOMPLETE_LISTING);
        assertThat(items.findById(scenario.file().getId()).orElseThrow().getRemovedAt())
                .isNull();
    }

    @Test
    void retainedCustodySelectsDeveloperlessCopiedProofWithoutRuntimeJobRows() {
        var scan = data.scan(scenario, ReviewStatus.COMPLETE, Instant.now().minusSeconds(60));
        var result = data.result(scenario, scan);
        assertThat(result.getDeveloperId()).isNull();
        var member = membership
                .findByWorkspaceIdAndReviewIdAndPracticeIdAndItemId(
                        scan.getWorkspaceId(), scan.getId(), result.getPracticeId(), result.getItemId())
                .orElseThrow();
        var copy = new PersonCopyIdentity("GITHUB", "https://github.com", "copied-person", null);
        var detail = (ObjectNode) Objects.requireNonNull(member.getDetail()).deepCopy();
        detail.set(
                "custody",
                json.valueToTree(new HistoryCustody(
                        true, List.of(copy), List.of(), List.of(Objects.requireNonNull(member.getJobId())))));
        member.setDetail(detail);
        membership.saveAndFlush(member);
        when(people.providers())
                .thenReturn(List.of(new PersonIdentityResolver.Provider(8, "GITHUB", "https://github.com")));
        var person = new PersonScope(null, List.of(new PersonIdentity(8, "copied-person", null)), List.of());
        var store = personData.contributors().stream()
                .filter(c -> c.store().equals("result"))
                .findFirst()
                .orElseThrow();
        assertThat(store.select(person).rows())
                .anySatisfy(key -> assertThat(key.columns().get("id"))
                        .isEqualTo(result.getId().toString()));
        assertThat(store.select(new PersonScope(
                                null, List.of(new PersonIdentity(8, "different-person", null)), List.of()))
                        .rows())
                .isEmpty();
    }
}
