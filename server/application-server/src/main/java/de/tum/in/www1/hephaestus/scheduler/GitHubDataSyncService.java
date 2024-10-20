package de.tum.in.www1.hephaestus.scheduler;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHObject;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.GHPullRequestQueryBuilder.Sort;
import org.kohsuke.github.GHPullRequestReview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.admin.AdminService;
import de.tum.in.www1.hephaestus.codereview.comment.IssueComment;
import de.tum.in.www1.hephaestus.codereview.comment.IssueCommentConverter;
import de.tum.in.www1.hephaestus.codereview.comment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.codereview.comment.review.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.codereview.comment.review.PullRequestReviewCommentConverter;
import de.tum.in.www1.hephaestus.codereview.comment.review.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequestConverter;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.codereview.pullrequest.review.PullRequestReview;
import de.tum.in.www1.hephaestus.codereview.pullrequest.review.PullRequestReviewConverter;
import de.tum.in.www1.hephaestus.codereview.pullrequest.review.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.codereview.repository.Repository;
import de.tum.in.www1.hephaestus.codereview.repository.RepositoryConverter;
import de.tum.in.www1.hephaestus.codereview.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.codereview.user.User;
import de.tum.in.www1.hephaestus.codereview.user.UserConverter;
import de.tum.in.www1.hephaestus.codereview.user.UserRepository;
import jakarta.transaction.Transactional;

@Transactional
@Service
public class GitHubDataSyncService {
    private static final Logger logger = LoggerFactory.getLogger(GitHubDataSyncService.class);

    @Value("${github.authToken:null}")
    private String ghAuthToken;

    @Value("${monitoring.timeframe}")
    private int timeframe;
    private OffsetDateTime cutOffTime;

    private GitHub github;

    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewRepository prReviewRepository;
    private final IssueCommentRepository commentRepository;
    private final PullRequestReviewCommentRepository reviewCommentRepository;
    private final UserRepository userRepository;

    @Autowired
    private AdminService adminService;

    @Autowired
    private RepositoryConverter repositoryConverter;
    @Autowired
    private PullRequestConverter pullRequestConverter;
    @Autowired
    private PullRequestReviewConverter reviewConverter;
    @Autowired
    private IssueCommentConverter commentConverter;
    @Autowired
    private PullRequestReviewCommentConverter reviewCommentConverter;
    @Autowired
    private UserConverter userConverter;

    // Temporary caches per repository
    private Set<User> users = new HashSet<>();
    private Set<PullRequestReview> reviews = new HashSet<>();

    public GitHubDataSyncService(RepositoryRepository repositoryRepository, PullRequestRepository pullRequestRepository,
            PullRequestReviewRepository prReviewRepository,
            IssueCommentRepository commentRepository, PullRequestReviewCommentRepository reviewCommentRepository,
            UserRepository userRepository) {
        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.prReviewRepository = prReviewRepository;
        this.commentRepository = commentRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.userRepository = userRepository;
    }

