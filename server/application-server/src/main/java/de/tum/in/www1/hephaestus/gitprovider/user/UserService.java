package de.tum.in.www1.hephaestus.gitprovider.user;

import java.util.Comparator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.dto.PullRequestDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.dto.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.dto.PullRequestReviewDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.dto.PullRequestReviewInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.dto.RepositoryDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.repository.dto.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserProfileDTO;
import jakarta.transaction.Transactional;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final UserDTOConverter userDTOConverter;
    private final RepositoryDTOConverter repositoryDTOConverter;
    private final PullRequestDTOConverter pullRequestDTOConverter;
    private final PullRequestReviewDTOConverter pullRequestReviewDTOConverter;

    public UserService(
            UserRepository userRepository,
            RepositoryRepository repositoryRepository,
            PullRequestRepository pullRequestRepository,
            PullRequestReviewRepository pullRequestReviewRepository,
            UserDTOConverter userDTOConverter,
            RepositoryDTOConverter repositoryDTOConverter,
            PullRequestDTOConverter pullRequestDTOConverter,
            PullRequestReviewDTOConverter pullRequestReviewDTOConverter) {
        this.userRepository = userRepository;
        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.userDTOConverter = userDTOConverter;
        this.repositoryDTOConverter = repositoryDTOConverter;
        this.pullRequestDTOConverter = pullRequestDTOConverter;
        this.pullRequestReviewDTOConverter = pullRequestReviewDTOConverter;
    }

    @Transactional
    public Optional<UserProfileDTO> getUserProfile(String login) {
        logger.info("Getting user profile with login: " + login);

        Optional<UserInfoDTO> optionalUser = userRepository.findByLogin(login).map(userDTOConverter::convertToDTO);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }

        UserInfoDTO user = optionalUser.get();
        OffsetDateTime firstContribution = pullRequestRepository.firstContributionByAuthorLogin(login).orElse(null);
        List<PullRequestInfoDTO> openPullRequests = pullRequestRepository.findAssignedByLoginAndStates(login, Set.of(Issue.State.OPEN))
                .stream()
                .map(pullRequestDTOConverter::convertToDTO)
                .toList();
        List<RepositoryInfoDTO> contributedRepositories = repositoryRepository.findContributedByLogin(login)
                .stream()
                .map(repositoryDTOConverter::convertToDTO)
                .sorted(Comparator.comparing(RepositoryInfoDTO::name))
                .toList();
        List<PullRequestReviewInfoDTO> reviewActivity = pullRequestReviewRepository.findAllByAuthorLoginSince(login, OffsetDateTime.now().minusDays(7))
                .stream()
                .map(pullRequestReviewDTOConverter::convertToDTO)
                .sorted(Comparator.comparing(PullRequestReviewInfoDTO::submittedAt))
                .toList();

        return Optional.of(new UserProfileDTO(
                user,
                firstContribution,
                contributedRepositories,
                reviewActivity,
                openPullRequests
        ));
    }
}
