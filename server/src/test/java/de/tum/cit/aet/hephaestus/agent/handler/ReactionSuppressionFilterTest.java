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
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.finding.CorrelationKey;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReaction;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReactionAction;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReactionRepository;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
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
    private PracticeFindingRepository practiceFindingRepository;

    @Mock
    private FindingReactionRepository findingReactionRepository;

    @Mock
    private FeedbackLedgerRecorder feedbackLedgerRecorder;

    private static final String SLUG = "commit-discipline";
    private static final long CONTRIBUTOR = 7L;
    private static final long TARGET = 100L;
    // The canonical key the filter recomputes for a SLUG finding with no location — the SAME value deliver() persists.
    private static final String CK = CorrelationKey.compute(
        SLUG,
        WorkArtifact.PULL_REQUEST.name(),
        TARGET,
        CONTRIBUTOR,
        null
    );

    private ReactionSuppressionFilter filter(boolean enabled) {
        return new ReactionSuppressionFilter(
            practiceFindingRepository,
            findingReactionRepository,
            feedbackLedgerRecorder,
            new PracticeReviewProperties(false, true, false, "", 15, false, enabled, false)
        );
    }

    @Test
    void flagOff_passesThroughUnchanged_noRepoCalls() {
        List<ValidatedFinding> in = List.of(vf(SLUG, Verdict.NOT_OBSERVED));

        var d = filter(false).evaluate(TestEntities.agentJob(), in);

        assertThat(d.deliverable()).isEqualTo(in);
        assertThat(d.suppressedCount()).isZero();
        verify(practiceFindingRepository, never()).findByAgentJobId(any());
    }

    @Test
    void disputedLocus_isSuppressedAndLedgered() {
        stubPersistedAndReaction(FindingReactionAction.DISPUTED);

        var d = filter(true).evaluate(TestEntities.agentJob(), List.of(vf(SLUG, Verdict.NOT_OBSERVED)));

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
        stubPersistedAndReaction(FindingReactionAction.NOT_APPLICABLE);
        doThrow(new RuntimeException("ledger down"))
            .when(feedbackLedgerRecorder)
            .recordSuppressed(any(), any(), any(), anyInt());

        var filter = filter(true);
        var job = TestEntities.agentJob();
        var in = List.of(vf(SLUG, Verdict.NOT_OBSERVED));

        assertThatCode(() -> filter.evaluate(job, in)).doesNotThrowAnyException();
        assertThat(filter.evaluate(job, in).deliverable()).isEmpty(); // still suppressed despite the failed write
    }

    @Test
    void unreactedLocus_isDelivered() {
        var pf = pf(CK);
        when(practiceFindingRepository.findByAgentJobId(any())).thenReturn(List.of(pf));
        when(findingReactionRepository.findLatestByCorrelationKeysAndContributor(any(), eq(CONTRIBUTOR))).thenReturn(
            List.of()
        );

        var d = filter(true).evaluate(TestEntities.agentJob(), List.of(vf(SLUG, Verdict.NOT_OBSERVED)));

        assertThat(d.deliverable()).hasSize(1);
        assertThat(d.suppressedCount()).isZero();
    }

    @Test
    void appliedButStillNotObserved_isKeptWithStifferOpener() {
        stubPersistedAndReaction(FindingReactionAction.APPLIED);

        var d = filter(true).evaluate(TestEntities.agentJob(), List.of(vf(SLUG, Verdict.NOT_OBSERVED)));

        assertThat(d.deliverable()).hasSize(1);
        assertThat(d.suppressedCount()).isZero();
        assertThat(d.deliverable().get(0).reasoning()).startsWith("You previously marked this as fixed");
    }

    // --- helpers ---

    private void stubPersistedAndReaction(FindingReactionAction action) {
        var pf = pf(CK);
        var reaction = reaction(action);
        when(practiceFindingRepository.findByAgentJobId(any())).thenReturn(List.of(pf));
        when(findingReactionRepository.findLatestByCorrelationKeysAndContributor(any(), eq(CONTRIBUTOR))).thenReturn(
            List.of(reaction)
        );
    }

    private static ValidatedFinding vf(String slug, Verdict verdict) {
        return new ValidatedFinding(
            slug,
            slug + " title",
            verdict,
            Severity.MINOR,
            0.8f,
            null,
            "because reasons",
            "do x",
            List.of()
        );
    }

    private PracticeFinding pf(String correlationKey) {
        PracticeFinding pf = org.mockito.Mockito.mock(PracticeFinding.class);
        User contributor = new User();
        contributor.setId(CONTRIBUTOR);
        when(pf.getCorrelationKey()).thenReturn(correlationKey);
        when(pf.getContributor()).thenReturn(contributor);
        when(pf.getSubjectUserId()).thenReturn(null);
        when(pf.getTargetType()).thenReturn(WorkArtifact.PULL_REQUEST);
        when(pf.getTargetId()).thenReturn(TARGET);
        return pf;
    }

    private static FindingReaction reaction(FindingReactionAction action) {
        FindingReaction r = org.mockito.Mockito.mock(FindingReaction.class);
        when(r.getCorrelationKey()).thenReturn(CK);
        when(r.getAction()).thenReturn(action);
        return r;
    }
}
