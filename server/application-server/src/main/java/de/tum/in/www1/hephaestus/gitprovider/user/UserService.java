package de.tum.in.www1.hephaestus.gitprovider.user;

import java.util.ArrayList;
import java.util.Comparator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import jakarta.transaction.Transactional;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final IssueCommentRepository issueCommentRepository;

    public UserService(
            UserRepository userRepository,
            RepositoryRepository repositoryRepository,
            PullRequestRepository pullRequestRepository,
            PullRequestReviewRepository pullRequestReviewRepository,
            IssueCommentRepository issueCommentRepository) {
        this.userRepository = userRepository;
        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.issueCommentRepository = issueCommentRepository;
    }

    @Transactional
    public Optional<UserProfileDTO> getUserProfile(String login) {
        logger.info("Getting user profile with login: " + login);

        Optional<UserInfoDTO> optionalUser = userRepository.findByLogin(login).map(UserInfoDTO::fromUser);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }

        UserInfoDTO user = optionalUser.get();
        OffsetDateTime firstContribution = pullRequestRepository.firstContributionByAuthorLogin(login).orElse(null);
        List<PullRequestInfoDTO> openPullRequests = pullRequestRepository
                .findAssignedByLoginAndStates(login, Set.of(Issue.State.OPEN))
                .stream()
                .map(PullRequestInfoDTO::fromPullRequest)
                .toList();
        List<RepositoryInfoDTO> contributedRepositories = repositoryRepository.findContributedByLogin(login)
                .stream()
                .map(RepositoryInfoDTO::fromRepository)
                .sorted(Comparator.comparing(RepositoryInfoDTO::name))
                .toList();
        
        // Review activity includes both pull request reviews and issue comments
        List<PullRequestReviewInfoDTO> reviewActivity = pullRequestReviewRepository
                .findAllByAuthorLoginSince(login, OffsetDateTime.now().minusDays(7))
                .stream()
                .map(PullRequestReviewInfoDTO::fromPullRequestReview)
                .collect(Collectors.toCollection(ArrayList::new));
        reviewActivity.addAll(
            issueCommentRepository
                .findAllByAuthorLoginSince(login, OffsetDateTime.now().minusDays(7), true)
                .stream()
                .map(PullRequestReviewInfoDTO::fromIssueComment)
                .toList()
        );
        reviewActivity.sort(Comparator.comparing(PullRequestReviewInfoDTO::submittedAt).reversed());
    
        return Optional.of(new UserProfileDTO(
                user,
                firstContribution,
                contributedRepositories,
                reviewActivity,
                openPullRequests));
    }
}
