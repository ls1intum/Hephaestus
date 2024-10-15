package de.tum.in.www1.hephaestus.codereview.user;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.codereview.pullrequest.IssueState;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequestDTO;
import de.tum.in.www1.hephaestus.codereview.pullrequest.review.PullRequestReview;
import de.tum.in.www1.hephaestus.codereview.pullrequest.review.PullRequestReviewDTO;
import de.tum.in.www1.hephaestus.codereview.repository.RepositoryDTO;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository actorRepository) {
        this.userRepository = actorRepository;
    }

    public Optional<User> getUser(String login) {
        logger.info("Getting user with login: " + login);
        return userRepository.findUser(login);
    }

    public Optional<UserDTO> getUserDTO(String login) {
        logger.info("Getting userDTO with login: " + login);
        return userRepository.findByLogin(login);
    }

    public List<User> getAllUsers() {
        logger.info("Getting all users");
        return userRepository.findAll().stream().toList();
    }

    public List<User> getAllUsersInTimeframe(OffsetDateTime after, OffsetDateTime before) {
        logger.info("Getting all users in timeframe between " + after + " and " + before);
        return userRepository.findAllInTimeframe(after, before);
    }

    public Optional<UserProfileDTO> getUserProfileDTO(String login) {
        logger.info("Getting userProfileDTO with login: " + login);
        Optional<User> optionalUser = userRepository.findUser(login);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }
        User user = optionalUser.get();

        OffsetDateTime firstContribution = user.getPullRequests().stream().map(pr -> pr.getCreatedAt())
                .min(OffsetDateTime::compareTo).orElse(null);
        Set<String> repositories = mapToDTO(user.getPullRequests(), pr -> true,
                pr -> pr.getRepository().getNameWithOwner(),
                (r1, r2) -> r1.compareTo(r2));
        Set<PullRequestDTO> pullRequests = getPullRequestDTOs(user.getPullRequests());
        Set<PullRequestReviewDTO> activity = getPullRequestReviewDTOs(user.getReviews());

        return Optional.of(new UserProfileDTO(user.getId(), user.getLogin(), user.getAvatarUrl(), firstContribution,
                repositories, activity, pullRequests));
    }

    private Set<PullRequestDTO> getPullRequestDTOs(Set<PullRequest> pullRequests) {
        return mapToDTO(pullRequests,
                isRecentlyPredicate().and(pr -> ((PullRequest) pr).getState().equals(IssueState.OPEN)),
                pr -> new PullRequestDTO(
                        pr.getId(), pr.getTitle(), pr.getNumber(), pr.getUrl(), pr.getState(), pr.getAdditions(),
                        pr.getDeletions(),
                        pr.getCreatedAt(), pr.getUpdatedAt(), null,
                        pr.getPullRequestLabels(),
                        new RepositoryDTO(pr.getRepository().getName(),
                                pr.getRepository().getNameWithOwner(), null,
                                pr.getRepository().getUrl())),
                (pr1, pr2) -> pr1.createdAt().compareTo(pr2.createdAt()));
    }

    private Set<PullRequestReviewDTO> getPullRequestReviewDTOs(Set<PullRequestReview> reviews) {
        return mapToDTO(reviews, isRecentlyPredicate(), re -> {
            PullRequest pr = re.getPullRequest();
            return new PullRequestReviewDTO(re.getId(),
                    re.getCreatedAt(), re.getUpdatedAt(), re.getSubmittedAt(), re.getState(), re.getUrl(),
                    new PullRequestDTO(pr.getId(), pr.getTitle(), pr.getNumber(), pr.getUrl(), pr.getState(),
                            pr.getAdditions(), pr.getDeletions(), pr.getCreatedAt(), pr.getUpdatedAt(), null,
                            new HashSet<>(),
                            new RepositoryDTO(pr.getRepository().getName(),
                                    pr.getRepository().getNameWithOwner(), null,
                                    pr.getRepository().getUrl())));
        }, (prr1, prr2) -> prr2.submittedAt().compareTo(prr1.submittedAt()));
    }

    private <T extends BaseGitServiceEntity, G> Set<G> mapToDTO(Set<T> entities, Predicate<? super T> predicate,
            Function<T, G> mapper,
            Comparator<G> comparator) {
        return entities.stream().filter(predicate).map(mapper).sorted(comparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // Predicate filtering createdAt for within past 7 days
    private Predicate<? super BaseGitServiceEntity> isRecentlyPredicate() {
        return entity -> entity.getCreatedAt().isAfter(OffsetDateTime.now().minusDays(7));
    }
}
