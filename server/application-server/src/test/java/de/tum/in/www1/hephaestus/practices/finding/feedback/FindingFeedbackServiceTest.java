package de.tum.in.www1.hephaestus.practices.finding.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.core.exception.AccessForbiddenException;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.AuthenticatedUserService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.in.www1.hephaestus.practices.finding.feedback.dto.CreateFindingFeedbackDTO;
import de.tum.in.www1.hephaestus.practices.finding.feedback.dto.FindingFeedbackDTO;
import de.tum.in.www1.hephaestus.practices.finding.feedback.dto.FindingFeedbackEngagementDTO;
import de.tum.in.www1.hephaestus.practices.model.PracticeFinding;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
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

@DisplayName("FindingFeedbackService")
class FindingFeedbackServiceTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Long CONTRIBUTOR_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;
    private static final UUID FINDING_ID = UUID.randomUUID();

    @Mock
    private FindingFeedbackRepository feedbackRepository;

    @Mock
    private PracticeFindingRepository findingRepository;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Captor
    private ArgumentCaptor<FindingFeedback> feedbackCaptor;

    private FindingFeedbackService service;
    private WorkspaceContext workspaceContext;

    @BeforeEach
    void setUp() {
        service = new FindingFeedbackService(feedbackRepository, findingRepository, authenticatedUserService);
        workspaceContext = new WorkspaceContext(WORKSPACE_ID, "test-ws", "Test WS", null, null, false, Set.of());
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

    // ── Submit Feedback ──────────────────────────────────────────────────

    @Nested
    @DisplayName("submitFeedback")
    class SubmitFeedback {

        @Test
        @DisplayName("APPLIED feedback saves successfully")
        void appliedFeedbackSaves() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(List.of(createUser(CONTRIBUTOR_ID)));
            when(feedbackRepository.save(any(FindingFeedback.class))).thenAnswer(inv -> {
                FindingFeedback fb = inv.getArgument(0);
                fb.onCreate();
                return fb;
            });

            var request = new CreateFindingFeedbackDTO(FindingFeedbackAction.APPLIED, null);
            FindingFeedbackDTO result = service.submitFeedback(workspaceContext, FINDING_ID, request);

            assertThat(result.action()).isEqualTo(FindingFeedbackAction.APPLIED);
            assertThat(result.explanation()).isNull();

            verify(feedbackRepository).save(feedbackCaptor.capture());
            FindingFeedback saved = feedbackCaptor.getValue();
            assertThat(saved.getContributorId()).isEqualTo(CONTRIBUTOR_ID);
            assertThat(saved.getAction()).isEqualTo(FindingFeedbackAction.APPLIED);
        }

        @Test
        @DisplayName("DISPUTED feedback with explanation saves successfully")
        void disputedWithExplanationSaves() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(List.of(createUser(CONTRIBUTOR_ID)));
            when(feedbackRepository.save(any(FindingFeedback.class))).thenAnswer(inv -> {
                FindingFeedback fb = inv.getArgument(0);
                fb.onCreate();
                return fb;
            });

            var request = new CreateFindingFeedbackDTO(FindingFeedbackAction.DISPUTED, "The AI is wrong about this");
            FindingFeedbackDTO result = service.submitFeedback(workspaceContext, FINDING_ID, request);

            assertThat(result.action()).isEqualTo(FindingFeedbackAction.DISPUTED);
            assertThat(result.explanation()).isEqualTo("The AI is wrong about this");
        }

        @Test
        @DisplayName("NOT_APPLICABLE feedback saves successfully")
        void notApplicableSaves() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(List.of(createUser(CONTRIBUTOR_ID)));
            when(feedbackRepository.save(any(FindingFeedback.class))).thenAnswer(inv -> {
                FindingFeedback fb = inv.getArgument(0);
                fb.onCreate();
                return fb;
            });

            var request = new CreateFindingFeedbackDTO(
                FindingFeedbackAction.NOT_APPLICABLE,
                "Not relevant to my use case"
            );
            FindingFeedbackDTO result = service.submitFeedback(workspaceContext, FINDING_ID, request);

            assertThat(result.action()).isEqualTo(FindingFeedbackAction.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("DISPUTED without explanation throws IllegalArgumentException")
        void disputedWithoutExplanationThrows() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(List.of(createUser(CONTRIBUTOR_ID)));

            var request = new CreateFindingFeedbackDTO(FindingFeedbackAction.DISPUTED, null);
            assertThatThrownBy(() -> service.submitFeedback(workspaceContext, FINDING_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Explanation is required");
        }

        @Test
        @DisplayName("DISPUTED with blank explanation throws IllegalArgumentException")
        void disputedWithBlankExplanationThrows() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(List.of(createUser(CONTRIBUTOR_ID)));

            var request = new CreateFindingFeedbackDTO(FindingFeedbackAction.DISPUTED, "   ");
            assertThatThrownBy(() -> service.submitFeedback(workspaceContext, FINDING_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Explanation is required");
        }

        @Test
        @DisplayName("non-contributor throws AccessForbiddenException")
        void nonContributorThrows() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(List.of(createUser(OTHER_USER_ID)));

            var request = new CreateFindingFeedbackDTO(FindingFeedbackAction.APPLIED, null);
            assertThatThrownBy(() -> service.submitFeedback(workspaceContext, FINDING_ID, request))
                .isInstanceOf(AccessForbiddenException.class)
                .hasMessageContaining("contributor");
        }

        @Test
        @DisplayName("contributor matched via a linked provider row (multi-IdP) is allowed to submit")
        void linkedProviderRowAllowedAsContributor() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            // First linked row is a different provider; the contributor row is among the
            // caller's linked identities, so access must still be granted.
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(
                List.of(createUser(OTHER_USER_ID), createUser(CONTRIBUTOR_ID))
            );
            when(feedbackRepository.save(any(FindingFeedback.class))).thenAnswer(inv -> {
                FindingFeedback fb = inv.getArgument(0);
                fb.onCreate();
                return fb;
            });

            var request = new CreateFindingFeedbackDTO(FindingFeedbackAction.APPLIED, null);
            FindingFeedbackDTO result = service.submitFeedback(workspaceContext, FINDING_ID, request);

            assertThat(result.action()).isEqualTo(FindingFeedbackAction.APPLIED);
            verify(feedbackRepository).save(feedbackCaptor.capture());
            assertThat(feedbackCaptor.getValue().getContributorId()).isEqualTo(CONTRIBUTOR_ID);
        }

        @Test
        @DisplayName("finding not found throws EntityNotFoundException")
        void findingNotFoundThrows() {
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.empty());

            var request = new CreateFindingFeedbackDTO(FindingFeedbackAction.APPLIED, null);
            assertThatThrownBy(() -> service.submitFeedback(workspaceContext, FINDING_ID, request)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }

    // ── Get Latest Feedback ──────────────────────────────────────────────

    @Nested
    @DisplayName("getLatestFeedback")
    class GetLatestFeedback {

        @Test
        @DisplayName("returns latest feedback when present")
        void returnsLatestWhenPresent() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(List.of(createUser(CONTRIBUTOR_ID)));

            FindingFeedback feedback = FindingFeedback.builder()
                .id(UUID.randomUUID())
                .finding(finding)
                .findingId(FINDING_ID)
                .contributorId(CONTRIBUTOR_ID)
                .action(FindingFeedbackAction.APPLIED)
                .createdAt(Instant.now())
                .build();
            when(
                feedbackRepository.findLatestByFindingIdAndContributors(FINDING_ID, List.of(CONTRIBUTOR_ID))
            ).thenReturn(List.of(feedback));

            Optional<FindingFeedbackDTO> result = service.getLatestFeedback(workspaceContext, FINDING_ID);

            assertThat(result).isPresent();
            assertThat(result.get().action()).isEqualTo(FindingFeedbackAction.APPLIED);
            assertThat(result.get().findingId()).isEqualTo(FINDING_ID);
        }

        @Test
        @DisplayName("returns empty when no feedback exists")
        void returnsEmptyWhenNone() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(List.of(createUser(CONTRIBUTOR_ID)));
            when(
                feedbackRepository.findLatestByFindingIdAndContributors(FINDING_ID, List.of(CONTRIBUTOR_ID))
            ).thenReturn(List.of());

            Optional<FindingFeedbackDTO> result = service.getLatestFeedback(workspaceContext, FINDING_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns newest feedback across linked contributor rows")
        void returnsNewestFeedbackAcrossLinkedRows() {
            Long secondContributorId = 11L;
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(
                List.of(createUser(CONTRIBUTOR_ID), createUser(secondContributorId))
            );

            FindingFeedback older = FindingFeedback.builder()
                .id(UUID.randomUUID())
                .finding(finding)
                .findingId(FINDING_ID)
                .contributorId(CONTRIBUTOR_ID)
                .action(FindingFeedbackAction.APPLIED)
                .createdAt(Instant.now().minusSeconds(60))
                .build();
            FindingFeedback newer = FindingFeedback.builder()
                .id(UUID.randomUUID())
                .finding(finding)
                .findingId(FINDING_ID)
                .contributorId(secondContributorId)
                .action(FindingFeedbackAction.DISPUTED)
                .createdAt(Instant.now())
                .build();

            when(
                feedbackRepository.findLatestByFindingIdAndContributors(
                    FINDING_ID,
                    List.of(CONTRIBUTOR_ID, secondContributorId)
                )
            ).thenReturn(List.of(older, newer));

            Optional<FindingFeedbackDTO> result = service.getLatestFeedback(workspaceContext, FINDING_ID);

            assertThat(result).isPresent();
            assertThat(result.get().action()).isEqualTo(FindingFeedbackAction.DISPUTED);
        }

        @Test
        @DisplayName("throws AccessForbiddenException when no linked identity is resolved")
        void throwsWhenNoLinkedIdentityResolved() {
            PracticeFinding finding = createFinding(CONTRIBUTOR_ID);
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.of(finding));
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(List.of());

            assertThatThrownBy(() -> service.getLatestFeedback(workspaceContext, FINDING_ID))
                .isInstanceOf(AccessForbiddenException.class)
                .hasMessageContaining("User not authenticated");
        }

        @Test
        @DisplayName("throws EntityNotFoundException when finding not in workspace")
        void throwsWhenFindingNotInWorkspace() {
            when(findingRepository.findByIdAndWorkspaceId(FINDING_ID, WORKSPACE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getLatestFeedback(workspaceContext, FINDING_ID)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }

    // ── Get Engagement ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getEngagement")
    class GetEngagement {

        @Test
        @DisplayName("returns correct counts with workspace scoping")
        void returnsCorrectCounts() {
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(List.of(createUser(CONTRIBUTOR_ID)));

            var appliedProjection = new FindingFeedbackRepository.ActionCountProjection() {
                @Override
                public FindingFeedbackAction getAction() {
                    return FindingFeedbackAction.APPLIED;
                }

                @Override
                public Long getCount() {
                    return 3L;
                }
            };
            var disputedProjection = new FindingFeedbackRepository.ActionCountProjection() {
                @Override
                public FindingFeedbackAction getAction() {
                    return FindingFeedbackAction.DISPUTED;
                }

                @Override
                public Long getCount() {
                    return 1L;
                }
            };

            when(
                feedbackRepository.countByContributorsAndWorkspaceGroupByAction(List.of(CONTRIBUTOR_ID), WORKSPACE_ID)
            ).thenReturn(List.of(appliedProjection, disputedProjection));

            FindingFeedbackEngagementDTO result = service.getEngagement(workspaceContext);

            assertThat(result.applied()).isEqualTo(3L);
            assertThat(result.disputed()).isEqualTo(1L);
            assertThat(result.notApplicable()).isEqualTo(0L);
        }

        @Test
        @DisplayName("returns all zeros when no feedback exists")
        void returnsZerosWhenEmpty() {
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(List.of(createUser(CONTRIBUTOR_ID)));
            when(
                feedbackRepository.countByContributorsAndWorkspaceGroupByAction(List.of(CONTRIBUTOR_ID), WORKSPACE_ID)
            ).thenReturn(List.of());

            FindingFeedbackEngagementDTO result = service.getEngagement(workspaceContext);

            assertThat(result.applied()).isZero();
            assertThat(result.disputed()).isZero();
            assertThat(result.notApplicable()).isZero();
        }

        @Test
        @DisplayName("queries engagement across all linked contributor rows")
        void queriesEngagementAcrossLinkedRows() {
            Long secondContributorId = 11L;
            when(authenticatedUserService.findAllLinkedUsers()).thenReturn(
                List.of(createUser(CONTRIBUTOR_ID), createUser(secondContributorId))
            );

            var appliedProjection = new FindingFeedbackRepository.ActionCountProjection() {
                @Override
                public FindingFeedbackAction getAction() {
                    return FindingFeedbackAction.APPLIED;
                }

                @Override
                public Long getCount() {
                    return 5L;
                }
            };

            when(
                feedbackRepository.countByContributorsAndWorkspaceGroupByAction(
                    List.of(CONTRIBUTOR_ID, secondContributorId),
                    WORKSPACE_ID
                )
            ).thenReturn(List.of(appliedProjection));

            FindingFeedbackEngagementDTO result = service.getEngagement(workspaceContext);

            assertThat(result.applied()).isEqualTo(5L);
            assertThat(result.disputed()).isZero();
            assertThat(result.notApplicable()).isZero();
        }
    }

    // ── Get Latest Feedback By Finding IDs ───────────────────────────────

    @Nested
    @DisplayName("getLatestFeedbackByFindingIds")
    class GetLatestFeedbackByFindingIds {

        @Test
        @DisplayName("returns empty map for empty input")
        void returnsEmptyForEmptyInput() {
            Map<UUID, FindingFeedbackDTO> result = service.getLatestFeedbackByFindingIds(List.of(), CONTRIBUTOR_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns map keyed by finding ID")
        void returnsMappedByFindingId() {
            UUID findingId1 = UUID.randomUUID();
            UUID findingId2 = UUID.randomUUID();
            PracticeFinding finding1 = PracticeFinding.builder().id(findingId1).build();
            PracticeFinding finding2 = PracticeFinding.builder().id(findingId2).build();

            FindingFeedback fb1 = FindingFeedback.builder()
                .id(UUID.randomUUID())
                .finding(finding1)
                .findingId(findingId1)
                .contributorId(CONTRIBUTOR_ID)
                .action(FindingFeedbackAction.APPLIED)
                .createdAt(Instant.now())
                .build();
            FindingFeedback fb2 = FindingFeedback.builder()
                .id(UUID.randomUUID())
                .finding(finding2)
                .findingId(findingId2)
                .contributorId(CONTRIBUTOR_ID)
                .action(FindingFeedbackAction.DISPUTED)
                .explanation("Wrong")
                .createdAt(Instant.now())
                .build();

            when(
                feedbackRepository.findLatestByFindingIdsAndContributor(List.of(findingId1, findingId2), CONTRIBUTOR_ID)
            ).thenReturn(List.of(fb1, fb2));

            Map<UUID, FindingFeedbackDTO> result = service.getLatestFeedbackByFindingIds(
                List.of(findingId1, findingId2),
                CONTRIBUTOR_ID
            );

            assertThat(result).hasSize(2);
            assertThat(result.get(findingId1).action()).isEqualTo(FindingFeedbackAction.APPLIED);
            assertThat(result.get(findingId2).action()).isEqualTo(FindingFeedbackAction.DISPUTED);
        }
    }
}
