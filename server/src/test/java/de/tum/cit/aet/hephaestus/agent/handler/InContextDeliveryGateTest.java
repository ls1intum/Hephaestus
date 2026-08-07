package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * The in-context delivery predicate: which findings are allowed to land on the artifact, and what is
 * written down about the ones that are not. Two rules apply — the practice's loudness tier and the run's
 * provenance — and this holds both to their reasons.
 */
@DisplayName("In-context delivery admission")
class InContextDeliveryGateTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 4L;
    private static final UUID JOB_ID = UUID.randomUUID();

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private FeedbackLedgerRecorder feedbackLedgerRecorder;

    private InContextDeliveryGate gate() {
        return new InContextDeliveryGate(practiceRepository, observationRepository, feedbackLedgerRecorder);
    }

    @Test
    void engagedPracticesReachTheArtifact() {
        when(practiceRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(
            List.of(practice("loud", PracticeReviewTier.ENGAGE))
        );
        ValidatedFinding finding = finding("loud", "occ-1");

        assertThat(gate().admitInContext(job(), List.of(finding))).containsExactly(finding);
        verifyNoInteractions(feedbackLedgerRecorder);
    }

    @Test
    void measuringAndCoachingPracticesAreWithheldFromTheArtifact() {
        when(practiceRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(
            List.of(
                practice("measured", PracticeReviewTier.MEASURE),
                practice("coached", PracticeReviewTier.COACH),
                practice("loud", PracticeReviewTier.ENGAGE)
            )
        );
        ValidatedFinding measured = finding("measured", "occ-1");
        ValidatedFinding coached = finding("coached", "occ-2");
        ValidatedFinding loud = finding("loud", "occ-3");
        // Built BEFORE the stubbing call: mocking inside a when(...) argument leaves Mockito's stubbing
        // half-finished and fails the next interaction instead of this line.
        List<Observation> persisted = List.of(observation("occ-1"), observation("occ-2"), observation("occ-3"));
        when(observationRepository.findByAgentJobId(JOB_ID)).thenReturn(persisted);

        List<ValidatedFinding> admitted = gate().admitInContext(job(), List.of(measured, coached, loud));

        assertThat(admitted).containsExactly(loud);
        // Written down, not dropped: a later evaluation must be able to tell a deliberate quiet from a miss.
        verify(feedbackLedgerRecorder, org.mockito.Mockito.times(2)).recordWithheld(any(), any(), any(), anyInt());
    }

    /**
     * A slug the workspace catalogue does not contain was never persisted as an observation either, so
     * there is no tier to consult; withholding it would silently drop feedback on a lookup miss.
     */
    @Test
    void anUnknownPracticeSlugIsKept() {
        when(practiceRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(
            List.of(practice("known", PracticeReviewTier.MEASURE))
        );
        ValidatedFinding stranger = finding("not-in-the-catalogue", "occ-9");

        assertThat(gate().admitInContext(job(), List.of(stranger))).containsExactly(stranger);
        verify(feedbackLedgerRecorder, never()).recordWithheld(any(), any(), any(), anyInt());
    }

    @Test
    void aWithheldFindingThatWasNeverPersistedGetsNoLedgerRow() {
        when(practiceRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(
            List.of(practice("measured", PracticeReviewTier.MEASURE))
        );
        when(observationRepository.findByAgentJobId(JOB_ID)).thenReturn(List.of());

        assertThat(gate().admitInContext(job(), List.of(finding("measured", null)))).isEmpty();
        verify(feedbackLedgerRecorder, never()).recordWithheld(any(), any(), any(), anyInt());
    }

    /** A ledger failure is telemetry loss, never delivery loss: the surviving findings still go out. */
    @Test
    void aLedgerFailureDoesNotStopTheFindingsThatSurvived() {
        when(practiceRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(
            List.of(practice("measured", PracticeReviewTier.MEASURE), practice("loud", PracticeReviewTier.ENGAGE))
        );
        List<Observation> persisted = List.of(observation("occ-1"));
        when(observationRepository.findByAgentJobId(JOB_ID)).thenReturn(persisted);
        doThrow(new IllegalStateException("ledger down"))
            .when(feedbackLedgerRecorder)
            .recordWithheld(any(), any(), any(), eq(0));
        ValidatedFinding loud = finding("loud", "occ-2");

        assertThat(gate().admitInContext(job(), List.of(finding("measured", "occ-1"), loud))).containsExactly(loud);
    }

    /**
     * A campaign's findings never reach the artifact. Posting one would comment on a pull request merged
     * months ago and notify everyone still subscribed to it about work nobody can act on.
     */
    @Test
    void aBackfilledRunSaysNothingOnTheArtifactWhateverTheTierIs() {
        List<Observation> persisted = List.of(observation("occ-1"), observation("occ-2"));
        when(observationRepository.findByAgentJobId(JOB_ID)).thenReturn(persisted);
        ValidatedFinding loud = finding("loud", "occ-1");
        ValidatedFinding alsoLoud = finding("also-loud", "occ-2");

        assertThat(gate().admitInContext(backfillJob(), List.of(loud, alsoLoud))).isEmpty();
        // Never even asks for the tiers: no dial can make a retrospective finding actionable in place.
        verifyNoInteractions(practiceRepository);
        verify(feedbackLedgerRecorder, org.mockito.Mockito.times(2)).recordWithheld(
            any(),
            any(),
            eq(FeedbackSuppressionReason.BACKFILL_QUIET),
            anyInt()
        );
    }

    /** The two withholding rules are separately answerable, which is why they are separately recorded. */
    @Test
    void aTierWithheldFindingIsRecordedUnderTheTierNotTheProvenance() {
        when(practiceRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(
            List.of(practice("measured", PracticeReviewTier.MEASURE))
        );
        // Built BEFORE the stubbing call: see measuringAndCoachingPracticesAreWithheldFromTheArtifact.
        List<Observation> persisted = List.of(observation("occ-1"));
        when(observationRepository.findByAgentJobId(JOB_ID)).thenReturn(persisted);

        assertThat(gate().admitInContext(job(), List.of(finding("measured", "occ-1")))).isEmpty();
        verify(feedbackLedgerRecorder).recordWithheld(
            any(),
            any(),
            eq(FeedbackSuppressionReason.PRACTICE_TIER_QUIET),
            eq(0)
        );
    }

    @Test
    void aJobWithoutAWorkspaceIsLeftAlone() {
        AgentJob job = new AgentJob();
        job.setId(JOB_ID);
        ValidatedFinding finding = finding("anything", "occ-1");

        assertThat(gate().admitInContext(job, List.of(finding))).containsExactly(finding);
        verifyNoInteractions(practiceRepository);
    }

    /** A job stamped the way {@code ReviewBackfillSubmitter} stamps one. */
    private AgentJob backfillJob() {
        AgentJob job = job();
        job.setMetadata(
            new tools.jackson.databind.ObjectMapper()
                .createObjectNode()
                .put(PracticeDetectionDeliveryService.ORIGIN_METADATA_KEY, ObservationOrigin.BACKFILL.name())
        );
        return job;
    }

    private AgentJob job() {
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        AgentJob job = new AgentJob();
        job.setId(JOB_ID);
        job.setWorkspace(workspace);
        return job;
    }

    private Practice practice(String slug, PracticeReviewTier tier) {
        Practice practice = new Practice();
        practice.setSlug(slug);
        practice.setReviewTier(tier);
        return practice;
    }

    private ValidatedFinding finding(String slug, String occurrenceKey) {
        return new ValidatedFinding(
            slug,
            "title",
            Presence.ABSENT,
            Assessment.BAD,
            Severity.MAJOR,
            0.9f,
            null,
            "reasoning",
            "guidance",
            List.of(),
            occurrenceKey == null ? null : new ObservationKeys(occurrenceKey, "rk-" + occurrenceKey)
        );
    }

    private Observation observation(String occurrenceKey) {
        Observation observation = org.mockito.Mockito.mock(Observation.class);
        org.mockito.Mockito.lenient().when(observation.getOccurrenceKey()).thenReturn(occurrenceKey);
        return observation;
    }
}