    public void syncData() {
        if (!initGithubClient()) {
            logger.error("Aborted GitHub data sync due to error during initialization of GitHub client.");
            return;
        }

        Set<String> repositoriesToMonitor = adminService.getAdminConfig().getRepositoriesToMonitor();
        int successfullySyncedRepositories = 0;
        for (String repositoryName : repositoriesToMonitor) {
            try {
                this.fetchRepository(repositoryName);
                logger.info("GitHub data sync completed successfully for repository: " + repositoryName);
                successfullySyncedRepositories++;
            } catch (Exception e) {
                logger.error("Error during GitHub data sync of repository " + repositoryName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        logger.info("GitHub data sync completed for " + successfullySyncedRepositories + "/"
                + repositoriesToMonitor.size() + " repositories for the last " + timeframe + " day(s).");
    }

    private boolean initGithubClient() {
        if (ghAuthToken == null || ghAuthToken.isEmpty() || ghAuthToken.equals("null")) {
            logger.error("No GitHub auth token provided!");
            return false;
        }
        if (github == null) {
            try {
                github = new GitHubBuilder().withOAuthToken(ghAuthToken).build();
            } catch (IOException e) {
                logger.error("Error while initializing GitHub client: " + e.getMessage());
                return false;
            }
        }
        return github.isCredentialValid();
    }

    /**
     * Rest API implementation of fetching a Github repository.
     * 
     * @throws IOException
     */
    public void fetchRepository(String nameWithOwner) throws IOException {
        GHRepository ghRepo = github.getRepository(nameWithOwner);
        // Avoid double fetching of already stored repositories
        Repository repository = repositoryRepository.findByNameWithOwnerWithEagerPullRequests(nameWithOwner);
        if (repository == null) {
            logger.info("Creating new repository: " + nameWithOwner);
            repository = repositoryRepository.save(repositoryConverter.convert(ghRepo));
            this.cutOffTime = new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * timeframe).toInstant()
                    .atOffset(ZoneOffset.UTC);
        } else {
            // Update cut-off time to avoid refetching stored data.
            // The updatedAt field of the repository does not change with every PR update,
            // so it will always be earlier
            this.cutOffTime = repository.getPullRequests().stream().map(PullRequest::getUpdatedAt)
                    .max(OffsetDateTime::compareTo).orElse(repository.getUpdatedAt());
        }
        logger.info("Cut-off time for the repository data sync: " + this.cutOffTime);

        Set<PullRequest> prs = fetchPullRequestsFromGHRepository(ghRepo, repository);
        logger.info("Found total of " + prs.size() + " PRs");
        for (PullRequest pr : prs) {
            if (repository.getPullRequests().stream().noneMatch(p -> p.getId().equals(pr.getId()))) {
                repository.addPullRequest(pr);
            }
        }

        pullRequestRepository.saveAll(prs);
        userRepository.saveAll(users);
        repositoryRepository.save(repository);
        prReviewRepository.saveAll(reviews);

        users = new HashSet<>();
        reviews = new HashSet<>();
    }

    private Set<PullRequest> fetchPullRequestsFromGHRepository(GHRepository ghRepo, Repository repository)
            throws IOException {
        // Iterator allows us to handle pullrequests without knowing the next ones
        PagedIterator<GHPullRequest> pullRequests = ghRepo.queryPullRequests()
                .state(GHIssueState.ALL)
                .sort(Sort.UPDATED)
                .direction(GHDirection.DESC)
                .list().withPageSize(100).iterator();
        Set<PullRequest> prs = new HashSet<>();

        // Only fetch next page if all PRs are still within the timeframe
        AtomicBoolean fetchStillInTimeframe = new AtomicBoolean(true);
        while (fetchStillInTimeframe.get() && pullRequests.hasNext()) {
            List<GHPullRequest> nextPage = pullRequests.nextPage();
            logger.info("Fetched " + nextPage.size() + " PRs from Github");
            prs.addAll(nextPage.stream().map(pr -> {
                if (!isResourceRecent(pr)) {
                    fetchStillInTimeframe.set(false);
                    return null;
                }

                PullRequest pullRequest = createOrUpdatePullRequest(pr, repository);

                try {
                    Set<PullRequestReview> newReviews = pr.listReviews().toList().stream()
                            .map(review -> createOrUpdatePullRequestReview(review, pullRequest))
                            .collect(Collectors.toSet());
                    for (PullRequestReview prReview : newReviews) {
                        if (reviews.stream().noneMatch(r -> r.getId().equals(prReview.getId()))) {
                            pullRequest.addReview(prReview);
                            reviews.add(prReview);
                        }
                    }
                    logger.info("Found " + newReviews.size() + " reviews for PR #" + pullRequest.getNumber());
                } catch (IOException e) {
                    logger.error("Error while fetching PR reviews!");
                }

                try {
                    List<PullRequestReviewComment> prrComments = pr.listReviewComments().withPageSize(100).toList()
                            .stream()
                            .takeWhile(rc -> isResourceRecent(rc))
                            .map(c -> handleSinglePullRequestReviewComment(c)).filter(Objects::nonNull).toList();
                    reviewCommentRepository.saveAll(prrComments);
                } catch (IOException e) {
                    logger.error("Error while fetching PR review comments!");
                }

                PagedIterator<GHIssueComment> commentIterator = pr.queryComments().list().withPageSize(100)
                        .iterator();
                Set<IssueComment> comments = pullRequest.getComments();
                while (commentIterator.hasNext()) {
                    List<GHIssueComment> nextCommentPage = commentIterator.nextPage();
                    try {
                        IssueComment nextComment = createOrUpdateIssueComment(nextCommentPage.getFirst(), pullRequest);
                        if (comments.stream().noneMatch(c -> c.getId().equals(nextComment.getId()))) {
                            comments.add(nextComment);
                            pullRequest.addComment(nextComment);
                        }
                    } catch (NoSuchElementException e) {
                        break;
                    }
                }
                commentRepository.saveAll(comments);

                return pullRequest;
            }).filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        return prs;
    }

    private PullRequestReviewComment handleSinglePullRequestReviewComment(GHPullRequestReviewComment comment) {
        PullRequestReviewComment prrc = reviewCommentRepository.save(reviewCommentConverter.convert(comment));

        PullRequestReview prReview = getPRRFromReviewId(comment.getPullRequestReviewId());
        prrc.setReview(prReview);
        User commentAuthor;
        try {
            commentAuthor = getUserFromGHUser(comment.getUser());
            commentAuthor.addReviewComment(prrc);
        } catch (IOException e) {
            // Dont mind this error as it occurs only for bots
            commentAuthor = null;
        }
        prrc.setAuthor(commentAuthor);
        prReview.addComment(prrc);
        return prrc;
    }

    /**
     * Creates new PullRequest or updates instance if it already exists.
     * 
     * @param pr         Source GitHub PullRequest
     * @param repository Repository the PR belongs to
     * @return PullRequest entity
     */
    private PullRequest createOrUpdatePullRequest(GHPullRequest pr, Repository repository) {
        Optional<PullRequest> pullRequest = pullRequestRepository.findByIdWithEagerRelations(pr.getId());
        if (pullRequest.isPresent()) {
            return pullRequestConverter.update(pr, pullRequest.get());
        }

        PullRequest newPullRequest = pullRequestRepository.save(pullRequestConverter.convert(pr));
        newPullRequest.setRepository(repository);
        repository.addPullRequest(newPullRequest);
        try {
            User prAuthor = getUserFromGHUser(pr.getUser());
            prAuthor.addPullRequest(newPullRequest);
            newPullRequest.setAuthor(prAuthor);
        } catch (IOException e) {
            // Dont mind this error as it occurs only for bots
            newPullRequest.setAuthor(null);
        }
        return newPullRequest;
    }

    private PullRequestReview createOrUpdatePullRequestReview(GHPullRequestReview review, PullRequest pullRequest) {
        Optional<PullRequestReview> pullRequestReview = prReviewRepository.findByIdWithEagerComments(review.getId());
        if (pullRequestReview.isPresent()) {
            return reviewConverter.update(review, pullRequestReview.get());
        }
        PullRequestReview newPullRequestReview = prReviewRepository.save(reviewConverter.convert(review));
        try {
            User reviewAuthor = getUserFromGHUser(review.getUser());
            reviewAuthor.addReview(newPullRequestReview);
            newPullRequestReview.setAuthor(reviewAuthor);
        } catch (IOException e) {
            // Dont mind this error as it occurs only for bots
        }
        newPullRequestReview.setPullRequest(pullRequest);
        return newPullRequestReview;
    }

    private IssueComment createOrUpdateIssueComment(GHIssueComment comment, PullRequest pullRequest) {
        Optional<IssueComment> issueComment = commentRepository.findById(comment.getId());
        if (issueComment.isPresent()) {
            return commentConverter.update(comment, issueComment.get());
        }
        IssueComment newIssueComment = commentRepository.save(commentConverter.convert(comment));
        newIssueComment.setPullRequest(pullRequest);
        User commentAuthor;
        try {
            commentAuthor = getUserFromGHUser(comment.getUser());
            commentAuthor.addIssueComment(newIssueComment);
        } catch (IOException e) {
            // Dont mind this error as it occurs only for bots
            commentAuthor = null;
        }
        newIssueComment.setAuthor(commentAuthor);
        return newIssueComment;
    }

    /**
     * Gets the corresponding User entity instance: cache -> database -> create new
     * user
     * 
     * @param user GHUser instance
     * @return entity instance
     */
    private User getUserFromGHUser(org.kohsuke.github.GHUser user) {
        // Try to find user in cache
        Optional<User> ghUser = users.stream().filter(u -> u.getLogin().equals(user.getLogin())).findFirst();
        if (ghUser.isPresent()) {
            return ghUser.get();
        }
        // Try to find user in database
        ghUser = userRepository.findUserEagerly(user.getLogin());
        if (ghUser.isPresent()) {
            User u = ghUser.get();
            if (!users.contains(u)) {
                users.add(u);
            }
            return u;
        }
        // Otherwise create new user
        User u = userRepository.save(userConverter.convert(user));
        users.add(u);
        return u;
    }

    /**
     * Gets the corresponding PullRequestReview from the cache.
     * 
     * @implNote Assumes that it's executed after the review has been fetched from
     *           Github already
     * @param reviewId
     */
    private PullRequestReview getPRRFromReviewId(Long reviewId) {
        Optional<PullRequestReview> prReview = reviews.stream().filter(prr -> prr.getId().equals(reviewId)).findFirst();
        if (prReview.isPresent()) {
            return prReview.get();
        }
        logger.error("Cannot find PRR with ID " + reviewId);
        return null;
    }

    /**
     * Checks if the resource has been created within the timeframe.
     * 
     * @param obj
     * @return
     */
    private boolean isResourceRecent(GHObject obj) {
        try {
            return obj.getUpdatedAt() != null
                    && obj.getUpdatedAt().toInstant().atOffset(ZoneOffset.UTC).isAfter(cutOffTime);
        } catch (IOException e) {
            logger.error("Error while fetching createdAt! Resource ID: " + obj.getId());
            return false;
        }
    }
}
