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
import de.tum.in.www1.hephaestus.integrations.posthog.PosthogClient;
import de.tum.in.www1.hephaestus.integrations.posthog.PosthogClientException;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipService;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextHolder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

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

    @Autowired
    private PosthogClient posthogClient;

    @Transactional
    public Optional<UserProfileDTO> getUserProfile(String login) {
        logger.info("Getting user profile with login: " + login);

        Optional<User> optionalUser = userRepository.findByLogin(login);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }

        User userEntity = optionalUser.get();

        WorkspaceContext context = WorkspaceContextHolder.getContext();
        Long workspaceId = context != null ? context.id() : null;

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

    public UserSettingsDTO getUserSettings(User user) {
        logger.info("Getting user settings with userId: " + user);
        return new UserSettingsDTO(user.isNotificationsEnabled(), user.isParticipateInResearch());
    }

    public UserSettingsDTO updateUserSettings(User user, UserSettingsDTO userSettings, String keycloakUserId) {
        logger.info("Updating user settings with userId: " + user);
        user.setNotificationsEnabled(
            Objects.requireNonNull(userSettings.receiveNotifications(), "receiveNotifications must not be null")
        );
        boolean previousParticipation = user.isParticipateInResearch();
        boolean participatesInResearch = Objects.requireNonNull(
            userSettings.participateInResearch(),
            "participateInResearch must not be null"
        );
        user.setParticipateInResearch(participatesInResearch);
        userRepository.save(user);
        if (previousParticipation && !participatesInResearch) {
            if (!StringUtils.hasText(keycloakUserId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing authentication subject");
            }
            try {
                boolean anyDeleted = deletePosthogIdentities(user, keycloakUserId);
                if (!anyDeleted) {
                    logger.warn("No PostHog person matched the provided identifiers for user {}", user.getLogin());
                }
            } catch (PosthogClientException exception) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to revoke analytics consent",
                    exception
                );
            }
        }
        return new UserSettingsDTO(user.isNotificationsEnabled(), user.isParticipateInResearch());
    }

    public void deleteUserTrackingData(Optional<User> user, String keycloakUserId) {
        try {
            boolean anyDeleted = deletePosthogIdentities(user.orElse(null), keycloakUserId);
            if (!anyDeleted) {
                logger.warn(
                    "No PostHog person matched the provided identifiers for user {} during account deletion",
                    user.map(User::getLogin).orElse("unknown")
                );
            }
        } catch (PosthogClientException exception) {
            throw exception;
        }
    }

    private boolean deletePosthogIdentities(User user, String primaryDistinctId) {
        Set<String> distinctIds = new LinkedHashSet<>();
        if (StringUtils.hasText(primaryDistinctId)) {
            distinctIds.add(primaryDistinctId);
        }
        if (user != null) {
            distinctIds.add(String.valueOf(user.getId()));
        }

        boolean anyDeleted = false;
        for (String distinctId : distinctIds) {
            if (!StringUtils.hasText(distinctId)) {
                continue;
            }
            anyDeleted = posthogClient.deletePersonData(distinctId) || anyDeleted;
        }
        return anyDeleted;
    }
}
