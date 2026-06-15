package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackFindingRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/** Unit tests for the delivered-feedback ledger writer (ADR 0021 C6 + C3 policy-floor binding). */
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class FeedbackLedgerRecorderTest extends BaseUnitTest {

    @Mock
    private PracticeFindingRepository practiceFindingRepository;

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private FeedbackFindingRepository feedbackFindingRepository;

    @Mock
    private FeedbackPlacementRepository feedbackPlacementRepository;

    private FeedbackLedgerRecorder recorder(boolean policyFloor) {
        when(feedbackRepository.existsByAgentJobIdAndUnitOrdinal(any(), anyInt())).thenReturn(false);
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(feedbackRepository.findFirstByContinuityKeyAndStateOrderByCreatedAtDesc(any(), any())).thenReturn(
            Optional.empty()
        );
        when(feedbackFindingRepository.findFindingIdsSuppressedForJob(any())).thenReturn(List.of());
        when(feedbackPlacementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new FeedbackLedgerRecorder(
            practiceFindingRepository,
            feedbackRepository,
            feedbackFindingRepository,
            feedbackPlacementRepository,
            new PracticeReviewProperties(false, true, false, "", 15, false, false, policyFloor)
        );
    }

    @Test
    void policyFloorOn_bindsEachFindingExactlyOnce_keptToDeliveredDroppedToSuppressed() {
        // 5 MINOR problems with the cap at 3 → 3 kept (DELIVERED), 2 dropped (each its own SUPPRESSED unit).
        // The bug this guards: record() used to bind the dropped findings to BOTH units (double-count).
        List<PracticeFinding> findings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            findings.add(problem(0.9f - i * 0.1f)); // distinct confidences so the cap is deterministic
        }
        when(practiceFindingRepository.findByAgentJobId(any())).thenReturn(findings);

        recorder(true).record(job(), new DeliveryContent("body", List.of()), WorkArtifact.PULL_REQUEST);

        // Every finding bound exactly once across ALL units (3 to DELIVERED + 1 each to the 2 SUPPRESSED units).
        var boundFindingIds = ArgumentCaptor.forClass(UUID.class);
        verify(feedbackFindingRepository, org.mockito.Mockito.times(5)).insertIfAbsent(
            any(),
            boundFindingIds.capture(),
            any(),
            anyInt()
        );
        assertThat(boundFindingIds.getAllValues()).doesNotHaveDuplicates().hasSize(5);

        // Two SUPPRESSED / POLICY_FLOOR_DROP units were written for the dropped tail.
        var saved = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository, org.mockito.Mockito.atLeast(3)).save(saved.capture());
        long suppressed = saved
            .getAllValues()
            .stream()
            .filter(f -> f.getState() == FeedbackState.SUPPRESSED)
            .filter(f -> f.getSuppressionReason() == FeedbackSuppressionReason.POLICY_FLOOR_DROP)
            .count();
        assertThat(suppressed).isEqualTo(2);
    }

    @Test
    void policyFloorOff_bindsAllProblems_noSuppressedUnits() {
        List<PracticeFinding> findings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            findings.add(problem(0.9f - i * 0.1f));
        }
        when(practiceFindingRepository.findByAgentJobId(any())).thenReturn(findings);

        recorder(false).record(job(), new DeliveryContent("body", List.of()), WorkArtifact.PULL_REQUEST);

        verify(feedbackFindingRepository, org.mockito.Mockito.times(5)).insertIfAbsent(any(), any(), any(), anyInt());
        var saved = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(saved.capture());
        assertThat(saved.getValue().getState()).isEqualTo(FeedbackState.DELIVERED);
    }

    @Test
    void alreadySuppressedFinding_isExcludedFromDeliveredUnit() {
        // A finding withheld earlier in the flow (B2 reaction suppression wrote a SUPPRESSED unit for it) must
        // NOT also be bound to the DELIVERED unit — else it is double-counted as delivered.
        var kept = problem(0.9f);
        var b2Suppressed = problem(0.8f);
        UUID keptId = kept.getId();
        UUID b2Id = b2Suppressed.getId();
        when(practiceFindingRepository.findByAgentJobId(any())).thenReturn(List.of(kept, b2Suppressed));
        var recorder = recorder(false);
        when(feedbackFindingRepository.findFindingIdsSuppressedForJob(any())).thenReturn(List.of(b2Id));

        recorder.record(job(), new DeliveryContent("body", List.of()), WorkArtifact.PULL_REQUEST);

        var bound = ArgumentCaptor.forClass(UUID.class);
        verify(feedbackFindingRepository).insertIfAbsent(any(), bound.capture(), any(), anyInt());
        assertThat(bound.getAllValues()).containsExactly(keptId);
    }

    private AgentJob job() {
        AgentJob job = TestEntities.agentJob();
        job.setWorkspace(TestEntities.workspace(1L));
        return job;
    }

    private PracticeFinding problem(float confidence) {
        PracticeFinding pf = mock(PracticeFinding.class);
        User contributor = new User();
        contributor.setId(7L);
        lenient().when(pf.getId()).thenReturn(UUID.randomUUID());
        lenient().when(pf.getVerdict()).thenReturn(Verdict.NOT_OBSERVED);
        lenient().when(pf.getSeverity()).thenReturn(Severity.MINOR);
        lenient().when(pf.getConfidence()).thenReturn(confidence);
        lenient().when(pf.getContributor()).thenReturn(contributor);
        lenient().when(pf.getTargetType()).thenReturn(WorkArtifact.PULL_REQUEST);
        lenient().when(pf.getTargetId()).thenReturn(100L);
        lenient().when(pf.getSubjectUserId()).thenReturn(null);
        return pf;
    }
}
