package de.tum.in.www1.hephaestus.gitprovider.user;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user profile data (workspace-scoped git activity).
 * Account management (settings, deletion) is handled by AccountService.
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private PullRequestReviewRepository pullRequestReviewRepository;

    @Autowired
    private IssueCommentRepository issueCommentRepository;

    @Autowired
    private PullRequestReviewInfoDTOConverter pullRequestReviewInfoDTOConverter;

    @Autowired
    private WorkspaceMembershipService workspaceMembershipService;

    /**
     * Get user profile with workspace-scoped activity data.
     *
     * @param login GitHub login
     * @param workspaceId workspace to scope activity to (null for global view)
     * @return user profile with open PRs, review activity, etc.
     */
    @Transactional
    public Optional<UserProfileDTO> getUserProfile(String login, Long workspaceId) {
        logger.info("Getting user profile with login: {} for workspaceId: {}", login, workspaceId);

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
        List<PullRequestReviewInfoDTO> reviewActivity =
            (workspaceId == null
                    ? List.<PullRequestReview>of()
                    : pullRequestReviewRepository.findAllByAuthorLoginSince(
                        login,
                        Instant.now().minusSeconds(7L * 24 * 60 * 60),
                        workspaceId
                    )).stream()
                .map(pullRequestReviewInfoDTOConverter::convert)
                .collect(Collectors.toCollection(ArrayList::new));
        reviewActivity.addAll(
            (workspaceId == null
                    ? List.<IssueComment>of()
                    : issueCommentRepository.findAllByAuthorLoginSince(
                        login,
                        Instant.now().minusSeconds(7L * 24 * 60 * 60),
                        true,
                        workspaceId
                    )).stream()
                .map(pullRequestReviewInfoDTOConverter::convert)
                .toList()
        );
        reviewActivity.sort(Comparator.comparing(PullRequestReviewInfoDTO::submittedAt).reversed());

        return Optional.of(
            new UserProfileDTO(user, firstContribution, contributedRepositories, reviewActivity, openPullRequests)
        );
    }
}
