package de.tum.in.www1.hephaestus.profile;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewInfoDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserProfileDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user profile data aggregation.
 * Combines git provider data (PRs, reviews) with workspace membership data (league points).
 */
@Service
public class UserProfileService {

    private static final Logger logger = LoggerFactory.getLogger(UserProfileService.class);

    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final IssueCommentRepository issueCommentRepository;
    private final PullRequestReviewInfoDTOConverter pullRequestReviewInfoDTOConverter;
    private final WorkspaceMembershipService workspaceMembershipService;

    public UserProfileService(
        UserRepository userRepository,
        RepositoryRepository repositoryRepository,
        PullRequestRepository pullRequestRepository,
        PullRequestReviewRepository pullRequestReviewRepository,
        IssueCommentRepository issueCommentRepository,
        PullRequestReviewInfoDTOConverter pullRequestReviewInfoDTOConverter,
        WorkspaceMembershipService workspaceMembershipService
    ) {
        this.userRepository = userRepository;
        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.issueCommentRepository = issueCommentRepository;
        this.pullRequestReviewInfoDTOConverter = pullRequestReviewInfoDTOConverter;
        this.workspaceMembershipService = workspaceMembershipService;
    }

    /**
     * Get user profile with workspace-scoped activity data.
     *
     * @param login GitHub login
     * @param workspaceId workspace to scope activity to (null for global view)
     * @return user profile with open PRs, review activity, etc.
     */
    @Transactional(readOnly = true)
    public Optional<UserProfileDTO> getUserProfile(String login, Long workspaceId) {
        logger.debug("Getting user profile for login: {} in workspace: {}", login, workspaceId);

        Optional<User> optionalUser = userRepository.findByLogin(login);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }

        User userEntity = optionalUser.get();

        int leaguePoints = workspaceMembershipService.getCurrentLeaguePoints(workspaceId, userEntity);
        UserInfoDTO user = UserInfoDTO.fromUser(userEntity, leaguePoints);
        var firstContribution = pullRequestRepository.firstContributionByAuthorLogin(login).orElse(null);

        List<PullRequestInfoDTO> openPullRequests = workspaceId == null
            ? List.of()
            : pullRequestRepository
                .findAssignedByLoginAndStates(login, Set.of(Issue.State.OPEN), workspaceId)
                .stream()
                .map(PullRequestInfoDTO::fromPullRequest)
                .toList();

        List<RepositoryInfoDTO> contributedRepositories = repositoryRepository
            .findContributedByLogin(login)
            .stream()
            .map(RepositoryInfoDTO::fromRepository)
            .sorted(Comparator.comparing(RepositoryInfoDTO::name))
            .toList();

        // Review activity includes both pull request reviews and issue comments
        List<PullRequestReviewInfoDTO> reviewActivity = buildReviewActivity(login, workspaceId);

        return Optional.of(
            new UserProfileDTO(user, firstContribution, contributedRepositories, reviewActivity, openPullRequests)
        );
    }

    private List<PullRequestReviewInfoDTO> buildReviewActivity(String login, Long workspaceId) {
        if (workspaceId == null) {
            return List.of();
        }

        Instant since = Instant.now().minusSeconds(7L * 24 * 60 * 60);

        List<PullRequestReviewInfoDTO> reviewActivity = pullRequestReviewRepository
            .findAllByAuthorLoginSince(login, since, workspaceId)
            .stream()
            .map(pullRequestReviewInfoDTOConverter::convert)
            .collect(Collectors.toCollection(ArrayList::new));

        reviewActivity.addAll(
            issueCommentRepository
                .findAllByAuthorLoginSince(login, since, true, workspaceId)
                .stream()
                .map(pullRequestReviewInfoDTOConverter::convert)
                .toList()
        );

        reviewActivity.sort(Comparator.comparing(PullRequestReviewInfoDTO::submittedAt).reversed());
        return reviewActivity;
    }
}
