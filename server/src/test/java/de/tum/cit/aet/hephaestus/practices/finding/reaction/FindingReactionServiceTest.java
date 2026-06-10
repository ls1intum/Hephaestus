package de.tum.cit.aet.hephaestus.practices.finding.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.CreateFindingReactionDTO;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.FindingReactionDTO;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.FindingReactionEngagementDTO;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
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

class FindingReactionServiceTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Long CONTRIBUTOR_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;
    private static final UUID FINDING_ID = UUID.randomUUID();

    @Mock
    private FindingReactionRepository reactionRepository;

    @Mock
    private PracticeFindingRepository findingRepository;

    @Mock
    private UserRepository userRepository;

    @Captor
    private ArgumentCaptor<FindingReaction> feedbackCaptor;

    private FindingReactionService service;
    private WorkspaceContext workspaceContext;

    @BeforeEach
    void setUp() {
        service = new FindingReactionService(reactionRepository, findingRepository, userRepository);
        workspaceContext = new WorkspaceContext(WORKSPACE_ID, "test-ws", "Test WS", null, null, false, false, Set.of());
    }

    private PracticeFinding createFinding(Long contributorId) {
        User contributor = new User();
        contributor.setId(contributorId);
        return PracticeFinding.builder().id(FINDING_ID).contributor(contributor).build();
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    // Submit Feedback

    @Nested
    class SubmitFeedback {

        @Test
        void appliedFeedbackSaves() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));
            when(reactionRepository.save(any(FindingReaction.class))).thenAnswer(inv -> {
                FindingReaction fb = inv.getArgument(0);
                fb.onCreate();
                return fb;
            });

            var request = new CreateFindingReactionDTO(FindingReactionAction.APPLIED, null);
            FindingReactionDTO result = service.submitReaction(workspaceContext, FINDING_ID, request);

            assertThat(result.action()).isEqualTo(FindingReactionAction.APPLIED);
            assertThat(result.explanation()).isNull();

            verify(reactionRepository).save(feedbackCaptor.capture());
            FindingReaction saved = feedbackCaptor.getValue();
            assertThat(saved.getContributorId()).isEqualTo(CONTRIBUTOR_ID);
            assertThat(saved.getAction()).isEqualTo(FindingReactionAction.APPLIED);
        }

        @Test
        void disputedWithExplanationSaves() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));
            when(reactionRepository.save(any(FindingReaction.class))).thenAnswer(inv -> {
                FindingReaction fb = inv.getArgument(0);
                fb.onCreate();
                return fb;
            });

            var request = new CreateFindingReactionDTO(FindingReactionAction.DISPUTED, "The AI is wrong about this");
            FindingReactionDTO result = service.submitReaction(workspaceContext, FINDING_ID, request);

            assertThat(result.action()).isEqualTo(FindingReactionAction.DISPUTED);
            assertThat(result.explanation()).isEqualTo("The AI is wrong about this");
        }

        @Test
        void notApplicableSaves() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));
            when(reactionRepository.save(any(FindingReaction.class))).thenAnswer(inv -> {
                FindingReaction fb = inv.getArgument(0);
                fb.onCreate();
                return fb;
            });

            var request = new CreateFindingReactionDTO(
                FindingReactionAction.NOT_APPLICABLE,
                "Not relevant to my use case"
            );
            FindingReactionDTO result = service.submitReaction(workspaceContext, FINDING_ID, request);

            assertThat(result.action()).isEqualTo(FindingReactionAction.NOT_APPLICABLE);
        }

        @Test
        void disputedWithoutExplanationThrows() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));

            var request = new CreateFindingReactionDTO(FindingReactionAction.DISPUTED, null);
            assertThatThrownBy(() -> service.submitReaction(workspaceContext, FINDING_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Explanation is required");
        }

        @Test
        void disputedWithBlankExplanationThrows() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));

            var request = new CreateFindingReactionDTO(FindingReactionAction.DISPUTED, "   ");
            assertThatThrownBy(() -> service.submitReaction(workspaceContext, FINDING_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Explanation is required");
        }

        @Test
        void nonContributorThrows() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(OTHER_USER_ID));

            var request = new CreateFindingReactionDTO(FindingReactionAction.APPLIED, null);
            assertThatThrownBy(() -> service.submitReaction(workspaceContext, FINDING_ID, request))
                .isInstanceOf(AccessForbiddenException.class)
                .hasMessageContaining("contributor");
        }

        @Test
        void findingNotFoundThrows() {
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.empty());

            var request = new CreateFindingReactionDTO(FindingReactionAction.APPLIED, null);
            assertThatThrownBy(() -> service.submitReaction(workspaceContext, FINDING_ID, request)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }

    // Get Latest Feedback

    @Nested
    class GetLatestFeedback {

        @Test
        void returnsLatestWhenPresent() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));

            FindingReaction feedback = FindingReaction.builder()
                .id(UUID.randomUUID())
                .finding(finding)
                .findingId(FINDING_ID)
                .contributorId(CONTRIBUTOR_ID)
                .action(FindingReactionAction.APPLIED)
                .createdAt(Instant.now())
                .build();
            when(
                reactionRepository.findFirstByFindingIdAndContributorIdOrderByCreatedAtDesc(FINDING_ID, CONTRIBUTOR_ID)
            ).thenReturn(Optional.of(feedback));

            Optional<FindingReactionDTO> result = service.getLatestReaction(workspaceContext, FINDING_ID);

            assertThat(result).isPresent();
            assertThat(result.get().action()).isEqualTo(FindingReactionAction.APPLIED);
            assertThat(result.get().findingId()).isEqualTo(FINDING_ID);
        }

        @Test
        void returnsEmptyWhenNone() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));
            when(
                reactionRepository.findFirstByFindingIdAndContributorIdOrderByCreatedAtDesc(FINDING_ID, CONTRIBUTOR_ID)
            ).thenReturn(Optional.empty());

            Optional<FindingReactionDTO> result = service.getLatestReaction(workspaceContext, FINDING_ID);

            assertThat(result).isEmpty();
        }

        @Test
        void throwsWhenFindingNotInWorkspace() {
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getLatestReaction(workspaceContext, FINDING_ID)).isInstanceOf(
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

            var appliedProjection = new FindingReactionRepository.ActionCountProjection() {
                @Override
                public FindingReactionAction getAction() {
                    return FindingReactionAction.APPLIED;
                }

                @Override
                public Long getCount() {
                    return 3L;
                }
            };
            var disputedProjection = new FindingReactionRepository.ActionCountProjection() {
                @Override
                public FindingReactionAction getAction() {
                    return FindingReactionAction.DISPUTED;
                }

                @Override
                public Long getCount() {
                    return 1L;
                }
            };

            when(
                reactionRepository.countByContributorAndWorkspaceGroupByAction(CONTRIBUTOR_ID, WORKSPACE_ID)
            ).thenReturn(List.of(appliedProjection, disputedProjection));

            FindingReactionEngagementDTO result = service.getEngagement(workspaceContext);

            assertThat(result.applied()).isEqualTo(3L);
            assertThat(result.disputed()).isEqualTo(1L);
            assertThat(result.notApplicable()).isEqualTo(0L);
        }

        @Test
        @DisplayName("returns all zeros when no feedback exists")
        void returnsZerosWhenEmpty() {
            when(userRepository.getCurrentUserElseThrow()).thenReturn(createUser(CONTRIBUTOR_ID));
            when(
                reactionRepository.countByContributorAndWorkspaceGroupByAction(CONTRIBUTOR_ID, WORKSPACE_ID)
            ).thenReturn(List.of());

            FindingReactionEngagementDTO result = service.getEngagement(workspaceContext);

            assertThat(result.applied()).isZero();
            assertThat(result.disputed()).isZero();
            assertThat(result.notApplicable()).isZero();
        }
    }

    // Get Latest Feedback By Finding IDs

    @Nested
    class GetLatestFeedbackByFindingIds {

        @Test
        void returnsEmptyForEmptyInput() {
            Map<UUID, FindingReactionDTO> result = service.getLatestReactionByFindingIds(List.of(), CONTRIBUTOR_ID);

            assertThat(result).isEmpty();
        }

        @Test
        void returnsMappedByFindingId() {
            UUID findingId1 = UUID.randomUUID();
            UUID findingId2 = UUID.randomUUID();
            PracticeFinding finding1 = PracticeFinding.builder().id(findingId1).build();
            PracticeFinding finding2 = PracticeFinding.builder().id(findingId2).build();

            FindingReaction fb1 = FindingReaction.builder()
                .id(UUID.randomUUID())
                .finding(finding1)
                .findingId(findingId1)
                .contributorId(CONTRIBUTOR_ID)
                .action(FindingReactionAction.APPLIED)
                .createdAt(Instant.now())
                .build();
            FindingReaction fb2 = FindingReaction.builder()
                .id(UUID.randomUUID())
                .finding(finding2)
                .findingId(findingId2)
                .contributorId(CONTRIBUTOR_ID)
                .action(FindingReactionAction.DISPUTED)
                .explanation("Wrong")
                .createdAt(Instant.now())
                .build();

            when(
                reactionRepository.findLatestByFindingIdsAndContributor(List.of(findingId1, findingId2), CONTRIBUTOR_ID)
            ).thenReturn(List.of(fb1, fb2));

            Map<UUID, FindingReactionDTO> result = service.getLatestReactionByFindingIds(
                List.of(findingId1, findingId2),
                CONTRIBUTOR_ID
            );

            assertThat(result).hasSize(2);
            assertThat(result.get(findingId1).action()).isEqualTo(FindingReactionAction.APPLIED);
            assertThat(result.get(findingId2).action()).isEqualTo(FindingReactionAction.DISPUTED);
        }
    }
}
