package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.adapter.EvidenceDeliveryAuthorization;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationVisibilityPolicy;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

/**
 * The conversational reconciler reads and authorizes a whole turn's linked findings in two queries rather
 * than two per finding. This pins the answer that batching must not change, wired through the real
 * {@link ObservationVisibilityPolicy} and {@link EvidenceDeliveryAuthorization} rather than a stubbed
 * verdict: the mistake being guarded against lives inside those, in how an absent key is read.
 *
 * <p>Only one direction of disagreement is visible to anyone. A turn that delivers less than it should
 * looks like a mentor with nothing to say. A turn that delivers more quotes evidence nobody may cite, and
 * no surface reports that it did.
 */
class ConversationalDeliveryBatchAuthorizationTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long WS = 7L;
    private static final long RECIPIENT = 11L;
    private static final String PERMITTED_KIND = "scm.pull-request.diff";
    private static final String DENIED_KIND = "hephaestus.observation-history";

    /**
     * Every way a linked finding can fail to be deliverable, in one turn, with the deliverable one LAST so
     * that admitting any other would flip the <em>wrong</em> feedback unit rather than merely one extra: a
     * source use the catalog withdrew, an id whose observation this workspace cannot read at all, a run with
     * no {@code agent_job} row, and a claim measured against superseded review rules.
     *
     * <p>Each refused finding has a PREPARED unit waiting behind it. Those are the trap: they are what a
     * wrongly-admitted finding would deliver, so a batch that reads a missing key as anything other than
     * "refused" fails here instead of passing quietly.
     */
    @Test
    void deliversOnlyTheLinkedFindingEveryConjunctStillAdmits() {
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        FeedbackObservationRepository feedbackObservations = mock(FeedbackObservationRepository.class);
        FeedbackPlacementRepository placements = mock(FeedbackPlacementRepository.class);
        ObservationRepository observations = mock(ObservationRepository.class);
        AgentJobRepository jobs = mock(AgentJobRepository.class);
        ArtifactSourceCatalogRegistry catalogs = mock(ArtifactSourceCatalogRegistry.class);

        Observation deniedSource = observation(DENIED_KIND, true);
        Observation unreadable = observation(PERMITTED_KIND, true);
        Observation runWithoutRow = observation(PERMITTED_KIND, true);
        Observation staleClaim = observation(PERMITTED_KIND, false);
        Observation deliverable = observation(PERMITTED_KIND, true);
        List<UUID> linked = List.of(
            deniedSource.getId(),
            unreadable.getId(),
            runWithoutRow.getId(),
            staleClaim.getId(),
            deliverable.getId()
        );

        // Everything but `unreadable`: an id the workspace-scoped read does not return is the same absence
        // the single-id findByIdAndWorkspaceId reported as an empty Optional.
        when(observations.findAllByIdInAndWorkspaceId(any(), eq(WS))).thenReturn(
            List.of(deniedSource, runWithoutRow, staleClaim, deliverable)
        );
        List<UUID> authorizationAskedAbout = new ArrayList<>();
        when(jobs.findEvidenceContractVersions(eq(WS), any())).thenAnswer(invocation -> {
            Collection<UUID> jobIds = invocation.getArgument(1);
            authorizationAskedAbout.addAll(jobIds);
            // No row for runWithoutRow — a run this workspace does not own, or one that recorded no
            // snapshot. The single-row form answered both with an empty Optional.
            return List.of(
                new ContractRow(deniedSource.getAgentJobId(), "1.0.0"),
                new ContractRow(deliverable.getAgentJobId(), "1.0.0")
            );
        });
        when(
            catalogs.isSourceUsePermitted(
                new SourceContractVersion("1.0.0"),
                new SourceKind(PERMITTED_KIND),
                SourceUsePurpose.CONVERSATIONAL_MENTORING
            )
        ).thenReturn(true);
        when(
            catalogs.isSourceUsePermitted(
                new SourceContractVersion("1.0.0"),
                new SourceKind(DENIED_KIND),
                SourceUsePurpose.CONVERSATIONAL_MENTORING
            )
        ).thenReturn(false);

        UUID deliverableUnit = UUID.randomUUID();
        when(
            feedbackObservations.findPreparedConversationFeedbackIdsByObservation(WS, RECIPIENT, deliverable.getId())
        ).thenReturn(List.of(deliverableUnit));
        // Lenient on purpose: the reconciler must never reach these lookups, and if it does, the unit it
        // finds is what the assertion below catches it delivering.
        UUID deniedSourceUnit = trapUnit(feedbackObservations, deniedSource);
        UUID unreadableUnit = trapUnit(feedbackObservations, unreadable);
        UUID runWithoutRowUnit = trapUnit(feedbackObservations, runWithoutRow);
        UUID staleClaimUnit = trapUnit(feedbackObservations, staleClaim);
        when(feedbackRepository.markConversationDelivered(eq(deliverableUnit), any())).thenReturn(1);
        when(feedbackRepository.getReferenceById(deliverableUnit)).thenReturn(mock(Feedback.class));

        ConversationalDeliveryReconciler reconciler = new ConversationalDeliveryReconciler(
            feedbackRepository,
            feedbackObservations,
            placements,
            observations,
            new ObservationVisibilityPolicy(new EvidenceDeliveryAuthorization(jobs, catalogs))
        );

        int flips = reconciler.reconcile(WS, RECIPIENT, UUID.randomUUID(), linked);

        assertThat(flips).isEqualTo(1);
        ArgumentCaptor<UUID> delivered = ArgumentCaptor.forClass(UUID.class);
        verify(feedbackRepository, times(1)).markConversationDelivered(delivered.capture(), any(Instant.class));
        assertThat(delivered.getValue())
            .as(
                "delivered unit: denied=%s unreadable=%s noRun=%s stale=%s",
                deniedSourceUnit,
                unreadableUnit,
                runWithoutRowUnit,
                staleClaimUnit
            )
            .isEqualTo(deliverableUnit);

        // Currentness is decided first, so a superseded claim never reaches an evidence read — and can never
        // be readmitted by an authorization answer given about the batch it happened to be in.
        assertThat(authorizationAskedAbout)
            .doesNotContain(staleClaim.getAgentJobId())
            .contains(deniedSource.getAgentJobId(), runWithoutRow.getAgentJobId(), deliverable.getAgentJobId());
        // One read of each kind for the whole turn, however many findings the mentor linked.
        verify(observations, times(1)).findAllByIdInAndWorkspaceId(any(), eq(WS));
        verify(jobs, times(1)).findEvidenceContractVersions(eq(WS), any());
    }

    private static UUID trapUnit(FeedbackObservationRepository feedbackObservations, Observation observation) {
        UUID feedbackId = UUID.randomUUID();
        lenient()
            .when(
                feedbackObservations.findPreparedConversationFeedbackIdsByObservation(
                    WS,
                    RECIPIENT,
                    observation.getId()
                )
            )
            .thenReturn(List.of(feedbackId));
        return feedbackId;
    }

    private static Observation observation(String sourceKind, boolean current) {
        PracticeRevision evaluated = mock(PracticeRevision.class);
        PracticeRevision currentRevision = mock(PracticeRevision.class);
        Practice practice = mock(Practice.class);
        // Lenient: the unreadable fixture never reaches the currentness test, which is the point of it.
        lenient().when(evaluated.getReviewRuleFingerprint()).thenReturn(current ? "fingerprint" : "superseded");
        lenient().when(currentRevision.getReviewRuleFingerprint()).thenReturn("fingerprint");
        lenient().when(practice.getCurrentRevision()).thenReturn(currentRevision);
        return Observation.builder()
            .id(UUID.randomUUID())
            .agentJobId(UUID.randomUUID())
            .practice(practice)
            .practiceRevision(evaluated)
            .evidence(MAPPER.readTree("{\"citations\":[{\"sourceKind\":\"" + sourceKind + "\"}]}"))
            .build();
    }

    private record ContractRow(
        UUID id,
        @Nullable String contractVersion
    ) implements AgentJobRepository.EvidenceContractVersionRow {
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
