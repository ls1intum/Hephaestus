package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedResult;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.ObservationFingerprint;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.reaction.ReactionRepository;
import de.tum.cit.aet.hephaestus.practices.result.reaction.ReactionRepository.ResultResolutionProjection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** A developer's dispute or "not applicable" holds back feedback about the same claim. */
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedbackResponseSuppressionFilterTest extends BaseUnitTest {

    @Mock
    private PracticeResultRepository practiceResultRepository;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private FeedbackLedgerRecorder feedbackLedgerRecorder;

    /** What this review recorded so far, in the order the test adds it. */
    private List<PracticeResult> persistedSoFar = List.of();

    /** The problems of those results, in the same order. */
    private List<Observation> problemsSoFar = List.of();

    private static final String SLUG = "commit-discipline";
    private static final long CONTRIBUTOR = 7L;
    private static final long TARGET = 100L;
    private static final UUID THIS_REVIEW = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EARLIER_REVIEW = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant EARLIER = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-09-02T10:00:00Z");
    private static final String CK =
            ObservationFingerprint.compute(SLUG, ArtifactKinds.PULL_REQUEST.value(), TARGET, CONTRIBUTOR, null);
    private static final String ELSEWHERE = ObservationFingerprint.compute(
            SLUG, ArtifactKinds.PULL_REQUEST.value(), TARGET, CONTRIBUTOR, "src/Elsewhere.java");

    private FeedbackResponseSuppressionFilter filter() {
        return new FeedbackResponseSuppressionFilter(
                practiceResultRepository, observationRepository, reactionRepository, feedbackLedgerRecorder);
    }

    private AgentJob job() {
        AgentJob job = TestEntities.agentJob();
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        job.setWorkspace(workspace);
        return job;
    }

    @Test
    void shouldSuppressAndLedgerWhenTheFeedbackAboutThisResultWasDisputed() {
        PracticeResult practiceResult = persisted(CK, "occ-a", Outcome.NOT_MET);
        answers(answer(practiceResult.getId(), THIS_REVIEW, CK, Outcome.NOT_MET, FeedbackResolution.DISPUTED, LATER));

        var decision = filter().evaluate(job(), List.of(vf("occ-a", Outcome.NOT_MET)));

        assertThat(decision.deliverable()).isEmpty();
        assertThat(decision.suppressedCount()).isEqualTo(1);
        verify(feedbackLedgerRecorder)
                .recordSuppressed(any(), eq(practiceResult), eq(FeedbackSuppressionReason.REACTED_DISPUTED), anyInt());
    }

    @Test
    void shouldStillSuppressWhenTheLedgerWriteFails() {
        PracticeResult practiceResult = persisted(CK, "occ-a", Outcome.NOT_MET);
        answers(answer(
                practiceResult.getId(), THIS_REVIEW, CK, Outcome.NOT_MET, FeedbackResolution.NOT_APPLICABLE, LATER));
        doThrow(new RuntimeException("ledger down"))
                .when(feedbackLedgerRecorder)
                .recordSuppressed(any(), any(), any(), anyInt());
        var filter = filter();
        var job = job();
        var in = List.of(vf("occ-a", Outcome.NOT_MET));

        assertThatCode(() -> filter.evaluate(job, in)).doesNotThrowAnyException();
        assertThat(filter.evaluate(job, in).deliverable()).isEmpty();
    }

    @Test
    void shouldDeliverWhenNothingWasAnswered() {
        persisted(CK, "occ-a", Outcome.NOT_MET);
        answers();

        var decision = filter().evaluate(job(), List.of(vf("occ-a", Outcome.NOT_MET)));

        assertThat(decision.deliverable()).hasSize(1);
        assertThat(decision.suppressedCount()).isZero();
    }

    @Test
    void shouldDeliverWithItsEvidenceWhenTheDeveloperMarkedItAddressed() {
        PracticeResult practiceResult = persisted(CK, "occ-a", Outcome.NOT_MET);
        answers(answer(practiceResult.getId(), THIS_REVIEW, CK, Outcome.NOT_MET, FeedbackResolution.ADDRESSED, LATER));

        var decision = filter().evaluate(job(), List.of(vf("occ-a", Outcome.NOT_MET)));

        assertThat(decision.deliverable()).hasSize(1);
        assertThat(decision.deliverable().getFirst().evidenceRationale()).isEqualTo("because reasons");
    }

    @Test
    void shouldAskOnlyForTheExactResultWhenItRecordedNoPlace() {
        UUID practiceResultId = persisted(null, "occ-a", Outcome.NOT_MET).getId();
        answers();

        filter().evaluate(job(), List.of(vf("occ-a", Outcome.NOT_MET)));

        ArgumentCaptor<String[]> keys = ArgumentCaptor.forClass(String[].class);
        verify(reactionRepository).findCurrentResolutions(eq(1L), eq(List.of(practiceResultId)), keys.capture());
        assertThat(keys.getValue()).isEmpty();
    }

    /** Within one review two results may share a place and still be two behaviours. */
    @Test
    void shouldSuppressOnlyTheAnsweredResultWhenTwoInOneReviewShareAPlace() {
        PracticeResult first = persisted(CK, "occ-first", Outcome.NOT_MET);
        persisted(CK, "occ-second", Outcome.NOT_MET);
        answers(answer(first.getId(), THIS_REVIEW, CK, Outcome.NOT_MET, FeedbackResolution.DISPUTED, LATER));
        var otherBehaviour = vf("occ-second", Outcome.NOT_MET);

        var decision = filter().evaluate(job(), List.of(vf("occ-first", Outcome.NOT_MET), otherBehaviour));

        assertThat(decision.deliverable()).containsExactly(otherBehaviour);
        verify(feedbackLedgerRecorder)
                .recordSuppressed(any(), eq(first), eq(FeedbackSuppressionReason.REACTED_DISPUTED), anyInt());
    }

    @Test
    void shouldSuppressTheSameClaimWhenAnEarlierReviewOfTheWorkWasDisputed() {
        PracticeResult practiceResult = persisted(CK, "occ-a", Outcome.NOT_MET);
        answers(answer(UUID.randomUUID(), EARLIER_REVIEW, CK, Outcome.NOT_MET, FeedbackResolution.DISPUTED, EARLIER));

        var decision = filter().evaluate(job(), List.of(vf("occ-a", Outcome.NOT_MET)));

        assertThat(decision.deliverable()).isEmpty();
        verify(feedbackLedgerRecorder)
                .recordSuppressed(any(), eq(practiceResult), eq(FeedbackSuppressionReason.REACTED_DISPUTED), anyInt());
    }

    /** A dispute of "this is missing" says nothing about a later "this is present": a met result has no problem. */
    @Test
    void shouldDeliverADifferentClaimAtTheSamePlaceWhenAnEarlierOneWasDisputed() {
        persisted(null, "occ-a", Outcome.MET);
        answers(answer(UUID.randomUUID(), EARLIER_REVIEW, CK, Outcome.NOT_MET, FeedbackResolution.DISPUTED, EARLIER));

        var decision = filter().evaluate(job(), List.of(vf("occ-a", Outcome.MET)));

        assertThat(decision.deliverable()).hasSize(1);
    }

    @Test
    void shouldDeliverWhenANewerAnswerAboutTheClaimNoLongerDisputesIt() {
        persisted(CK, "occ-a", Outcome.NOT_MET);
        answers(
                answer(UUID.randomUUID(), EARLIER_REVIEW, CK, Outcome.NOT_MET, FeedbackResolution.DISPUTED, EARLIER),
                answer(UUID.randomUUID(), EARLIER_REVIEW, CK, Outcome.NOT_MET, FeedbackResolution.ADDRESSED, LATER));

        var decision = filter().evaluate(job(), List.of(vf("occ-a", Outcome.NOT_MET)));

        assertThat(decision.deliverable()).hasSize(1);
    }

    @Test
    void shouldDeliverADifferentStatementWithTheSameOutcomeAtTheSamePlace() {
        PracticeResult current = persisted(CK, "occ-a", Outcome.NOT_MET);
        when(current.getSummary()).thenReturn("The test does not cover the failure path");
        answers(answer(UUID.randomUUID(), EARLIER_REVIEW, CK, Outcome.NOT_MET, FeedbackResolution.DISPUTED, EARLIER));

        var decision = filter().evaluate(job(), List.of(vf("occ-a", Outcome.NOT_MET)));

        assertThat(decision.deliverable()).hasSize(1);
    }

    @Test
    void shouldSuppressAResultWhenAnEarlierReviewDisputedTheSameClaimAtOneOfItsProblems() {
        List<@Nullable String> recurrenceKeys = new ArrayList<>();
        recurrenceKeys.add(ELSEWHERE);
        recurrenceKeys.add(null);
        recurrenceKeys.add(CK);
        PracticeResult practiceResult = persisted("occ-a", Outcome.NOT_MET, recurrenceKeys);
        answers(answer(UUID.randomUUID(), EARLIER_REVIEW, CK, Outcome.NOT_MET, FeedbackResolution.DISPUTED, EARLIER));

        var decision = filter().evaluate(job(), List.of(vf("occ-a", Outcome.NOT_MET)));

        assertThat(decision.deliverable()).isEmpty();
        verify(feedbackLedgerRecorder)
                .recordSuppressed(any(), eq(practiceResult), eq(FeedbackSuppressionReason.REACTED_DISPUTED), anyInt());
        UUID practiceResultId = practiceResult.getId();
        ArgumentCaptor<String[]> keys = ArgumentCaptor.forClass(String[].class);
        verify(reactionRepository).findCurrentResolutions(eq(1L), eq(List.of(practiceResultId)), keys.capture());
        assertThat(keys.getValue()).containsExactlyInAnyOrder(ELSEWHERE, CK);
    }

    @Test
    void shouldDeliverAResultWhenAnEarlierDisputeRecursAtNoneOfItsProblems() {
        persisted(ELSEWHERE, "occ-a", Outcome.NOT_MET);
        answers(answer(UUID.randomUUID(), EARLIER_REVIEW, CK, Outcome.NOT_MET, FeedbackResolution.DISPUTED, EARLIER));

        var decision = filter().evaluate(job(), List.of(vf("occ-a", Outcome.NOT_MET)));

        assertThat(decision.deliverable()).hasSize(1);
        assertThat(decision.suppressedCount()).isZero();
    }

    /** Another result's problem recurring at the disputed place does not carry the dispute to this result. */
    @Test
    void shouldDeliverAResultWhenOnlyAnotherResultsProblemRecursAtTheDisputedPlace() {
        PracticeResult elsewhere = persisted(ELSEWHERE, "occ-a", Outcome.NOT_MET);
        PracticeResult disputed = persisted(CK, "occ-b", Outcome.NOT_MET);
        answers(answer(UUID.randomUUID(), EARLIER_REVIEW, CK, Outcome.NOT_MET, FeedbackResolution.DISPUTED, EARLIER));
        var stillOwed = vf("occ-a", Outcome.NOT_MET);

        var decision = filter().evaluate(job(), List.of(stillOwed, vf("occ-b", Outcome.NOT_MET)));

        assertThat(decision.deliverable()).containsExactly(stillOwed);
        verify(feedbackLedgerRecorder)
                .recordSuppressed(any(), eq(disputed), eq(FeedbackSuppressionReason.REACTED_DISPUTED), anyInt());
        verify(feedbackLedgerRecorder, never()).recordSuppressed(any(), eq(elsewhere), any(), anyInt());
    }

    /** A result of this review with one problem, which recurs at {@code recurrenceKey} when it is given. */
    private PracticeResult persisted(@Nullable String recurrenceKey, String occurrenceKey, Outcome outcome) {
        List<@Nullable String> problems = new ArrayList<>();
        if (outcome == Outcome.NOT_MET) problems.add(recurrenceKey);
        return persisted(occurrenceKey, outcome, problems);
    }

    /** A result of this review with one problem per entry, each recurring at its key when the entry is not null. */
    private PracticeResult persisted(String occurrenceKey, Outcome outcome, List<@Nullable String> recurrenceKeys) {
        PracticeResult practiceResult = mock(PracticeResult.class);
        List<Observation> problems = new ArrayList<>(problemsSoFar);
        for (int position = 0; position < recurrenceKeys.size(); position++) {
            problems.add(Observation.builder()
                    .id(UUID.randomUUID())
                    .workspaceId(1L)
                    .practiceResultId(id(occurrenceKey))
                    .position(position)
                    .severity(Severity.MINOR)
                    .summary("A problem of " + occurrenceKey)
                    .explanation("What this place shows.")
                    .recurrenceKey(recurrenceKeys.get(position))
                    .build());
        }
        problemsSoFar = problems;
        when(observationRepository.findByPracticeResultIds(anyLong(), any())).thenReturn(problems);
        lenient().when(practiceResult.getOccurrenceKey()).thenReturn(occurrenceKey);
        lenient().when(practiceResult.getId()).thenReturn(id(occurrenceKey));
        lenient().when(practiceResult.getAgentJobId()).thenReturn(THIS_REVIEW);
        lenient().when(practiceResult.getOutcome()).thenReturn(outcome);
        lenient().when(practiceResult.getSummary()).thenReturn(SLUG + " title");
        lenient().when(practiceResult.getAboutUserId()).thenReturn(CONTRIBUTOR);
        List<PracticeResult> all = new ArrayList<>(persistedSoFar);
        all.add(practiceResult);
        persistedSoFar = all;
        when(practiceResultRepository.findByAgentJobId(any(), anyLong())).thenReturn(all);
        return practiceResult;
    }

    private void answers(ResultResolutionProjection... rows) {
        when(reactionRepository.findCurrentResolutions(anyLong(), any(), any())).thenReturn(List.of(rows));
    }

    private static ResultResolutionProjection answer(
            UUID practiceResultId,
            UUID agentJobId,
            String recurrenceKey,
            Outcome outcome,
            FeedbackResolution resolution,
            Instant respondedAt) {
        var row = mock(ResultResolutionProjection.class);
        when(row.getPracticeResultId()).thenReturn(practiceResultId);
        when(row.getAgentJobId()).thenReturn(agentJobId);
        when(row.getRecurrenceKey()).thenReturn(recurrenceKey);
        when(row.getOutcome()).thenReturn(outcome.name());
        when(row.getSummary()).thenReturn(SLUG + " title");
        when(row.getResolution()).thenReturn(resolution.name());
        when(row.getRespondedAt()).thenReturn(respondedAt);
        return row;
    }

    private static UUID id(String occurrence) {
        return UUID.nameUUIDFromBytes(occurrence.getBytes(StandardCharsets.UTF_8));
    }

    private static ValidatedResult vf(String occurrenceKey, Outcome outcome) {
        return new ValidatedResult(
                SLUG,
                SLUG + " title",
                outcome,
                outcome == Outcome.NOT_MET ? Severity.MINOR : null,
                null,
                "because reasons",
                new ResultKeys(occurrenceKey));
    }
}
