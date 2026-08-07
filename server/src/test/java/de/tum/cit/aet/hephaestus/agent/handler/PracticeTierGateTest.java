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
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
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
 * The in-context half of the loudness tier: which findings are allowed to land on the artifact, and what
 * is written down about the ones that are not.
 */
@DisplayName("Loudness tier, in-context channel")
class PracticeTierGateTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 4L;
    private static final UUID JOB_ID = UUID.randomUUID();

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private FeedbackLedgerRecorder feedbackLedgerRecorder;

    private PracticeTierGate gate() {
        return new PracticeTierGate(practiceRepository, observationRepository, feedbackLedgerRecorder);
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
        verify(feedbackLedgerRecorder, org.mockito.Mockito.times(2)).recordTierWithheld(any(), any(), anyInt());
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
        verify(feedbackLedgerRecorder, never()).recordTierWithheld(any(), any(), anyInt());
    }

    @Test
    void aWithheldFindingThatWasNeverPersistedGetsNoLedgerRow() {
        when(practiceRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(
            List.of(practice("measured", PracticeReviewTier.MEASURE))
        );
        when(observationRepository.findByAgentJobId(JOB_ID)).thenReturn(List.of());

        assertThat(gate().admitInContext(job(), List.of(finding("measured", null)))).isEmpty();
        verify(feedbackLedgerRecorder, never()).recordTierWithheld(any(), any(), anyInt());
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
            .recordTierWithheld(any(), any(), eq(0));
        ValidatedFinding loud = finding("loud", "occ-2");

        assertThat(gate().admitInContext(job(), List.of(finding("measured", "occ-1"), loud))).containsExactly(loud);
    }

    @Test
    void aJobWithoutAWorkspaceIsLeftAlone() {
        AgentJob job = new AgentJob();
        job.setId(JOB_ID);
        ValidatedFinding finding = finding("anything", "occ-1");

        assertThat(gate().admitInContext(job, List.of(finding))).containsExactly(finding);
        verifyNoInteractions(practiceRepository);
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
