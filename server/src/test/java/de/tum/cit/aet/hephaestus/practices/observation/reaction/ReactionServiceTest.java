package de.tum.cit.aet.hephaestus.practices.observation.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.dto.CreateReactionDTO;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.dto.ReactionDTO;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.dto.ReactionEngagementDTO;
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

class ReactionServiceTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Long CONTRIBUTOR_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;
    private static final UUID FEEDBACK_ID = UUID.randomUUID();

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private UserRepository userRepository;

    @Captor
    private ArgumentCaptor<Reaction> reactionCaptor;

    private ReactionService service;
    private WorkspaceContext workspaceContext;

    @BeforeEach
    void setUp() {
        service = new ReactionService(reactionRepository, feedbackRepository, userRepository);
        workspaceContext = new WorkspaceContext(WORKSPACE_ID, "test-ws", "Test WS", null, null, false, false, Set.of());
    }

    private Feedback createFeedback(Long recipientUserId) {
        return Feedback.builder().id(FEEDBACK_ID).recipientUserId(recipientUserId).workspaceId(WORKSPACE_ID).build();
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    // Submit Reaction

    @Nested
    class SubmitReaction {

        @Test
        void appliedFeedbackSaves() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(
                Optional.of(feedback)
            );
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));
            when(reactionRepository.save(any(Reaction.class))).thenAnswer(inv -> {
                Reaction reaction = inv.getArgument(0);
                reaction.onCreate();
                return reaction;
            });

            var request = new CreateReactionDTO(ReactionAction.ADDRESSED, null);
            ReactionDTO result = service.submitReaction(workspaceContext, FEEDBACK_ID, request);

            assertThat(result.action()).isEqualTo(ReactionAction.ADDRESSED);
            assertThat(result.explanation()).isNull();

            verify(reactionRepository).save(reactionCaptor.capture());
            Reaction saved = reactionCaptor.getValue();
            assertThat(saved.getReactorUserId()).isEqualTo(CONTRIBUTOR_ID);
            assertThat(saved.getAction()).isEqualTo(ReactionAction.ADDRESSED);
        }

        @Test
        void disputedWithExplanationSaves() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(
                Optional.of(feedback)
            );
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));
            when(reactionRepository.save(any(Reaction.class))).thenAnswer(inv -> {
                Reaction reaction = inv.getArgument(0);
                reaction.onCreate();
                return reaction;
            });

            var request = new CreateReactionDTO(ReactionAction.DISPUTED, "The AI is wrong about this");
            ReactionDTO result = service.submitReaction(workspaceContext, FEEDBACK_ID, request);

            assertThat(result.action()).isEqualTo(ReactionAction.DISPUTED);
            assertThat(result.explanation()).isEqualTo("The AI is wrong about this");
        }

        @Test
        void notApplicableSaves() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(
                Optional.of(feedback)
            );
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));
            when(reactionRepository.save(any(Reaction.class))).thenAnswer(inv -> {
                Reaction reaction = inv.getArgument(0);
                reaction.onCreate();
                return reaction;
            });

            var request = new CreateReactionDTO(ReactionAction.NOT_APPLICABLE, "Not relevant to my use case");
            ReactionDTO result = service.submitReaction(workspaceContext, FEEDBACK_ID, request);

            assertThat(result.action()).isEqualTo(ReactionAction.NOT_APPLICABLE);
        }

        @Test
        void disputedWithoutExplanationThrows() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(
                Optional.of(feedback)
            );
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));

            var request = new CreateReactionDTO(ReactionAction.DISPUTED, null);
            assertThatThrownBy(() -> service.submitReaction(workspaceContext, FEEDBACK_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Explanation is required");
        }

        @Test
        void disputedWithBlankExplanationThrows() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(
                Optional.of(feedback)
            );
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));

            var request = new CreateReactionDTO(ReactionAction.DISPUTED, "   ");
            assertThatThrownBy(() -> service.submitReaction(workspaceContext, FEEDBACK_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Explanation is required");
        }

        @Test
        void nonRecipientThrows() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(
                Optional.of(feedback)
            );
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(OTHER_USER_ID));

            var request = new CreateReactionDTO(ReactionAction.ADDRESSED, null);
            assertThatThrownBy(() -> service.submitReaction(workspaceContext, FEEDBACK_ID, request))
                .isInstanceOf(AccessForbiddenException.class)
                .hasMessageContaining("recipient");
        }

        @Test
        void feedbackNotFoundThrows() {
            when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(Optional.empty());

            var request = new CreateReactionDTO(ReactionAction.ADDRESSED, null);
            assertThatThrownBy(() -> service.submitReaction(workspaceContext, FEEDBACK_ID, request)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }

    // Get Latest Reaction

    @Nested
    class GetLatestReaction {

        @Test
        void returnsLatestWhenPresent() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(
                Optional.of(feedback)
            );
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));

            Reaction reaction = Reaction.builder()
                .id(UUID.randomUUID())
                .feedback(feedback)
                .feedbackId(FEEDBACK_ID)
                .reactorUserId(CONTRIBUTOR_ID)
                .action(ReactionAction.ADDRESSED)
                .createdAt(Instant.now())
                .build();
            when(
                reactionRepository.findFirstByFeedbackIdAndReactorUserIdOrderByCreatedAtDesc(
                    FEEDBACK_ID,
                    CONTRIBUTOR_ID
                )
            ).thenReturn(Optional.of(reaction));

            Optional<ReactionDTO> result = service.getLatestReaction(workspaceContext, FEEDBACK_ID);

            assertThat(result).isPresent();
            assertThat(result.get().action()).isEqualTo(ReactionAction.ADDRESSED);
            assertThat(result.get().feedbackId()).isEqualTo(FEEDBACK_ID);
        }

        @Test
        void returnsEmptyWhenNone() {
            Feedback feedback = createFeedback(CONTRIBUTOR_ID);
            when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(
                Optional.of(feedback)
            );
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));
            when(
                reactionRepository.findFirstByFeedbackIdAndReactorUserIdOrderByCreatedAtDesc(
                    FEEDBACK_ID,
                    CONTRIBUTOR_ID
                )
            ).thenReturn(Optional.empty());

            Optional<ReactionDTO> result = service.getLatestReaction(workspaceContext, FEEDBACK_ID);

            assertThat(result).isEmpty();
        }

        @Test
        void throwsWhenFeedbackNotInWorkspace() {
            when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getLatestReaction(workspaceContext, FEEDBACK_ID)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }

    // Get Engagement

    @Nested
    class GetEngagement {

        @Test
        void returnsCorrectCounts() {
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));

            var appliedProjection = new ReactionRepository.ActionCountProjection() {
                @Override
                public ReactionAction getAction() {
                    return ReactionAction.ADDRESSED;
                }

                @Override
                public Long getCount() {
                    return 3L;
                }
            };
            var disputedProjection = new ReactionRepository.ActionCountProjection() {
                @Override
                public ReactionAction getAction() {
                    return ReactionAction.DISPUTED;
                }

                @Override
                public Long getCount() {
                    return 1L;
                }
            };

            when(reactionRepository.countByReactorAndWorkspaceGroupByAction(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of(appliedProjection, disputedProjection)
            );

            ReactionEngagementDTO result = service.getEngagement(workspaceContext);

            assertThat(result.addressed()).isEqualTo(3L);
            assertThat(result.disputed()).isEqualTo(1L);
            assertThat(result.notApplicable()).isEqualTo(0L);
        }

        @Test
        @DisplayName("returns all zeros when no reaction exists")
        void returnsZerosWhenEmpty() {
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));
            when(reactionRepository.countByReactorAndWorkspaceGroupByAction(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of()
            );

            ReactionEngagementDTO result = service.getEngagement(workspaceContext);

            assertThat(result.addressed()).isZero();
            assertThat(result.disputed()).isZero();
            assertThat(result.notApplicable()).isZero();
        }
    }
}
