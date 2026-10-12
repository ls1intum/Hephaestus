package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.adapter.EvidenceDeliveryAuthorization;
import de.tum.cit.aet.hephaestus.agent.context.CitedSourceAccess;
import de.tum.cit.aet.hephaestus.agent.context.providers.mentor.ConversationConsentGate;
import de.tum.cit.aet.hephaestus.agent.handler.AdmittedResultFixtures;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorChannel.DeliveryOutcome;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultInvalidationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * The conversational reconciler reads and authorizes a whole turn's linked results in two queries rather
 * than two per result. This pins the answer that batching must not change, wired through the real
 * {@link ResultVisibilityPolicy} and {@link EvidenceDeliveryAuthorization} rather than a stubbed
 * verdict: the mistake being guarded against lives inside those, in how an absent key is read.
 *
 * <p>Only one direction of disagreement is visible to anyone. A turn that delivers less than it should
 * looks like a mentor with nothing to say. A turn that delivers more quotes evidence nobody may cite, and
 * no surface reports that it did.
 *
 * <p>Run once per ledger-writing turn ending, because the answer to "which linked results may this turn
 * act on" cannot depend on how the turn ended. The two endings write opposite things — DELIVERED raises the
 * unit, INSTANCE_SILENCED burns it, and nothing ever writes a unit back to PREPARED — so an ending that
 * admits an id the other refuses spends a developer's coaching on a result the turn was never allowed to
 * use. {@code MentorTurnPersistence#reconcileConversationalDelivery} is the switch these correspond to; a
 * new outcome added there fails this file's switch to compile.
 */
class ConversationalDeliveryBatchAuthorizationTest extends BaseUnitTest {

    private static final long WS = 7L;
    private static final long RECIPIENT = 11L;
    private static final String PERMITTED_KIND = "scm.pull-request.diff";
    private static final String DENIED_KIND = "hephaestus.observation-history";

    /**
     * Every way a linked result can fail to be deliverable, in one turn, with the deliverable one LAST so
     * that admitting any other would flip the <em>wrong</em> feedback unit rather than merely one extra: a
     * source use the catalog withdrew, an id whose result this workspace cannot read at all, a run with
     * no {@code agent_job} row, and a claim measured against superseded review rules.
     *
     * <p>Each refused result has a PREPARED unit waiting behind it. Those are the trap: they are what a
     * wrongly-admitted result would deliver, so a batch that reads a missing key as anything other than
     * "refused" fails here instead of passing quietly.
     */
    @ParameterizedTest
    @EnumSource(
            value = DeliveryOutcome.class,
            names = {"DELIVERED", "INSTANCE_SILENCED"})
    void actsOnlyOnTheLinkedResultEveryConjunctStillAdmits(DeliveryOutcome ending) {
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        FeedbackEvidenceRepository feedbackResults = mock(FeedbackEvidenceRepository.class);
        FeedbackPlacementRepository placements = mock(FeedbackPlacementRepository.class);
        PracticeResultRepository practiceResults = mock(PracticeResultRepository.class);
        AgentJobRepository jobs = mock(AgentJobRepository.class);
        ArtifactSourceCatalogRegistry catalogs = mock(ArtifactSourceCatalogRegistry.class);

        PracticeResult deniedSource = practiceResult(DENIED_KIND, true);
        PracticeResult unreadable = practiceResult(PERMITTED_KIND, true);
        PracticeResult runWithoutRow = practiceResult(PERMITTED_KIND, true);
        PracticeResult staleClaim = practiceResult(PERMITTED_KIND, false);
        PracticeResult deliverable = practiceResult(PERMITTED_KIND, true);
        List<UUID> linked = List.of(
                deniedSource.getId(),
                unreadable.getId(),
                runWithoutRow.getId(),
                staleClaim.getId(),
                deliverable.getId());

        // Everything but `unreadable`: an id the workspace-scoped read does not return is the same absence
        // the single-id findByIdAndWorkspaceId reported as an empty Optional.
        when(practiceResults.findAllByIdInAndWorkspaceId(any(), eq(WS)))
                .thenReturn(List.of(deniedSource, runWithoutRow, staleClaim, deliverable));
        List<UUID> authorizationAskedAbout = new ArrayList<>();
        when(jobs.findEvidenceContractVersions(eq(WS), any())).thenAnswer(invocation -> {
            Collection<UUID> jobIds = invocation.getArgument(1);
            authorizationAskedAbout.addAll(jobIds);
            // No row for runWithoutRow — a run this workspace does not own, or one that recorded no
            // snapshot. The single-row form answered both with an empty Optional.
            return List.of(
                    new ContractRow(deniedSource.getAgentJobId(), "1.3.0"),
                    new ContractRow(deliverable.getAgentJobId(), "1.3.0"));
        });
        when(catalogs.isSourceUsePermitted(
                        new SourceContractVersion("1.3.0"),
                        new SourceKind(PERMITTED_KIND),
                        SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(true);
        when(catalogs.isSourceUsePermitted(
                        new SourceContractVersion("1.3.0"),
                        new SourceKind(DENIED_KIND),
                        SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(false);

        UUID deliverableUnit = UUID.randomUUID();
        when(feedbackResults.findPreparedConversationFeedbackIdsByResult(WS, RECIPIENT, deliverable.getId()))
                .thenReturn(List.of(deliverableUnit));
        // Lenient on purpose: neither ending may reach these lookups, and if one does, the unit it finds is
        // what the assertion below catches it acting on.
        UUID deniedSourceUnit = trapUnit(feedbackResults, deniedSource);
        UUID unreadableUnit = trapUnit(feedbackResults, unreadable);
        UUID runWithoutRowUnit = trapUnit(feedbackResults, runWithoutRow);
        UUID staleClaimUnit = trapUnit(feedbackResults, staleClaim);
        // Answered for any unit, not just the deliverable one: a wrongly-admitted result must fail on the
        // assertion below, which names the unit it acted on, rather than on a stubbing mismatch.
        Feedback deliveredRow = mock(Feedback.class);
        lenient()
                .when(feedbackRepository.markConversationDelivered(any(), any()))
                .thenReturn(1);
        lenient()
                .when(feedbackRepository.markConversationSuppressedBySilentMode(any()))
                .thenReturn(1);
        lenient().when(feedbackRepository.getReferenceById(any())).thenReturn(deliveredRow);

        ConversationalDeliveryReconciler reconciler = new ConversationalDeliveryReconciler(
                feedbackRepository,
                feedbackResults,
                placements,
                practiceResults,
                new ResultVisibilityPolicy(
                        new EvidenceDeliveryAuthorization(
                                jobs,
                                catalogs,
                                mock(
                                        CitedSourceAccess.class,
                                        call -> call.getMethod().getName().equals("checks")
                                                ? (CitedSourceAccess.Checks) citation -> true
                                                : Mockito.RETURNS_DEFAULTS.answer(call))),
                        mock(PracticeResultInvalidationRepository.class)),
                mock(ConversationConsentGate.class));

        UUID actedOn =
                switch (ending) {
                    case DELIVERED -> {
                        assertThat(reconciler.reconcile(WS, RECIPIENT, UUID.randomUUID(), linked))
                                .isEqualTo(1);
                        ArgumentCaptor<UUID> flipped = ArgumentCaptor.forClass(UUID.class);
                        verify(feedbackRepository, times(1))
                                .markConversationDelivered(flipped.capture(), any(Instant.class));
                        verify(feedbackRepository, never()).markConversationSuppressedBySilentMode(any());
                        verify(placements, times(1)).save(any());
                        yield flipped.getValue();
                    }
                    case INSTANCE_SILENCED -> {
                        assertThat(reconciler.suppressForSilentMode(WS, RECIPIENT, linked))
                                .isEqualTo(1);
                        ArgumentCaptor<UUID> burned = ArgumentCaptor.forClass(UUID.class);
                        verify(feedbackRepository, times(1)).markConversationSuppressedBySilentMode(burned.capture());
                        verify(feedbackRepository, never()).markConversationDelivered(any(), any());
                        // Nothing was said, so nothing was placed.
                        verify(placements, never()).save(any());
                        yield burned.getValue();
                    }
                    case NOT_DELIVERED ->
                        throw new AssertionError("NOT_DELIVERED writes no unit; @EnumSource excludes it");
                };

        assertThat(actedOn)
                .as(
                        "unit acted on for %s: denied=%s unreadable=%s noRun=%s stale=%s",
                        ending, deniedSourceUnit, unreadableUnit, runWithoutRowUnit, staleClaimUnit)
                .isEqualTo(deliverableUnit);

        // Currentness is decided first, so a superseded claim never reaches an evidence read — and can never
        // be readmitted by an authorization answer given about the batch it happened to be in.
        assertThat(authorizationAskedAbout)
                .doesNotContain(staleClaim.getAgentJobId())
                .contains(deniedSource.getAgentJobId(), runWithoutRow.getAgentJobId(), deliverable.getAgentJobId());
        // One read of each kind for the whole turn, however many results the mentor linked.
        verify(practiceResults, times(1)).findAllByIdInAndWorkspaceId(any(), eq(WS));
        verify(jobs, times(1)).findEvidenceContractVersions(eq(WS), any());
    }

    private static UUID trapUnit(FeedbackEvidenceRepository feedbackResults, PracticeResult practiceResult) {
        UUID feedbackId = UUID.randomUUID();
        lenient()
                .when(feedbackResults.findPreparedConversationFeedbackIdsByResult(
                        WS, RECIPIENT, practiceResult.getId()))
                .thenReturn(List.of(feedbackId));
        return feedbackId;
    }

    private static PracticeResult practiceResult(String sourceKind, boolean current) {
        PracticeRevision evaluated = mock(PracticeRevision.class);
        PracticeRevision currentRevision = mock(PracticeRevision.class);
        Practice practice = mock(Practice.class);
        // Lenient: the unreadable fixture never reaches the currentness test, which is the point of it.
        lenient().when(evaluated.getReviewRuleFingerprint()).thenReturn(current ? "v5:fingerprint" : "v5:superseded");
        lenient().when(currentRevision.getReviewRuleFingerprint()).thenReturn("v5:fingerprint");
        lenient().when(practice.getCurrentRevision()).thenReturn(currentRevision);
        UUID jobId = UUID.randomUUID();
        return PracticeResult.builder()
                .scopeKey("test-scope")
                .id(UUID.randomUUID())
                .agentJobId(jobId)
                .practice(practice)
                .practiceRevision(evaluated)
                .evidence(AdmittedResultFixtures.evidence(jobId, sourceKind))
                .build();
    }

    private record ContractRow(UUID id, @Nullable String contractVersion)
            implements AgentJobRepository.EvidenceContractVersionRow {
        @Override
        public int getAttempt() {
            return 0;
        }

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        @Nullable
        public String getContractVersion() {
            return contractVersion;
        }
    }
}
