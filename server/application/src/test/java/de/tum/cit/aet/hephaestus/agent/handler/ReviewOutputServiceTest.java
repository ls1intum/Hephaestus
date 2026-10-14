package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ResultsRefusedException;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobAssignments;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmissionRequestDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.SubmittedResultDTO;
import de.tum.cit.aet.hephaestus.evidence.ClasspathSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCapture;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.spi.AdmissionReceipt;
import de.tum.cit.aet.hephaestus.practices.spi.CandidateRefusal;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCandidate;
import de.tum.cit.aet.hephaestus.practices.spi.ResultStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ReviewOutputServiceTest extends BaseUnitTest {
    private final JsonMapper mapper = new JsonMapper();
    private final ReviewJobAssignments assignments = mock(ReviewJobAssignments.class);
    private final CandidateEvidenceVerifier verifier = mock(CandidateEvidenceVerifier.class);
    private final ResultStore results = mock(ResultStore.class);
    private final ReviewAssignmentStore membership = mock(ReviewAssignmentStore.class);
    private final JobEvidenceFiles files = mock(JobEvidenceFiles.class);
    private final ClasspathSourceCatalogRegistry catalogs = mock(ClasspathSourceCatalogRegistry.class);
    private final ReviewJob job = RuntimeReviewTestData.job();
    private final ReviewOutputService service =
            new ReviewOutputService(assignments, verifier, results, mapper, files, catalogs, membership);

    @BeforeEach
    void setup() {
        var assigned = RuntimeReviewTestData.assignment(5L, null);
        when(assignments.admissible(job)).thenReturn(List.of(assigned));
        when(assignments.review(job)).thenReturn(RuntimeReviewTestData.review(ReviewKind.ITEM, assigned));
        var manifest = new JobFolderIndex(
                new SourceContractVersion("2.0.0"),
                "a".repeat(64),
                "docs.document",
                Instant.EPOCH,
                List.of(new SourceCapture(
                        new SourceKind("docs.document.core"),
                        new SourceCaptureState.NotCollected(SourceAbsenceReason.GOVERNANCE_NOT_EFFECTIVE),
                        List.of())),
                List.of(),
                List.of(),
                List.of());
        var capture = mapper.createObjectNode();
        capture.set("manifest", mapper.valueToTree(manifest));
        job.setCapture(capture);
        job.setWorkerId("worker");
        when(files.registerReadReceipts(eq(job), any(), eq(List.of()), eq(catalogs)))
                .thenReturn(manifest);
    }

    private AdmissionRequestDTO submission(String slug, String version) {
        var candidate = mapper.readTree("""
            {"outcome":"MET","summary":"Clear names","explanation":"The names explain the document.",
             "citations":[{"sourceKind":"docs.document.core","sourcePath":"context/body.txt","path":"document/body","startLine":1,"endLine":1,"quote":"Clear names"}],
             "evidence":{},"coverage":{"source":"claimed","selected":1,"checked":1,"total":1,"complete":true}}
            """);
        return new AdmissionRequestDTO(
                3,
                job.getReviewId(),
                job.getId(),
                0,
                List.of(new SubmittedResultDTO(slug, "2", version, candidate, List.of())));
    }

    @Test
    void shouldWithholdAnUnverifiedPracticeThroughTheOneMembershipPort() {
        doThrow(new CandidateEvidenceVerifier.Unverified(
                        CandidateRefusal.QUOTE_UNVERIFIED, "The text was unavailable", null))
                .when(verifier)
                .verifyCandidate(eq(job), any(), eq(ReviewKind.ITEM), any(), any());
        when(results.admit(1, job.getReviewId(), job.getId(), 0, List.of()))
                .thenReturn(new AdmissionReceipt(List.of(), List.of()));
        var prepared = service.prepare(job, submission("clear-names", "docs:one"));
        verifyNoInteractions(membership, results);
        prepared.record(job);
        verify(membership)
                .withhold(
                        1,
                        RuntimeReviewTestData.assignment(5L, null).id(),
                        CandidateRefusal.QUOTE_UNVERIFIED,
                        "The text was unavailable");
        assertThat(Objects.requireNonNull(job.getCapture())
                        .path("withheld")
                        .get(0)
                        .path("reason")
                        .asString())
                .isEqualTo("quote_unverified");
    }

    @Test
    void shouldWithholdHostCoverageWhenItsReceiptsDifferFromTheSubmission() {
        var submitted = submission("clear-names", "docs:one");
        var entry = submitted.results().getFirst();
        var candidate = (ObjectNode) entry.candidate();
        candidate.set("coverage", mapper.readTree("""
                {"source":"host","complete":true,"reads":1,"truncatedReads":0,"failedReads":0,
                 "receipts":[{"sourceKind":"docs.document.core","sourcePath":"context/body.txt",
                 "path":"document/body","version":"docs:one","status":"COMPLETE"}]}
                """));
        when(results.admit(1, job.getReviewId(), job.getId(), 0, List.of()))
                .thenReturn(new AdmissionReceipt(List.of(), List.of()));
        service.prepare(job, submitted).record(job);
        verifyNoInteractions(verifier);
        verify(membership).withhold(1, RuntimeReviewTestData.assignment(5L, null).id(),
                CandidateRefusal.COVERAGE_INCONSISTENT, "Host coverage differs from the submitted read receipts");
    }

    @Test
    void shouldRejectAChangedItemVersionBeforeItCanProduceProof() {
        assertThatThrownBy(() -> service.prepare(job, submission("clear-names", "docs:other")))
                .isInstanceOf(ResultsRefusedException.class);
        verifyNoInteractions(results, membership, verifier);
    }

    @Test
    void shouldRejectAnUnassignedPractice() {
        assertThatThrownBy(() -> service.prepare(job, submission("other-practice", "docs:one")))
                .isInstanceOf(ResultsRefusedException.class);
        verifyNoInteractions(results, membership, verifier);
    }

    @Test
    void shouldKeepTheWorkerAttemptFenceAroundTheProductWrite() {
        when(verifier.verifyCandidate(eq(job), any(), eq(ReviewKind.ITEM), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(3));
        var prepared = service.prepare(job, submission("clear-names", "docs:one"));
        job.setAttempt(1);
        assertThatThrownBy(() -> prepared.record(job)).isInstanceOf(ResultAdmissionService.StaleAttemptException.class);
        verifyNoInteractions(results, membership);
    }

    @Test
    void shouldKeepAnAdmissiblePracticeWhenAnotherCandidateBreaksItsContract() {
        when(verifier.verifyCandidate(eq(job), any(), eq(ReviewKind.ITEM), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(3));
        var first = RuntimeReviewTestData.assignment(5L, null);
        var second = new ReviewAssignmentSnapshot(
                new ReviewAssignmentId(job.getReviewId(), 41, 2),
                first.item(),
                first.version(),
                first.developerId(),
                "other-practice",
                new PinnedPractice(
                        41,
                        42,
                        first.practice().definition(),
                        first.practice().reviewDigest(),
                        first.practice().method()));
        when(assignments.admissible(job)).thenReturn(List.of(first, second));
        when(results.admit(eq(1L), eq(job.getReviewId()), eq(job.getId()), eq(0), any()))
                .thenReturn(new AdmissionReceipt(List.of(), List.of()));
        var valid = submission("clear-names", "docs:one").results().getFirst();
        var malformed = new SubmittedResultDTO("other-practice", "2", "docs:one", mapper.createObjectNode(), List.of());
        var prepared = service.prepare(
                job, new AdmissionRequestDTO(3, job.getReviewId(), job.getId(), 0, List.of(valid, malformed)));
        prepared.record(job);
        ArgumentCaptor<List<ResultCandidate>> admitted = ArgumentCaptor.captor();
        verify(results).admit(eq(1L), eq(job.getReviewId()), eq(job.getId()), eq(0), admitted.capture());
        assertThat(admitted.getValue())
                .singleElement()
                .satisfies(result -> assertThat(result.practiceId()).isEqualTo(31));
        assertThat(Objects.requireNonNull(job.getCapture())
                        .path("withheld")
                        .get(0)
                        .path("practiceSlug")
                        .asString())
                .isEqualTo("other-practice");
        assertThat(Objects.requireNonNull(job.getCapture())
                        .path("withheld")
                        .get(0)
                        .path("reason")
                        .asString())
                .isEqualTo("malformed_candidate");
    }
}
