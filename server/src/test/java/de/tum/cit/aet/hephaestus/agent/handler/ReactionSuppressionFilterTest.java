package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
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
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.util.List;
import java.util.UUID;
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

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

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
        ReactionSuppressionFilter f = filter(false);
        List<ValidatedFinding> in = List.of(vf("commit-discipline", "T1", Verdict.NOT_OBSERVED));

        ReactionSuppressionFilter.ReactionDecision d = f.evaluate(job(), in);

        assertThat(d.deliverable()).isEqualTo(in);
        assertThat(d.suppressedCount()).isZero();
        verify(practiceFindingRepository, never()).findByAgentJobId(any());
    }

    @Test
    void disputedLocus_isSuppressedAndLedgered() {
        ReactionSuppressionFilter f = filter(true);
        PracticeFinding pf = pf("commit-discipline", "T1", "CK1", Verdict.NOT_OBSERVED);
        FindingReaction r = reaction("CK1", FindingReactionAction.DISPUTED);
        when(practiceFindingRepository.findByAgentJobId(any())).thenReturn(List.of(pf));
        when(findingReactionRepository.findLatestByCorrelationKeysAndContributor(any(), eq(7L))).thenReturn(List.of(r));

        ReactionSuppressionFilter.ReactionDecision d = f.evaluate(
            job(),
            List.of(vf("commit-discipline", "T1", Verdict.NOT_OBSERVED))
        );

        assertThat(d.deliverable()).isEmpty();
        assertThat(d.suppressedCount()).isEqualTo(1);
        verify(feedbackLedgerRecorder).recordSuppressed(any(), eq(pf), eq(FeedbackSuppressionReason.REACTED_DISPUTED), anyInt());
    }

    @Test
    void unreactedLocus_isDelivered() {
        ReactionSuppressionFilter f = filter(true);
        PracticeFinding pf = pf("commit-discipline", "T1", "CK1", Verdict.NOT_OBSERVED);
        when(practiceFindingRepository.findByAgentJobId(any())).thenReturn(List.of(pf));
        when(findingReactionRepository.findLatestByCorrelationKeysAndContributor(any(), eq(7L))).thenReturn(List.of());

        ReactionSuppressionFilter.ReactionDecision d = f.evaluate(
            job(),
            List.of(vf("commit-discipline", "T1", Verdict.NOT_OBSERVED))
        );

        assertThat(d.deliverable()).hasSize(1);
        assertThat(d.suppressedCount()).isZero();
    }

    @Test
    void appliedButStillNotObserved_isKeptAndEscalated() {
        ReactionSuppressionFilter f = filter(true);
        PracticeFinding pf = pf("commit-discipline", "T1", "CK1", Verdict.NOT_OBSERVED);
        FindingReaction r = reaction("CK1", FindingReactionAction.APPLIED);
        when(practiceFindingRepository.findByAgentJobId(any())).thenReturn(List.of(pf));
        when(findingReactionRepository.findLatestByCorrelationKeysAndContributor(any(), eq(7L))).thenReturn(List.of(r));

        ReactionSuppressionFilter.ReactionDecision d = f.evaluate(
            job(),
            List.of(vf("commit-discipline", "T1", Verdict.NOT_OBSERVED))
        );

        assertThat(d.deliverable()).hasSize(1);
        assertThat(d.escalatedLoci()).hasSize(1);
        assertThat(d.deliverable().get(0).reasoning()).startsWith("You previously marked this as fixed");
    }

    // --- helpers ---

    private AgentJob job() {
        return de.tum.cit.aet.hephaestus.testconfig.TestEntities.agentJob();
    }

    private static ValidatedFinding vf(String slug, String title, Verdict verdict) {
        return new ValidatedFinding(slug, title, verdict, Severity.MINOR, 0.8f, null, "because reasons", "do x", List.of());
    }

    private PracticeFinding pf(String slug, String title, String ck, Verdict verdict) {
        PracticeFinding pf = org.mockito.Mockito.mock(PracticeFinding.class);
        Practice practice = org.mockito.Mockito.mock(Practice.class);
        User contributor = new User();
        contributor.setId(7L);
        when(practice.getSlug()).thenReturn(slug);
        when(pf.getPractice()).thenReturn(practice);
        when(pf.getContributor()).thenReturn(contributor);
        when(pf.getTitle()).thenReturn(title);
        when(pf.getCorrelationKey()).thenReturn(ck);
        when(pf.getVerdict()).thenReturn(verdict);
        when(pf.getEvidence()).thenReturn(null);
        when(pf.getSubjectUserId()).thenReturn(null);
        when(pf.getTargetType()).thenReturn(WorkArtifact.PULL_REQUEST);
        when(pf.getTargetId()).thenReturn(100L);
        return pf;
    }

    private static FindingReaction reaction(String ck, FindingReactionAction action) {
        FindingReaction r = org.mockito.Mockito.mock(FindingReaction.class);
        when(r.getCorrelationKey()).thenReturn(ck);
        when(r.getAction()).thenReturn(action);
        return r;
    }
}
