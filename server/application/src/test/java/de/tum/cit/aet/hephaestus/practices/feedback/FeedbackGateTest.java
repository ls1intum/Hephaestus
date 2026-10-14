package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackContentDigest;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecisionRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeVersion;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckStage;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@Tag("unit")
class FeedbackGateTest {

    final FeedbackBasisRepository basis = mock(FeedbackBasisRepository.class);
    final ReviewRepository reviews = mock(ReviewRepository.class);
    final PersonProcessingSuppression suppression = mock(PersonProcessingSuppression.class);
    final FeedbackGate gate = new FeedbackGate(
            basis,
            mock(FeedbackCopyRepository.class),
            mock(FeedbackDecisionRepository.class),
            mock(ResultRepository.class),
            mock(PracticeRepository.class),
            mock(PracticeVersionRepository.class),
            reviews,
            mock(WorkspaceRepository.class),
            mock(EvidenceAuthorization.class),
            suppression,
            mock(SilentModeQuery.class),
            List.of(),
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            mock(FeedbackContentDigest.class));
    final Feedback feedback = Feedback.builder()
            .id(UUID.randomUUID())
            .reviewId(UUID.randomUUID())
            .workspaceId(1)
            .developerId(2)
            .destination(FeedbackDestination.PRACTICE_PAGE)
            .build();
    FeedbackBasisRepository.PolicyRow policy;

    @BeforeEach
    void setup() {
        when(reviews.findByIdAndWorkspaceId(feedback.getReviewId(), 1))
                .thenReturn(Optional.of(Review.builder()
                        .status(ReviewStatus.RUNNING)
                        .rolloutRevision(4)
                        .build()));
        policy = mock(FeedbackBasisRepository.PolicyRow.class);
        when(basis.policy(1, 2)).thenReturn(Optional.of(policy));
        when(policy.getWorkspaceStatus()).thenReturn("ACTIVE");
        when(policy.getEnabled()).thenReturn(true);
        when(policy.getRolloutRevision()).thenReturn(4L);
        when(policy.getPeopleScope()).thenReturn("ALL");
        when(policy.getAccountStatus()).thenReturn("ACTIVE");
        when(policy.getDeliveryEnabled()).thenReturn(true);
    }

    @Test
    void shouldRefuseScopeChangesAgainstTheAdmittedRevision() {
        when(policy.getRolloutRevision()).thenReturn(5L);
        var check = gate.evaluate(feedback, FeedbackCheckStage.EGRESS, false);
        assertThat(check.reason()).isEqualTo(FeedbackStopReason.STALE_ROLLOUT_REVISION);
        assertThat(check.facts().admittedRolloutRevision()).isEqualTo(4);
    }

    @Test
    void shouldRefuseOptedOutOrSuppressedDevelopers() {
        when(policy.getAiChoice()).thenReturn("NO_AI");
        assertThat(gate.evaluate(feedback, FeedbackCheckStage.EGRESS, false).reason())
                .isEqualTo(FeedbackStopReason.NO_AI);
        when(suppression.isAccountSuppressed(2)).thenReturn(true);
        assertThat(gate.evaluate(feedback, FeedbackCheckStage.EGRESS, false).reason())
                .isEqualTo(FeedbackStopReason.PERSON_SUPPRESSED);
    }

    @Test
    void shouldRefuseSelectedScopeWithoutSelection() {
        when(policy.getPeopleScope()).thenReturn("SELECTED");
        assertThat(gate.evaluate(feedback, FeedbackCheckStage.EGRESS, false).reason())
                .isEqualTo(FeedbackStopReason.OUTSIDE_CURRENT_SCOPE);
    }

    private record Support(
            FeedbackGate gate, Feedback feedback, PracticeVersion current, ItemSource source, ItemRef item) {}

