package de.tum.in.www1.hephaestus.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.activity.ActivityEvent;
import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.activity.ActivityTargetType;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.profile.dto.ProfileDTO;
import de.tum.in.www1.hephaestus.profile.dto.ProfileReviewActivityDTO;
import de.tum.in.www1.hephaestus.workspace.WorkspaceContributionActivityService;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for UserProfileService.
 *
 * <p>Tests verify the CQRS pattern: ActivityEvent is the source of truth for
 * what activity exists (same as leaderboard), with entity details hydrated
 * from gitprovider tables.
 */
@Tag("unit")
@DisplayName("UserProfileService")
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final String USER_LOGIN = "alice";
    private static final Instant AFTER = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant BEFORE = Instant.parse("2024-01-08T00:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileRepositoryQueryRepository profileRepositoryQueryRepository;

    @Mock
    private ProfilePullRequestQueryRepository profilePullRequestQueryRepository;

    @Mock
    private PullRequestReviewRepository pullRequestReviewRepository;

    @Mock
    private IssueCommentRepository issueCommentRepository;

    @Mock
    private ProfileReviewActivityAssembler reviewActivityAssembler;

    @Mock
    private WorkspaceMembershipService workspaceMembershipService;

    @Mock
    private WorkspaceContributionActivityService workspaceContributionActivityService;

    @Mock
    private ProfileActivityQueryService profileActivityQueryService;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private ActivityEventRepository activityEventRepository;

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(
            userRepository,
            profileRepositoryQueryRepository,
            profilePullRequestQueryRepository,
            pullRequestReviewRepository,
            issueCommentRepository,
            reviewActivityAssembler,
            workspaceMembershipService,
            workspaceContributionActivityService,
            profileActivityQueryService,
            pullRequestRepository,
            activityEventRepository
        );
    }

    @Nested
    @DisplayName("CQRS Architecture Tests")
    class CqrsArchitectureTests {

        @Test
        @DisplayName("queries ActivityEvent first then hydrates from gitprovider")
        void queriesActivityEventFirstThenHydrates() {
            // Arrange
            User user = createUser(USER_ID, USER_LOGIN);
            Repository repo = createRepository(200L);
            PullRequest pr = createPullRequest(300L, user, repo);
            PullRequestReview review = createReview(400L, user, pr);

            when(userRepository.findByLogin(USER_LOGIN)).thenReturn(Optional.of(user));
            when(profilePullRequestQueryRepository.findAssignedByLoginAndStates(any(), any(), any())).thenReturn(
                List.of()
            );
            when(profileRepositoryQueryRepository.findContributedByLogin(any(), any())).thenReturn(List.of());

            // ActivityEvent is the source of truth
            ActivityEvent event = createActivityEvent(WORKSPACE_ID, USER_ID, 400L, ActivityTargetType.REVIEW, 15.0);
            when(
                activityEventRepository.findProfileActivityByActorInTimeframe(
                    eq(WORKSPACE_ID),
                    eq(USER_ID),
                    any(),
                    any()
                )
            ).thenReturn(List.of(event));

            // Hydrate review details from gitprovider
            when(pullRequestReviewRepository.findAllByIdWithRelations(Set.of(400L))).thenReturn(List.of(review));

            // Mock assembler
            when(reviewActivityAssembler.assemble(eq(review), eq(15))).thenReturn(createProfileReviewDTO(400L, 15));

            // Act
            Optional<ProfileDTO> result = service.getUserProfile(USER_LOGIN, WORKSPACE_ID, AFTER, BEFORE);

            // Assert
            assertThat(result).isPresent();

            // Verify ActivityEvent queried first (source of truth)
            verify(activityEventRepository).findProfileActivityByActorInTimeframe(
                eq(WORKSPACE_ID),
                eq(USER_ID),
                any(),
                any()
            );

            // Verify entity hydrated from gitprovider
            verify(pullRequestReviewRepository).findAllByIdWithRelations(Set.of(400L));

            // Verify assembler was called with XP from ActivityEvent
            verify(reviewActivityAssembler).assemble(eq(review), eq(15));
        }

        @Test
        @DisplayName("returns empty activity when no ActivityEvents exist")
        void returnsEmptyWhenNoActivityEvents() {
            // Arrange
            User user = createUser(USER_ID, USER_LOGIN);

            when(userRepository.findByLogin(USER_LOGIN)).thenReturn(Optional.of(user));
            when(profilePullRequestQueryRepository.findAssignedByLoginAndStates(any(), any(), any())).thenReturn(
                List.of()
            );
            when(profileRepositoryQueryRepository.findContributedByLogin(any(), any())).thenReturn(List.of());

            // No ActivityEvents in ledger
            when(
                activityEventRepository.findProfileActivityByActorInTimeframe(
                    eq(WORKSPACE_ID),
                    eq(USER_ID),
                    any(),
                    any()
                )
            ).thenReturn(List.of());

            // Act
            Optional<ProfileDTO> result = service.getUserProfile(USER_LOGIN, WORKSPACE_ID, AFTER, BEFORE);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().reviewActivity()).isEmpty();

            // Verify no hydration queries (efficiency)
            verifyNoInteractions(pullRequestReviewRepository);
            verifyNoInteractions(issueCommentRepository);
        }

        @Test
        @DisplayName("batch-fetches reviews and comments efficiently (no N+1)")
        void batchFetchesEntitiesEfficiently() {
            // Arrange
            User user = createUser(USER_ID, USER_LOGIN);
            Repository repo = createRepository(200L);
            PullRequest pr = createPullRequest(300L, user, repo);
            PullRequestReview review1 = createReview(400L, user, pr);
            PullRequestReview review2 = createReview(401L, user, pr);
            IssueComment comment = createIssueComment(500L, user, pr);

            when(userRepository.findByLogin(USER_LOGIN)).thenReturn(Optional.of(user));
            when(profilePullRequestQueryRepository.findAssignedByLoginAndStates(any(), any(), any())).thenReturn(
                List.of()
            );
            when(profileRepositoryQueryRepository.findContributedByLogin(any(), any())).thenReturn(List.of());

            // ActivityEvents for 2 reviews + 1 comment
            ActivityEvent event1 = createActivityEvent(WORKSPACE_ID, USER_ID, 400L, ActivityTargetType.REVIEW, 10.0);
            ActivityEvent event2 = createActivityEvent(WORKSPACE_ID, USER_ID, 401L, ActivityTargetType.REVIEW, 20.0);
            ActivityEvent event3 = createActivityEvent(
                WORKSPACE_ID,
                USER_ID,
                500L,
                ActivityTargetType.ISSUE_COMMENT,
                5.0
            );
            when(
                activityEventRepository.findProfileActivityByActorInTimeframe(
                    eq(WORKSPACE_ID),
                    eq(USER_ID),
                    any(),
                    any()
                )
            ).thenReturn(List.of(event1, event2, event3));

            // Batch hydration
            when(pullRequestReviewRepository.findAllByIdWithRelations(Set.of(400L, 401L))).thenReturn(
                List.of(review1, review2)
            );
            when(issueCommentRepository.findAllByIdWithRelations(Set.of(500L))).thenReturn(List.of(comment));

            // Mock assembler returns
            when(reviewActivityAssembler.assemble(eq(review1), eq(10))).thenReturn(createProfileReviewDTO(400L, 10));
            when(reviewActivityAssembler.assemble(eq(review2), eq(20))).thenReturn(createProfileReviewDTO(401L, 20));
            when(reviewActivityAssembler.assemble(eq(comment), eq(5))).thenReturn(createProfileReviewDTO(500L, 5));

            // Act
            service.getUserProfile(USER_LOGIN, WORKSPACE_ID, AFTER, BEFORE);

            // Assert: Single batch query for each entity type (no N+1)
            verify(pullRequestReviewRepository, times(1)).findAllByIdWithRelations(any());
            verify(issueCommentRepository, times(1)).findAllByIdWithRelations(any());

            // Verify each item assembled with its correct XP from ActivityEvent
            verify(reviewActivityAssembler).assemble(eq(review1), eq(10));
            verify(reviewActivityAssembler).assemble(eq(review2), eq(20));
            verify(reviewActivityAssembler).assemble(eq(comment), eq(5));
        }

        @Test
        @DisplayName("skips missing entities gracefully (logs warning)")
        void skipsMissingEntitiesGracefully() {
            // Arrange
            User user = createUser(USER_ID, USER_LOGIN);

            when(userRepository.findByLogin(USER_LOGIN)).thenReturn(Optional.of(user));
            when(profilePullRequestQueryRepository.findAssignedByLoginAndStates(any(), any(), any())).thenReturn(
                List.of()
            );
            when(profileRepositoryQueryRepository.findContributedByLogin(any(), any())).thenReturn(List.of());

            // ActivityEvent exists but entity was deleted
            ActivityEvent event = createActivityEvent(WORKSPACE_ID, USER_ID, 999L, ActivityTargetType.REVIEW, 15.0);
            when(
                activityEventRepository.findProfileActivityByActorInTimeframe(
                    eq(WORKSPACE_ID),
                    eq(USER_ID),
                    any(),
                    any()
                )
            ).thenReturn(List.of(event));

            // Entity not found in gitprovider
            when(pullRequestReviewRepository.findAllByIdWithRelations(Set.of(999L))).thenReturn(List.of());

            // Act
            Optional<ProfileDTO> result = service.getUserProfile(USER_LOGIN, WORKSPACE_ID, AFTER, BEFORE);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().reviewActivity()).isEmpty();

            // Assembler should not be called for missing entity
            verifyNoInteractions(reviewActivityAssembler);
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private User createUser(Long id, String login) {
        User user = new User();
        user.setId(id);
        user.setLogin(login);
        return user;
    }

    private Repository createRepository(Long id) {
        Repository repo = new Repository();
        repo.setId(id);
        repo.setName("test-repo");
        return repo;
    }

    private PullRequest createPullRequest(Long id, User author, Repository repo) {
        PullRequest pr = new PullRequest();
        pr.setId(id);
        pr.setAuthor(author);
        pr.setRepository(repo);
        pr.setNumber(1);
        pr.setTitle("Test PR");
        pr.setHtmlUrl("https://github.com/test/pr/1");
        return pr;
    }

    private PullRequestReview createReview(Long id, User author, PullRequest pr) {
        PullRequestReview review = new PullRequestReview();
        review.setId(id);
        review.setAuthor(author);
        review.setPullRequest(pr);
        review.setState(PullRequestReview.State.APPROVED);
        review.setSubmittedAt(Instant.now());
        review.setHtmlUrl("https://github.com/test/review/1");
        return review;
    }

    private IssueComment createIssueComment(Long id, User author, Issue issue) {
        IssueComment comment = new IssueComment();
        comment.setId(id);
        comment.setAuthor(author);
        comment.setIssue(issue);
        comment.setCreatedAt(Instant.now());
        comment.setHtmlUrl("https://github.com/test/comment/1");
        return comment;
    }

    private ActivityEvent createActivityEvent(
        Long workspaceId,
        Long actorId,
        Long targetId,
        ActivityTargetType targetType,
        Double xp
    ) {
        return ActivityEvent.builder()
            .targetId(targetId)
            .targetType(targetType.getValue())
            .xp(xp)
            .occurredAt(Instant.now())
            .eventType(
                targetType == ActivityTargetType.REVIEW
                    ? ActivityEventType.REVIEW_APPROVED
                    : ActivityEventType.COMMENT_CREATED
            )
            .eventKey(
                ActivityEvent.buildKey(
                    targetType == ActivityTargetType.REVIEW
                        ? ActivityEventType.REVIEW_APPROVED
                        : ActivityEventType.COMMENT_CREATED,
                    targetId,
                    Instant.now()
                )
            )
            .build();
    }

    private ProfileReviewActivityDTO createProfileReviewDTO(Long id, int score) {
        return new ProfileReviewActivityDTO(
            id,
            false,
            PullRequestReview.State.APPROVED,
            0,
            null,
            null,
            "https://github.com/test/review/" + id,
            score,
            Instant.now()
        );
    }
}
