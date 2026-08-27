package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackEngagementDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackResponseDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackResponseRequestDTO;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.Reaction;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionRepository;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

class FeedbackResponseServiceTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Long CONTRIBUTOR_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;
    private static final UUID FEEDBACK_ID = UUID.randomUUID();

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private CurrentDeveloperLookup currentDeveloperLookup;

    @Captor
    private ArgumentCaptor<Reaction> reactionCaptor;

    private FeedbackResponseService service;
    private WorkspaceContext workspaceContext;

    @BeforeEach
    void setUp() {
        service = new FeedbackResponseService(reactionRepository, feedbackRepository, currentDeveloperLookup);
        workspaceContext = new WorkspaceContext(WORKSPACE_ID, "test-ws", "Test WS", null, null, false, false, Set.of());
        appended.clear();
        // Both shapes of the port: a write demands an id, a read accepts its absence. Lenient because most
        // tests exercise only one of the two paths.
        org.mockito.Mockito.lenient()
            .when(currentDeveloperLookup.currentDeveloperIdElseThrow())
            .thenReturn(CONTRIBUTOR_ID);
        org.mockito.Mockito.lenient()
            .when(currentDeveloperLookup.currentDeveloperId())
            .thenReturn(Optional.of(CONTRIBUTOR_ID));
        org.mockito.Mockito.lenient()
            .when(reactionRepository.save(any(Reaction.class)))
            .thenAnswer(invocation -> {
                Reaction row = invocation.getArgument(0);
                appended.add(row);
                return row;
            });
        // Stands in for the repository's fold so the returned DTO says something here. Deliberately naive —
        // it reads the rows this test appended, one dimension at a time. Whether the SQL folds them the same
        // way is what FeedbackResponseControllerIntegrationTest is for; this class tests validation and what
        // gets written.
        org.mockito.Mockito.lenient()
            .when(reactionRepository.findCurrentResponse(any(), any()))
            .thenAnswer(invocation -> Optional.ofNullable(fold()));
    }

    private final List<Reaction> appended = new java.util.ArrayList<>();

    private ReactionRepository.@org.jspecify.annotations.Nullable CurrentResponseProjection fold() {
        int withdrawnAt = -1;
        for (int index = appended.size() - 1; index >= 0; index--) {
            Reaction row = appended.get(index);
            if (row.getUsefulness() == null && row.getResolution() == null) {
                withdrawnAt = index;
                break;
            }
        }
        FeedbackUsefulness usefulness = null;
        FeedbackResolution resolution = null;
        String comment = null;
        for (int index = appended.size() - 1; index > withdrawnAt; index--) {
            Reaction row = appended.get(index);
            if (usefulness == null) {
                usefulness = row.getUsefulness();
            }
            if (resolution == null && row.getResolution() != null) {
                resolution = row.getResolution();
                comment = row.getExplanation();
            }
        }
        if (usefulness == null && resolution == null) {
            return null;
        }
        var projection = org.mockito.Mockito.mock(ReactionRepository.CurrentResponseProjection.class);
        org.mockito.Mockito.lenient()
            .when(projection.getUsefulness())
            .thenReturn(usefulness == null ? null : usefulness.name());
        org.mockito.Mockito.lenient()
            .when(projection.getResolution())
            .thenReturn(resolution == null ? null : resolution.name());
        org.mockito.Mockito.lenient().when(projection.getComment()).thenReturn(comment);
        org.mockito.Mockito.lenient().when(projection.getRespondedAt()).thenReturn(Instant.now());
        return projection;
    }

    private Feedback createFeedback(Long recipientUserId) {
        return Feedback.builder()
            .id(FEEDBACK_ID)
            .recipientUserId(recipientUserId)
            .workspaceId(WORKSPACE_ID)
            .deliveryState(FeedbackDeliveryState.DELIVERED)
            .build();
    }

    @Nested
    class SubmitResponse {

        @Test
        void shouldRecordUsefulnessAndResolutionTogether() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(
                feedbackRepository.findByIdAndWorkspaceIdAndRecipientUserIdAndDeliveryState(
                    FEEDBACK_ID,
                    WORKSPACE_ID,
                    CONTRIBUTOR_ID,
                    FeedbackDeliveryState.DELIVERED
                )
            ).thenReturn(Optional.of(feedback));
            var request = new FeedbackResponseRequestDTO(
                FeedbackUsefulness.HELPFUL,
                FeedbackResolution.ADDRESSED,
                "Clear and actionable.",
                null
            );

            FeedbackResponseDTO result = service.submitResponse(workspaceContext, FEEDBACK_ID, request);

            verify(reactionRepository).save(reactionCaptor.capture());
            Reaction saved = reactionCaptor.getValue();
            assertThat(saved.getUsefulness()).isEqualTo(FeedbackUsefulness.HELPFUL);
            assertThat(saved.getResolution()).isEqualTo(FeedbackResolution.ADDRESSED);
            assertThat(result.usefulness()).isEqualTo(FeedbackUsefulness.HELPFUL);
            assertThat(result.resolution()).isEqualTo(FeedbackResolution.ADDRESSED);
        }

        @Test
        void shouldRejectEmptyResponse() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(
                feedbackRepository.findByIdAndWorkspaceIdAndRecipientUserIdAndDeliveryState(
                    FEEDBACK_ID,
                    WORKSPACE_ID,
                    CONTRIBUTOR_ID,
                    FeedbackDeliveryState.DELIVERED
                )
            ).thenReturn(Optional.of(feedback));

            assertThatThrownBy(() ->
                service.submitResponse(
                    workspaceContext,
                    FEEDBACK_ID,
                    new FeedbackResponseRequestDTO(null, null, null, null)
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usefulness or resolution");
        }

        @Test
        void addressedFeedbackSaves() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(
                feedbackRepository.findByIdAndWorkspaceIdAndRecipientUserIdAndDeliveryState(
                    FEEDBACK_ID,
                    WORKSPACE_ID,
                    CONTRIBUTOR_ID,
                    FeedbackDeliveryState.DELIVERED
                )
            ).thenReturn(Optional.of(feedback));
            when(feedbackRepository.findHeadlineRecurrenceKey(FEEDBACK_ID)).thenReturn(Optional.of("ck-abc123"));

            var request = new FeedbackResponseRequestDTO(null, FeedbackResolution.ADDRESSED, null, null);
            FeedbackResponseDTO result = service.submitResponse(workspaceContext, FEEDBACK_ID, request);

            assertThat(result.resolution()).isEqualTo(FeedbackResolution.ADDRESSED);
            assertThat(result.comment()).isNull();

            verify(reactionRepository).save(reactionCaptor.capture());
            Reaction saved = reactionCaptor.getValue();
            assertThat(saved.getReactorUserId()).isEqualTo(CONTRIBUTOR_ID);
            assertThat(saved.getResolution()).isEqualTo(FeedbackResolution.ADDRESSED);
            // Denormalization (ADR 0021): the saved reaction must carry the headline recurrence key so
            // suppression can follow the reacted locus across the detector's per-run re-detections.
            assertThat(saved.getRecurrenceKey()).isEqualTo("ck-abc123");
        }

        @Test
        void nonDeliveredFeedbackThrows() {
            when(
                feedbackRepository.findByIdAndWorkspaceIdAndRecipientUserIdAndDeliveryState(
                    FEEDBACK_ID,
                    WORKSPACE_ID,
                    CONTRIBUTOR_ID,
                    FeedbackDeliveryState.DELIVERED
                )
            ).thenReturn(Optional.empty());

            var request = new FeedbackResponseRequestDTO(null, FeedbackResolution.ADDRESSED, null, null);
            assertThatThrownBy(() -> service.submitResponse(workspaceContext, FEEDBACK_ID, request)).isInstanceOf(
                EntityNotFoundException.class
            );
        }

        @Test
        void disputedWithExplanationSaves() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(
                feedbackRepository.findByIdAndWorkspaceIdAndRecipientUserIdAndDeliveryState(
                    FEEDBACK_ID,
                    WORKSPACE_ID,
                    CONTRIBUTOR_ID,
                    FeedbackDeliveryState.DELIVERED
                )
            ).thenReturn(Optional.of(feedback));

            var request = new FeedbackResponseRequestDTO(
                null,
                FeedbackResolution.DISPUTED,
                "The AI is wrong about this",
                null
            );
            FeedbackResponseDTO result = service.submitResponse(workspaceContext, FEEDBACK_ID, request);

            assertThat(result.resolution()).isEqualTo(FeedbackResolution.DISPUTED);
            assertThat(result.comment()).isEqualTo("The AI is wrong about this");
        }

        @Test
        void notApplicableSaves() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(
                feedbackRepository.findByIdAndWorkspaceIdAndRecipientUserIdAndDeliveryState(
                    FEEDBACK_ID,
                    WORKSPACE_ID,
                    CONTRIBUTOR_ID,
                    FeedbackDeliveryState.DELIVERED
                )
            ).thenReturn(Optional.of(feedback));

            var request = new FeedbackResponseRequestDTO(
                null,
                FeedbackResolution.NOT_APPLICABLE,
                "Not relevant to my use case",
                null
            );
            FeedbackResponseDTO result = service.submitResponse(workspaceContext, FEEDBACK_ID, request);

            assertThat(result.resolution()).isEqualTo(FeedbackResolution.NOT_APPLICABLE);
        }

        @Test
        void disputedWithoutExplanationThrows() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(
                feedbackRepository.findByIdAndWorkspaceIdAndRecipientUserIdAndDeliveryState(
                    FEEDBACK_ID,
                    WORKSPACE_ID,
                    CONTRIBUTOR_ID,
                    FeedbackDeliveryState.DELIVERED
                )
            ).thenReturn(Optional.of(feedback));

            var request = new FeedbackResponseRequestDTO(null, FeedbackResolution.DISPUTED, null, null);
            assertThatThrownBy(() -> service.submitResponse(workspaceContext, FEEDBACK_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comment is required");
        }

        @Test
        void disputedWithBlankExplanationThrows() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(
                feedbackRepository.findByIdAndWorkspaceIdAndRecipientUserIdAndDeliveryState(
                    FEEDBACK_ID,
                    WORKSPACE_ID,
                    CONTRIBUTOR_ID,
                    FeedbackDeliveryState.DELIVERED
                )
            ).thenReturn(Optional.of(feedback));

            var request = new FeedbackResponseRequestDTO(null, FeedbackResolution.DISPUTED, "   ", null);
            assertThatThrownBy(() -> service.submitResponse(workspaceContext, FEEDBACK_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comment is required");
        }

        @Test
        void nonRecipientThrows() {
            when(currentDeveloperLookup.currentDeveloperIdElseThrow()).thenReturn(OTHER_USER_ID);
            when(
                feedbackRepository.findByIdAndWorkspaceIdAndRecipientUserIdAndDeliveryState(
                    FEEDBACK_ID,
                    WORKSPACE_ID,
                    OTHER_USER_ID,
                    FeedbackDeliveryState.DELIVERED
                )
            ).thenReturn(Optional.empty());

            var request = new FeedbackResponseRequestDTO(null, FeedbackResolution.ADDRESSED, null, null);
            assertThatThrownBy(() -> service.submitResponse(workspaceContext, FEEDBACK_ID, request)).isInstanceOf(
                EntityNotFoundException.class
            );
        }

        @Test
        void feedbackNotFoundThrows() {
            when(
                feedbackRepository.findByIdAndWorkspaceIdAndRecipientUserIdAndDeliveryState(
                    FEEDBACK_ID,
                    WORKSPACE_ID,
                    CONTRIBUTOR_ID,
                    FeedbackDeliveryState.DELIVERED
                )
            ).thenReturn(Optional.empty());

            var request = new FeedbackResponseRequestDTO(null, FeedbackResolution.ADDRESSED, null, null);
            assertThatThrownBy(() -> service.submitResponse(workspaceContext, FEEDBACK_ID, request)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }

    @Nested
    class GetLatestResponse {

        @Test
        void returnsLatestWhenPresent() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(
                feedbackRepository.findByIdAndWorkspaceIdAndRecipientUserIdAndDeliveryState(
                    FEEDBACK_ID,
                    WORKSPACE_ID,
                    CONTRIBUTOR_ID,
                    FeedbackDeliveryState.DELIVERED
                )
            ).thenReturn(Optional.of(feedback));

            appended.add(
                Reaction.builder()
                    .id(UUID.randomUUID())
                    .feedback(feedback)
                    .feedbackId(FEEDBACK_ID)
                    .reactorUserId(CONTRIBUTOR_ID)
                    .resolution(FeedbackResolution.ADDRESSED)
                    .createdAt(Instant.now())
                    .build()
            );

            Optional<FeedbackResponseDTO> result = service.getLatestResponse(workspaceContext, FEEDBACK_ID);

            assertThat(result).isPresent();
            assertThat(result.get().resolution()).isEqualTo(FeedbackResolution.ADDRESSED);
            assertThat(result.get().feedbackId()).isEqualTo(FEEDBACK_ID);
        }

        @Test
        void returnsEmptyWhenNone() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(
                feedbackRepository.findByIdAndWorkspaceIdAndRecipientUserIdAndDeliveryState(
                    FEEDBACK_ID,
                    WORKSPACE_ID,
                    CONTRIBUTOR_ID,
                    FeedbackDeliveryState.DELIVERED
                )
            ).thenReturn(Optional.of(feedback));

            Optional<FeedbackResponseDTO> result = service.getLatestResponse(workspaceContext, FEEDBACK_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("answers a signed-in non-developer with nothing rather than an error")
        void returnsEmptyForACallerWhoIsNotASyncedDeveloper() {
            // A read answers absence with absence — the contract CurrentDeveloperLookup states, and the one
            // the reflection and review-history surfaces already keep.
            when(currentDeveloperLookup.currentDeveloperId()).thenReturn(Optional.empty());

            assertThat(service.getLatestResponse(workspaceContext, FEEDBACK_ID)).isEmpty();
        }

        @Test
        void throwsWhenFeedbackNotInWorkspace() {
            when(
                feedbackRepository.findByIdAndWorkspaceIdAndRecipientUserIdAndDeliveryState(
                    FEEDBACK_ID,
                    WORKSPACE_ID,
                    CONTRIBUTOR_ID,
                    FeedbackDeliveryState.DELIVERED
                )
            ).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getLatestResponse(workspaceContext, FEEDBACK_ID)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }

    @Nested
    class GetEngagement {

        @Test
        @DisplayName("counts zero for a signed-in non-developer instead of failing")
        void returnsZeroesForACallerWhoIsNotASyncedDeveloper() {
            when(currentDeveloperLookup.currentDeveloperId()).thenReturn(Optional.empty());

            FeedbackEngagementDTO result = service.getEngagement(workspaceContext);

            assertThat(result.addressed()).isZero();
            assertThat(result.disputed()).isZero();
            assertThat(result.notApplicable()).isZero();
        }

        @Test
        void returnsCorrectCounts() {
            var addressedProjection = new ReactionRepository.ActionCountProjection() {
                @Override
                public String getAction() {
                    return FeedbackResolution.ADDRESSED.name();
                }

                @Override
                public Long getCount() {
                    return 3L;
                }
            };
            var disputedProjection = new ReactionRepository.ActionCountProjection() {
                @Override
                public String getAction() {
                    return FeedbackResolution.DISPUTED.name();
                }

                @Override
                public Long getCount() {
                    return 1L;
                }
            };

            when(reactionRepository.countByReactorAndWorkspaceGroupByAction(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of(addressedProjection, disputedProjection)
            );

            FeedbackEngagementDTO result = service.getEngagement(workspaceContext);

            assertThat(result.addressed()).isEqualTo(3L);
            assertThat(result.disputed()).isEqualTo(1L);
            assertThat(result.notApplicable()).isEqualTo(0L);
        }

        @Test
        @DisplayName("returns all zeros when no reaction exists")
        void returnsZerosWhenEmpty() {
            when(reactionRepository.countByReactorAndWorkspaceGroupByAction(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of()
            );

            FeedbackEngagementDTO result = service.getEngagement(workspaceContext);

            assertThat(result.addressed()).isZero();
            assertThat(result.disputed()).isZero();
            assertThat(result.notApplicable()).isZero();
        }
    }
}