    private Support supported() {
        var supportedFeedback = Feedback.builder()
                .id(feedback.getId())
                .reviewId(feedback.getReviewId())
                .workspaceId(1)
                .itemId(3L)
                .developerId(2)
                .destination(FeedbackDestination.ON_WORK)
                .build();
        var result = Result.builder()
                .id(UUID.randomUUID())
                .reviewId(feedback.getReviewId())
                .workspaceId(1)
                .itemId(3)
                .itemVersion("v1")
                .practiceId(4)
                .practiceVersionId(5L)
                .developerId(2L)
                .build();
        when(basis.findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(1, feedback.getId()))
                .thenReturn(List.of(FeedbackBasis.builder()
                        .id(UUID.randomUUID())
                        .resultId(result.getId())
                        .build()));
        var results = mock(ResultRepository.class);
        when(results.findByWorkspaceIdAndIdIn(1, List.of(result.getId()))).thenReturn(List.of(result));
        var practice = mock(Practice.class);
        when(practice.getWorkspaceId()).thenReturn(1L);
        when(practice.getFeedbackMode()).thenReturn(FeedbackMode.AUTOMATIC);
        var practiceRepository = mock(PracticeRepository.class);
        when(practiceRepository.findById(4L)).thenReturn(Optional.of(practice));
        var pinned = mock(PracticeVersion.class);
        when(pinned.getWorkspaceId()).thenReturn(1L);
        when(pinned.getReviewDigest()).thenReturn("v6:" + "a".repeat(64));
        when(pinned.getDefinition())
                .thenReturn(new ObjectMapper()
                        .readTree("{\"about\":\"AUTHOR\",\"reviewOn\":{\"state\":{\"state\":[\"open\"]}}}"));
        var current = mock(PracticeVersion.class);
        when(current.getReviewDigest()).thenReturn("v6:" + "a".repeat(64));
        when(practice.getCurrentVersion()).thenReturn(current);
        var versionRepository = mock(PracticeVersionRepository.class);
        when(versionRepository.findById(5L)).thenReturn(Optional.of(pinned));
        var row = mock(FeedbackBasisRepository.ItemRow.class);
        when(row.getId()).thenReturn(3L);
        when(row.getKind()).thenReturn("scm.pull_request");
        when(row.getSourceId()).thenReturn(42L);
        when(row.getParentId()).thenReturn(null);
        when(basis.findItems(1, List.of(3L))).thenReturn(List.of(row));
        var source = mock(ItemSource.class);
        var item = new ItemRef(1, 3, new ItemKind("scm.pull_request"), 42L, null, null);
        when(source.kind()).thenReturn(item.kind());
        when(source.supports(item)).thenReturn(true);
        when(source.current(item)).thenReturn(Optional.of(snapshot(item, "open", "v1")));
        var evidence = mock(EvidenceAuthorization.class);
        when(evidence.permitsForNewDelivery(eq(1L), any(), any())).thenReturn(Set.of(result.getId()));
        var gate = new FeedbackGate(
                basis,
                mock(FeedbackCopyRepository.class),
                mock(FeedbackDecisionRepository.class),
                results,
                practiceRepository,
                versionRepository,
                reviews,
                mock(WorkspaceRepository.class),
                evidence,
                suppression,
                mock(SilentModeQuery.class),
                List.of(source),
                Clock.systemUTC(),
                mock(FeedbackContentDigest.class));
        return new Support(gate, supportedFeedback, current, source, item);
    }

    private static ItemSnapshot snapshot(ItemRef item, String state, String version) {
        return new ItemSnapshot(
                item,
                new ItemVersion(version, null),
                new IntegrationRef(IntegrationKind.GITHUB, 1, null),
                Map.of("state", state),
                List.of(),
                new ItemLabel(3, "Change", "pull request", null, null, null, IntegrationKind.GITHUB));
    }

    @Test
    void shouldAllowCurrentAuthorizedAutomaticFeedbackButRefuseAChangedPracticeStandard() {
        var support = supported();
        assertThat(support.gate()
                        .evaluate(support.feedback(), FeedbackCheckStage.EGRESS, true)
                        .allowed())
                .isTrue();
        when(support.current().getReviewDigest()).thenReturn("v6:" + "b".repeat(64));
        assertThat(support.gate()
                        .evaluate(support.feedback(), FeedbackCheckStage.EGRESS, true)
                        .reason())
                .isEqualTo(FeedbackStopReason.REVIEWED_VERSION_CHANGED);
    }

    @Test
    void shouldRefuseChangedWorkAndIneligibleStateThroughTheGenericSource() {
        var support = supported();
        when(support.source().current(support.item())).thenReturn(Optional.of(snapshot(support.item(), "open", "v2")));
        assertThat(support.gate()
                        .evaluate(support.feedback(), FeedbackCheckStage.EGRESS, true)
                        .reason())
                .isEqualTo(FeedbackStopReason.REVIEWED_VERSION_CHANGED);
        when(support.source().current(support.item()))
                .thenReturn(Optional.of(snapshot(support.item(), "closed", "v1")));
        var closed = support.gate().evaluate(support.feedback(), FeedbackCheckStage.EGRESS, true);
        assertThat(closed.reason()).isEqualTo(FeedbackStopReason.ITEM_STATE_INELIGIBLE);
        assertThat(closed.facts().currentVersion()).isTrue();
    }
}
