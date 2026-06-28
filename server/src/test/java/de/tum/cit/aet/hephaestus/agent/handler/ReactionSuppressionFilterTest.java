package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationFingerprint;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.Reaction;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionAction;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionRepository;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/** Unit tests for reaction-aware re-nag suppression (ADR 0021, B2). */
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ReactionSuppressionFilterTest extends BaseUnitTest {

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private ReactionRepository findingReactionRepository;

    @Mock
    private FeedbackLedgerRecorder feedbackLedgerRecorder;

    private static final String SLUG = "commit-discipline";
    private static final long CONTRIBUTOR = 7L;
    private static final long TARGET = 100L;
    // The canonical key the filter recomputes for a SLUG finding with no location — the SAME value deliver() persists.
    private static final String CK = ObservationFingerprint.compute(
        SLUG,
        WorkArtifact.PULL_REQUEST.name(),
        TARGET,
        CONTRIBUTOR,
        null
    );

    private ReactionSuppressionFilter filter(boolean enabled) {
        return new ReactionSuppressionFilter(
            observationRepository,
            findingReactionRepository,
            feedbackLedgerRecorder,
            new PracticeReviewProperties(false, true, false, "", 15, false, enabled, false)
        );
    }

    @Test
    void flagOff_passesThroughUnchanged_noRepoCalls() {
        List<ValidatedFinding> in = List.of(vf(SLUG, Presence.ABSENT));

        var d = filter(false).evaluate(TestEntities.agentJob(), in);

        assertThat(d.deliverable()).isEqualTo(in);
        assertThat(d.suppressedCount()).isZero();
        verify(observationRepository, never()).findByAgentJobId(any());
    }

    @Test
    void disputedLocus_isSuppressedAndLedgered() {
        stubPersistedAndReaction(ReactionAction.DISPUTED);

        var d = filter(true).evaluate(TestEntities.agentJob(), List.of(vf(SLUG, Presence.ABSENT)));

        assertThat(d.deliverable()).isEmpty();
        assertThat(d.suppressedCount()).isEqualTo(1);
        verify(feedbackLedgerRecorder).recordSuppressed(
            any(),
            any(),
            eq(FeedbackSuppressionReason.REACTED_DISPUTED),
            anyInt()
        );
    }

    @Test
    void suppression_survivesLedgerWriteFailure() {
        stubPersistedAndReaction(ReactionAction.NOT_APPLICABLE);
        doThrow(new RuntimeException("ledger down"))
            .when(feedbackLedgerRecorder)
            .recordSuppressed(any(), any(), any(), anyInt());

        var filter = filter(true);
        var job = TestEntities.agentJob();
        var in = List.of(vf(SLUG, Presence.ABSENT));

        assertThatCode(() -> filter.evaluate(job, in)).doesNotThrowAnyException();
        assertThat(filter.evaluate(job, in).deliverable()).isEmpty(); // still suppressed despite the failed write
    }

    @Test
    void unreactedLocus_isDelivered() {
        var pf = pf(CK);
        when(observationRepository.findByAgentJobId(any())).thenReturn(List.of(pf));
        when(findingReactionRepository.findLatestByRecurrenceKeysAndReactor(any(), eq(CONTRIBUTOR))).thenReturn(
            List.of()
        );

        var d = filter(true).evaluate(TestEntities.agentJob(), List.of(vf(SLUG, Presence.ABSENT)));

        assertThat(d.deliverable()).hasSize(1);
        assertThat(d.suppressedCount()).isZero();
    }

    @Test
    void addressedButStillBad_isKeptWithStifferOpener() {
        stubPersistedAndReaction(ReactionAction.ADDRESSED);

        var d = filter(true).evaluate(TestEntities.agentJob(), List.of(vf(SLUG, Presence.ABSENT)));

        assertThat(d.deliverable()).hasSize(1);
        assertThat(d.suppressedCount()).isZero();
        assertThat(d.deliverable().get(0).reasoning()).startsWith("You previously marked this as fixed");
    }

    @Test
    void addressedAndNowGood_isDeliveredPlainNotEscalated() {
        // ADDRESSED only escalates a STILL-failing locus; if the practice is now OBSERVED the finding passes through
        // untouched (escalation is keyed on observation == NOT_OBSERVED, not on the reaction alone).
        stubPersistedAndReaction(ReactionAction.ADDRESSED);

        var d = filter(true).evaluate(TestEntities.agentJob(), List.of(vf(SLUG, Presence.PRESENT)));

        assertThat(d.deliverable()).hasSize(1);
        assertThat(d.suppressedCount()).isZero();
        assertThat(d.deliverable().get(0).reasoning()).isEqualTo("because reasons"); // unchanged
    }

    // --- helpers ---

    private void stubPersistedAndReaction(ReactionAction action) {
        var pf = pf(CK);
        var reaction = reaction(action);
        when(observationRepository.findByAgentJobId(any())).thenReturn(List.of(pf));
        when(findingReactionRepository.findLatestByRecurrenceKeysAndReactor(any(), eq(CONTRIBUTOR))).thenReturn(
            List.of(reaction)
        );
    }

    private static ValidatedFinding vf(String slug, Presence presence) {
        // Former GOOD practice: PRESENT->GOOD (strength), ABSENT->BAD (gap), NA->null.
        Assessment assessment =
            presence == Presence.NOT_APPLICABLE
                ? null
                : presence == Presence.PRESENT
                    ? Assessment.GOOD
                    : Assessment.BAD;
        return new ValidatedFinding(
            slug,
            slug + " title",
            presence,
            assessment,
            Severity.MINOR,
            0.8f,
            null,
            "because reasons",
            "do x",
            List.of()
        );
    }

    private Observation pf(String findingFingerprint) {
        Observation pf = org.mockito.Mockito.mock(Observation.class);
        // subject_user_id is always populated; for author-side findings it equals the developer.
        when(pf.getRecurrenceKey()).thenReturn(findingFingerprint);
        when(pf.getAboutUserId()).thenReturn(CONTRIBUTOR);
        when(pf.getArtifactType()).thenReturn(WorkArtifact.PULL_REQUEST);
        when(pf.getArtifactId()).thenReturn(TARGET);
        return pf;
    }

    private static Reaction reaction(ReactionAction action) {
        Reaction r = org.mockito.Mockito.mock(Reaction.class);
        when(r.getRecurrenceKey()).thenReturn(CK);
        when(r.getAction()).thenReturn(action);
        return r;
    }
}
