package de.tum.in.www1.hephaestus.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivityTargetType;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
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
 * <p>Tests verify the CQRS pattern: XP is read from the activity_event ledger,
 * not recalculated on-the-fly. Also verifies proper layer separation - the profile
 * module composes gitprovider data with XP from activity module.
 */
@Tag("unit")
@DisplayName("UserProfileService")
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final Long WORKSPACE_ID = 1L;
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
    private ProfileReviewQueryRepository profileReviewQueryRepository;

    @Mock
    private ProfileCommentQueryRepository profileCommentQueryRepository;

    @Mock
    private ActivityEventRepository activityEventRepository;

    @Mock
    private ProfileReviewActivityAssembler reviewActivityAssembler;

    @Mock
    private WorkspaceMembershipService workspaceMembershipService;

    @Mock
    private WorkspaceContributionActivityService workspaceContributionActivityService;

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(
            userRepository,
            profileRepositoryQueryRepository,
            profilePullRequestQueryRepository,
            profileReviewQueryRepository,
            profileCommentQueryRepository,
            activityEventRepository,
            reviewActivityAssembler,
            workspaceMembershipService,
            workspaceContributionActivityService
        );
    }

    @Nested
    @DisplayName("CQRS Architecture Tests")
    class CqrsArchitectureTests {

        @Test
        @DisplayName("fetches XP from activity_event ledger (not recalculated)")
        void fetchesXpFromActivityEventLedger() {
            // Arrange
            User user = createUser(100L, USER_LOGIN);
            Repository repo = createRepository(200L);
            PullRequest pr = createPullRequest(300L, user, repo);
            PullRequestReview review = createReview(400L, user, pr);

            when(userRepository.findByLogin(USER_LOGIN)).thenReturn(Optional.of(user));
            when(
                profileReviewQueryRepository.findAllByAuthorLoginInTimeframe(
                    eq(USER_LOGIN),
                    any(),
                    any(),
                    eq(WORKSPACE_ID)
                )
            ).thenReturn(List.of(review));
            when(
                profileCommentQueryRepository.findAllByAuthorLoginInTimeframe(
                    eq(USER_LOGIN),
                    any(),
                    any(),
                    eq(true),
                    eq(WORKSPACE_ID)
                )
            ).thenReturn(List.of());
            when(profilePullRequestQueryRepository.findAssignedByLoginAndStates(any(), any(), any())).thenReturn(
                List.of()
            );
            when(profileRepositoryQueryRepository.findContributedByLogin(any(), any())).thenReturn(List.of());

            // XP from activity_event ledger
            ActivityEventRepository.TargetXpProjection xpProjection = createXpProjection(400L, 15.0);
            when(
                activityEventRepository.findXpByTargetIdsAndTypes(
                    eq(WORKSPACE_ID),
                    eq(Set.of(400L)),
                    eq(Set.of(ActivityTargetType.REVIEW, ActivityTargetType.ISSUE_COMMENT))
                )
            ).thenReturn(List.of(xpProjection));

            // Mock assembler
            when(reviewActivityAssembler.assemble(eq(review), eq(15))).thenReturn(createProfileReviewDTO(400L, 15));

            // Act
            Optional<ProfileDTO> result = service.getUserProfile(USER_LOGIN, WORKSPACE_ID, AFTER, BEFORE);

            // Assert
            assertThat(result).isPresent();

            // Verify XP was fetched from activity_event ledger (CQRS pattern)
            verify(activityEventRepository).findXpByTargetIdsAndTypes(
                eq(WORKSPACE_ID),
                eq(Set.of(400L)),
                eq(Set.of(ActivityTargetType.REVIEW, ActivityTargetType.ISSUE_COMMENT))
            );

            // Verify assembler was called with pre-computed XP (15)
            verify(reviewActivityAssembler).assemble(eq(review), eq(15));
        }

        @Test
        @DisplayName("uses zero XP for historical reviews without activity events")
        void usesZeroXpForHistoricalReviews() {
            // Arrange
            User user = createUser(100L, USER_LOGIN);
            Repository repo = createRepository(200L);
            PullRequest pr = createPullRequest(300L, user, repo);
            PullRequestReview review = createReview(400L, user, pr);

            when(userRepository.findByLogin(USER_LOGIN)).thenReturn(Optional.of(user));
            when(
                profileReviewQueryRepository.findAllByAuthorLoginInTimeframe(
                    eq(USER_LOGIN),
                    any(),
                    any(),
                    eq(WORKSPACE_ID)
                )
            ).thenReturn(List.of(review));
            when(
                profileCommentQueryRepository.findAllByAuthorLoginInTimeframe(
                    eq(USER_LOGIN),
                    any(),
                    any(),
                    eq(true),
                    eq(WORKSPACE_ID)
                )
            ).thenReturn(List.of());
            when(profilePullRequestQueryRepository.findAssignedByLoginAndStates(any(), any(), any())).thenReturn(
                List.of()
            );
            when(profileRepositoryQueryRepository.findContributedByLogin(any(), any())).thenReturn(List.of());

            // No XP in ledger (historical review before activity_event existed)
            when(activityEventRepository.findXpByTargetIdsAndTypes(any(), any(), any())).thenReturn(List.of());

            // Mock assembler
            when(reviewActivityAssembler.assemble(eq(review), eq(0))).thenReturn(createProfileReviewDTO(400L, 0));

            // Act
            service.getUserProfile(USER_LOGIN, WORKSPACE_ID, AFTER, BEFORE);

            // Assert: assembler called with 0 XP as fallback
            verify(reviewActivityAssembler).assemble(eq(review), eq(0));
        }

        @Test
        @DisplayName("batch-fetches XP for multiple reviews and comments efficiently")
        void batchFetchesXpForMultipleItems() {
            // Arrange
            User user = createUser(100L, USER_LOGIN);
            Repository repo = createRepository(200L);
            PullRequest pr = createPullRequest(300L, user, repo);
            PullRequestReview review1 = createReview(400L, user, pr);
            PullRequestReview review2 = createReview(401L, user, pr);
            IssueComment comment = createIssueComment(500L, user, pr);

            when(userRepository.findByLogin(USER_LOGIN)).thenReturn(Optional.of(user));
            when(
                profileReviewQueryRepository.findAllByAuthorLoginInTimeframe(
                    eq(USER_LOGIN),
                    any(),
                    any(),
                    eq(WORKSPACE_ID)
                )
            ).thenReturn(List.of(review1, review2));
            when(
                profileCommentQueryRepository.findAllByAuthorLoginInTimeframe(
                    eq(USER_LOGIN),
                    any(),
                    any(),
                    eq(true),
                    eq(WORKSPACE_ID)
                )
            ).thenReturn(List.of(comment));
            when(profilePullRequestQueryRepository.findAssignedByLoginAndStates(any(), any(), any())).thenReturn(
                List.of()
            );
            when(profileRepositoryQueryRepository.findContributedByLogin(any(), any())).thenReturn(List.of());

            // XP from activity_event ledger (batch query for all 3 items)
            when(
                activityEventRepository.findXpByTargetIdsAndTypes(
                    eq(WORKSPACE_ID),
                    eq(Set.of(400L, 401L, 500L)),
                    eq(Set.of(ActivityTargetType.REVIEW, ActivityTargetType.ISSUE_COMMENT))
                )
            ).thenReturn(
                List.of(createXpProjection(400L, 10.0), createXpProjection(401L, 20.0), createXpProjection(500L, 5.0))
            );

            // Mock assembler returns
            when(reviewActivityAssembler.assemble(eq(review1), eq(10))).thenReturn(createProfileReviewDTO(400L, 10));
            when(reviewActivityAssembler.assemble(eq(review2), eq(20))).thenReturn(createProfileReviewDTO(401L, 20));
            when(reviewActivityAssembler.assemble(eq(comment), eq(5))).thenReturn(createProfileReviewDTO(500L, 5));

            // Act
            service.getUserProfile(USER_LOGIN, WORKSPACE_ID, AFTER, BEFORE);

            // Assert: Single batch query for all IDs (no N+1)
            verify(activityEventRepository, times(1)).findXpByTargetIdsAndTypes(any(), any(), any());

            // Verify each item assembled with its correct XP
            verify(reviewActivityAssembler).assemble(eq(review1), eq(10));
            verify(reviewActivityAssembler).assemble(eq(review2), eq(20));
            verify(reviewActivityAssembler).assemble(eq(comment), eq(5));
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

    private ActivityEventRepository.TargetXpProjection createXpProjection(Long targetId, Double xp) {
        return new ActivityEventRepository.TargetXpProjection() {
            @Override
            public Long getTargetId() {
                return targetId;
            }

            @Override
            public Double getXp() {
                return xp;
            }
        };
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
