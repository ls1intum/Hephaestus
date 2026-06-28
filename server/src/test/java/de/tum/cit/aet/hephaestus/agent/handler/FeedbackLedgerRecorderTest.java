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
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.core.spi.FindingAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacement;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementType;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
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
    private ObservationRepository observationRepository;

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private FeedbackObservationRepository feedbackObservationRepository;

    @Mock
    private FeedbackPlacementRepository feedbackPlacementRepository;

    private FeedbackLedgerRecorder recorder(boolean policyFloor) {
        when(feedbackRepository.existsByAgentJobIdAndPosition(any(), anyInt())).thenReturn(false);
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(feedbackRepository.findFirstByThreadKeyAndDeliveryStateOrderByCreatedAtDesc(any(), any())).thenReturn(
            Optional.empty()
        );
        when(feedbackObservationRepository.findObservationIdsSuppressedForJob(any())).thenReturn(List.of());
        when(feedbackPlacementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new FeedbackLedgerRecorder(
            observationRepository,
            feedbackRepository,
            feedbackObservationRepository,
            feedbackPlacementRepository,
            new PracticeReviewProperties(false, true, false, "", 15, false, false, policyFloor)
        );
    }

    @Test
    void policyFloorOn_bindsEachFindingExactlyOnce_keptToDeliveredDroppedToSuppressed() {
        // 5 MINOR problems with the cap at 3 → 3 kept (DELIVERED), 2 dropped (each its own SUPPRESSED unit).
        // The bug this guards: record() used to bind the dropped findings to BOTH units (double-count).
        List<Observation> findings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            findings.add(problem(0.9f - i * 0.1f)); // distinct confidences so the cap is deterministic
        }
        when(observationRepository.findByAgentJobId(any())).thenReturn(findings);

        recorder(true).record(job(), new DeliveryContent("body", List.of()), WorkArtifact.PULL_REQUEST, List.of());

        // Every finding bound exactly once across ALL units (3 to DELIVERED + 1 each to the 2 SUPPRESSED units).
        var boundFindingIds = ArgumentCaptor.forClass(UUID.class);
        verify(feedbackObservationRepository, org.mockito.Mockito.times(5)).insertIfAbsent(
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
            .filter(f -> f.getDeliveryState() == FeedbackDeliveryState.SUPPRESSED)
            .filter(f -> f.getSuppressionReason() == FeedbackSuppressionReason.POLICY_FLOOR_DROP)
            .count();
        assertThat(suppressed).isEqualTo(2);
    }

    @Test
    void policyFloorOff_bindsAllProblems_noSuppressedUnits() {
        List<Observation> findings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            findings.add(problem(0.9f - i * 0.1f));
        }
        when(observationRepository.findByAgentJobId(any())).thenReturn(findings);

        recorder(false).record(job(), new DeliveryContent("body", List.of()), WorkArtifact.PULL_REQUEST, List.of());

        verify(feedbackObservationRepository, org.mockito.Mockito.times(5)).insertIfAbsent(
            any(),
            any(),
            any(),
            anyInt()
        );
        var saved = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(saved.capture());
        assertThat(saved.getValue().getDeliveryState()).isEqualTo(FeedbackDeliveryState.DELIVERED);
    }

    @Test
    void inlinePlacement_persistsExternalRefFromMatchingSignal() {
        // A3: the INLINE placement must carry the durable vendor handle the channel reported, not a hardcoded
        // null. The note and its DeliveredSignal share a findingFingerprint, so the signal's externalRef lands
        // on the saved FeedbackPlacement. A no-op (the old hardcoded null) fails this.
        var finding = problem(0.9f);
        when(observationRepository.findByAgentJobId(any())).thenReturn(List.of(finding));

        var note = new DiffNote("src/Foo.java", 10, null, "Fix this", "ck-foo-10");
        var signal = new InlineFindingChannel.DeliveredSignal(
            "ck-foo-10",
            new FindingAnchor.DiffAnchor("src/Foo.java", 10, null),
            InlineFindingChannel.Disposition.POSTED,
            "note-gid-42",
            "discussion-gid-7"
        );

        recorder(false).record(
            job(),
            new DeliveryContent("body", List.of(note)),
            WorkArtifact.PULL_REQUEST,
            List.of(signal)
        );

        var placements = ArgumentCaptor.forClass(FeedbackPlacement.class);
        verify(feedbackPlacementRepository, org.mockito.Mockito.atLeastOnce()).save(placements.capture());
        FeedbackPlacement inline = placements
            .getAllValues()
            .stream()
            .filter(p -> p.getPlacementType() == PlacementType.INLINE)
            .findFirst()
            .orElseThrow();
        assertThat(inline.getPostedCommentRef()).isEqualTo("note-gid-42");
    }

    @Test
    void inlinePlacement_fallsBackToPathLineWhenNoObservationFingerprint_andFailedSignalHasNoRef() {
        // No findingFingerprint on the note (legacy/unkeyed) → match by path + terminal line. A FAILED disposition
        // leaves no external_ref, so a dead delivery is not recorded with a vendor handle.
        var finding = problem(0.9f);
        when(observationRepository.findByAgentJobId(any())).thenReturn(List.of(finding));

        var note = new DiffNote("src/Bar.java", 5, 8, "Range note"); // multi-line, no key
        var signal = new InlineFindingChannel.DeliveredSignal(
            null,
            new FindingAnchor.DiffAnchor("src/Bar.java", 8, 5), // newLineNumber=8 is the terminal (end) line
            InlineFindingChannel.Disposition.FAILED,
            null,
            null
        );

        recorder(false).record(
            job(),
            new DeliveryContent("body", List.of(note)),
            WorkArtifact.PULL_REQUEST,
            List.of(signal)
        );

        var placements = ArgumentCaptor.forClass(FeedbackPlacement.class);
        verify(feedbackPlacementRepository, org.mockito.Mockito.atLeastOnce()).save(placements.capture());
        FeedbackPlacement inline = placements
            .getAllValues()
            .stream()
            .filter(p -> p.getPlacementType() == PlacementType.INLINE)
            .findFirst()
            .orElseThrow();
        assertThat(inline.getPostedCommentRef()).isNull();
    }

    @Test
    void b2AndPolicyFloorBothOn_aSuppressedFindingIsNeverBoundTwice() {
        // B2 × C3 interaction: a finding B2 already suppressed must NOT also be written as a POLICY_FLOOR_DROP
        // unit — it does not re-enter the policy-dropped tail, and is bound exactly once across all units.
        List<Observation> findings = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            findings.add(problem(0.9f - i * 0.1f));
        }
        UUID b2Id = findings.get(5).getId(); // the lowest-confidence one — would otherwise be in the dropped tail
        when(observationRepository.findByAgentJobId(any())).thenReturn(findings);
        var recorder = recorder(true); // policyFloor ON
        when(feedbackObservationRepository.findObservationIdsSuppressedForJob(any())).thenReturn(List.of(b2Id));

        recorder.record(job(), new DeliveryContent("body", List.of()), WorkArtifact.PULL_REQUEST, List.of());

        var bound = ArgumentCaptor.forClass(UUID.class);
        verify(feedbackObservationRepository, org.mockito.Mockito.atLeastOnce()).insertIfAbsent(
            any(),
            bound.capture(),
            any(),
            anyInt()
        );
        assertThat(bound.getAllValues()).doesNotHaveDuplicates().doesNotContain(b2Id);
    }

    @Test
    void alreadySuppressedFinding_isExcludedFromDeliveredUnit() {
        // A finding withheld earlier in the flow (B2 reaction suppression wrote a SUPPRESSED unit for it) must
        // NOT also be bound to the DELIVERED unit — else it is double-counted as delivered.
        var kept = problem(0.9f);
        var b2Suppressed = problem(0.8f);
        UUID keptId = kept.getId();
        UUID b2Id = b2Suppressed.getId();
        when(observationRepository.findByAgentJobId(any())).thenReturn(List.of(kept, b2Suppressed));
        var recorder = recorder(false);
        when(feedbackObservationRepository.findObservationIdsSuppressedForJob(any())).thenReturn(List.of(b2Id));

        recorder.record(job(), new DeliveryContent("body", List.of()), WorkArtifact.PULL_REQUEST, List.of());

        var bound = ArgumentCaptor.forClass(UUID.class);
        verify(feedbackObservationRepository).insertIfAbsent(any(), bound.capture(), any(), anyInt());
        assertThat(bound.getAllValues()).containsExactly(keptId);
    }

    @Test
    void everySavedFeedback_isReSourcedToTheObservationSubject_recipientEqualsAbout() {
        // The delivery firewall: the recorder must re-source both recipient AND subject from the
        // observation's about_user_id (7L here), never from some other field. This pins that the saved
        // Feedback always satisfies recipientUserId == aboutUserId == observation.aboutUserId.
        List<Observation> findings = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            findings.add(problem(0.9f - i * 0.1f));
        }
        when(observationRepository.findByAgentJobId(any())).thenReturn(findings);

        recorder(false).record(job(), new DeliveryContent("body", List.of()), WorkArtifact.PULL_REQUEST, List.of());

        var saved = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues())
            .isNotEmpty()
            .allSatisfy(f -> {
                assertThat(f.getRecipientUserId()).isEqualTo(7L);
                assertThat(f.getAboutUserId()).isEqualTo(f.getRecipientUserId());
            });
    }

    private AgentJob job() {
        AgentJob job = TestEntities.agentJob();
        job.setWorkspace(TestEntities.workspace(1L));
        return job;
    }

    private Observation problem(float confidence) {
        Observation pf = mock(Observation.class);
        lenient().when(pf.getId()).thenReturn(UUID.randomUUID());
        lenient().when(pf.getPresence()).thenReturn(Presence.ABSENT);
        lenient().when(pf.getAssessment()).thenReturn(Assessment.BAD);
        lenient().when(pf.getSeverity()).thenReturn(Severity.MINOR);
        lenient().when(pf.getConfidence()).thenReturn(confidence);
        lenient().when(pf.getArtifactType()).thenReturn(WorkArtifact.PULL_REQUEST);
        lenient().when(pf.getArtifactId()).thenReturn(100L);
        // about_user_id is the recipient the recorder binds feedback to (formerly the dropped developer, id 7).
        lenient().when(pf.getAboutUserId()).thenReturn(7L);
        return pf;
    }
}
