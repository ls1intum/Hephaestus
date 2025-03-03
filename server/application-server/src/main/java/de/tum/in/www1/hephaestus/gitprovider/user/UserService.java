package de.tum.in.www1.hephaestus.gitprovider.user;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewInfoDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.notification.MailService;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
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
    private MailService mailService;

    @Transactional
    public Optional<UserProfileDTO> getUserProfile(String login) {
        logger.info("Getting user profile with login: " + login);

        Optional<UserInfoDTO> optionalUser = userRepository.findByLogin(login).map(UserInfoDTO::fromUser);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }

        userRepository.findByLogin(login).ifPresent(user -> {
            mailService.sendBadPracticesDetectedEmail(user, "badPractice");
        });

        UserInfoDTO user = optionalUser.get();
        OffsetDateTime firstContribution = pullRequestRepository.firstContributionByAuthorLogin(login).orElse(null);
        List<PullRequestInfoDTO> openPullRequests = pullRequestRepository
            .findAssignedByLoginAndStates(login, Set.of(Issue.State.OPEN))
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
        List<PullRequestReviewInfoDTO> reviewActivity = pullRequestReviewRepository
            .findAllByAuthorLoginSince(login, OffsetDateTime.now().minusDays(7))
            .stream()
            .map(pullRequestReviewInfoDTOConverter::convert)
            .collect(Collectors.toCollection(ArrayList::new));
        reviewActivity.addAll(
            issueCommentRepository
                .findAllByAuthorLoginSince(login, OffsetDateTime.now().minusDays(7), true)
                .stream()
                .map(pullRequestReviewInfoDTOConverter::convert)
                .toList()
        );
        reviewActivity.sort(Comparator.comparing(PullRequestReviewInfoDTO::submittedAt).reversed());

        return Optional.of(
            new UserProfileDTO(user, firstContribution, contributedRepositories, reviewActivity, openPullRequests)
        );
    }

    public UserSettingsDTO getUserSettings(User user) {
        logger.info("Getting user settings with userId: " + user);
        return new UserSettingsDTO(user.isNotificationsEnabled());
    }

    public UserSettingsDTO updateUserSettings(User user, UserSettingsDTO userSettings) {
        logger.info("Updating user settings with userId: " + user);
        user.setNotificationsEnabled(userSettings.receiveNotifications());
        userRepository.save(user);
        return new UserSettingsDTO(user.isNotificationsEnabled());
    }
}
