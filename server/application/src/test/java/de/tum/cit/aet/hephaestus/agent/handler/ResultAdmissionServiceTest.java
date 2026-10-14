package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewHistoryContentSource;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedback;
import de.tum.cit.aet.hephaestus.agent.job.ReviewCompositionBasisRepository;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobAssignments;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmissionRequestDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.PrepareFeedbackRequestDTO;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedResultQuery;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedResultSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStore;
import de.tum.cit.aet.hephaestus.practices.spi.PreparedFeedback;
import de.tum.cit.aet.hephaestus.practices.spi.PreparedPackage;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCandidate;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

class ResultAdmissionServiceTest extends BaseUnitTest {
    private final JsonMapper mapper = new JsonMapper();
    private final ReviewJobRepository jobs = mock(ReviewJobRepository.class);
    private final ReviewOutputService output = mock(ReviewOutputService.class);
    private final AdmittedResultQuery proof = mock(AdmittedResultQuery.class);
    private final ReviewJobAssignments assignments = mock(ReviewJobAssignments.class);
    private final PublicReviewEligibility publicEligibility = mock(PublicReviewEligibility.class);
    private final JobEvidenceFiles files = mock(JobEvidenceFiles.class);
    private final FeedbackStore feedback = mock(FeedbackStore.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final ReviewJob job = RuntimeReviewTestData.job();
    private ResultAdmissionService service;

    @BeforeEach
    void setup() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ResultAdmissionService(jobs,
output,
proof,
assignments,
publicEligibility,
files,
feedback,
mock(ReviewHistoryContentSource.class),
mapper,
manager,
mock(ReviewCompositionBasisRepository.class),
org.mockito.Mockito.mock(de.tum.cit.aet.hephaestus.agent.handler.FeedbackCompositionContextReader.class));
        job.setStatus(ReviewJobStatus.RUNNING);
        job.setWorkerId("worker");
        job.setCapture(mapper.createObjectNode());
        when(jobs.findByIdAndWorkspaceIdForUpdate(job.getId(), 1)).thenReturn(Optional.of(job));
    }

    private ResultAdmissionService.AdmissionIdentity identity() {
        return new ResultAdmissionService.AdmissionIdentity(job.getId(), 1, 0, "worker");
    }

    @Test
    void shouldPrepareOneAtomicPackageAndReturnItsCanonicalReceipt() {
        job.setAdmissionAttempt(0);
        job.setAdmissionDigest("admitted");
        var assignment = RuntimeReviewTestData.assignment(5L, null);
        when(assignments.review(job)).thenReturn(RuntimeReviewTestData.review(ReviewKind.ITEM, assignment));
        UUID resultId = UUID.randomUUID();
        var candidate = mock(ResultCandidate.class);
        when(candidate.developerId()).thenReturn(5L);
        when(proof.forAttempt(1, job.getReviewId(), job.getId(), 0))
                .thenReturn(List.of(new AdmittedResultSnapshot(resultId, candidate, List.of())));
        UUID feedbackId = UUID.randomUUID();
        Instant preparedAt = Instant.parse("2026-10-14T06:00:00Z");
        when(feedback.prepareAll(eq(1L), eq(job.getReviewId()), any()))
                .thenReturn(new PreparedPackage(
                        "package-digest",
                        preparedAt,
                        List.of(new PreparedFeedback(feedbackId, "piece-digest", preparedAt))));
        var request = new PrepareFeedbackRequestDTO(
                job.getReviewId(),
                job.getId(),
                0,
                List.of(resultId),
                List.of(new ComposedFeedback(
                        FeedbackDestination.ON_WORK, List.of(resultId), "Use clear names.", List.of(), null)));
        var response = service.prepareFeedback(identity(), request);
        assertThat(response.feedbackIds()).containsExactly(feedbackId);
        assertThat(response.contentDigest()).isEqualTo("package-digest");
        assertThat(response.preparedAt()).isEqualTo(preparedAt);
        verify(feedback).prepareAll(eq(1L), eq(job.getReviewId()), any());
    }

    @Test
    void shouldRejectAnOldWorkerAttemptBeforePreparingAnything() {
        var stale = new ResultAdmissionService.AdmissionIdentity(job.getId(), 1, 1, "worker");
        assertThatThrownBy(() -> service.prepareFeedback(
                        stale, new PrepareFeedbackRequestDTO(job.getReviewId(), job.getId(), 1, List.of(), List.of())))
                .isInstanceOf(ResultAdmissionService.StaleAttemptException.class);
        verifyNoInteractions(feedback, proof);
    }

    @Test
    void shouldKeepAdmissionIdempotentAndRejectChangedPayload() {
        var assignment = RuntimeReviewTestData.assignment(5L, null);
        when(assignments.review(job)).thenReturn(RuntimeReviewTestData.review(ReviewKind.ITEM, assignment));
        when(assignments.load(job)).thenReturn(List.of(assignment));
        when(proof.forAttempt(1, job.getReviewId(), job.getId(), 0)).thenReturn(List.of());
        when(publicEligibility.publicResultIds(job, List.of())).thenReturn(Set.of());
        var request = new AdmissionRequestDTO(3, job.getReviewId(), job.getId(), 0, List.of());
        var writes = new AtomicInteger();
        when(output.prepare(job, request)).thenReturn(owned -> writes.incrementAndGet());
        service.admit(identity(), request);
        service.admit(identity(), request);
        assertThat(writes.get()).isEqualTo(1);
        job.setAdmissionDigest("another-payload");
        assertThatThrownBy(() -> service.admit(identity(), request))
                .isInstanceOf(ResultAdmissionService.AdmissionConflictException.class);
    }
}
