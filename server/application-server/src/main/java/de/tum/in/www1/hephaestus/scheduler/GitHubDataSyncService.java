package de.tum.in.www1.hephaestus.scheduler;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHObject;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.GHPullRequestQueryBuilder.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class GitHubDataSyncService {
    private static final Logger logger = LoggerFactory.getLogger(GitHubDataSyncService.class);

    @Value("${github.authToken:null}")
    private String ghAuthToken;

    @Value("${monitoring.repositories}")
    private String[] repositoriesToMonitor;

    @Value("${monitoring.timeframe}")
    private int timeframe;

    private GitHub github;

    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewRepository prReviewRepository;
    private final IssueCommentRepository commentRepository;
    private final PullRequestReviewCommentRepository reviewCommentRepository;
    private final UserRepository userRepository;

    private final RepositoryConverter repositoryConverter;
    private final PullRequestConverter pullRequestConverter;
    private final PullRequestReviewConverter reviewConverter;
    private final IssueCommentConverter commentConverter;
    private final PullRequestReviewCommentConverter reviewCommentConverter;
    private final UserConverter userConverter;

    private Set<User> users = new HashSet<>();
    private Set<PullRequestReview> reviews = new HashSet<>();

    public GitHubDataSyncService(RepositoryRepository repositoryRepository, PullRequestRepository pullRequestRepository,
            PullRequestReviewRepository prReviewRepository,
            IssueCommentRepository commentRepository, PullRequestReviewCommentRepository reviewCommentRepository,
            UserRepository userRepository,
            RepositoryConverter repositoryConverter, PullRequestConverter pullRequestConverter,
            PullRequestReviewConverter reviewConverter, IssueCommentConverter commentConverter,
            PullRequestReviewCommentConverter reviewCommentConverter, UserConverter userConverter) {
        logger.info("Hello from GitHubDataSyncService!");

        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.prReviewRepository = prReviewRepository;
        this.commentRepository = commentRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.userRepository = userRepository;

        this.repositoryConverter = repositoryConverter;
        this.pullRequestConverter = pullRequestConverter;
        this.reviewConverter = reviewConverter;
        this.commentConverter = commentConverter;
        this.reviewCommentConverter = reviewCommentConverter;
        this.userConverter = userConverter;
    }

    public void syncData() {
        int successfullySyncedRepositories = 0;
        for (String repositoryName : repositoriesToMonitor) {
            try {
                syncRepository(repositoryName);
                logger.info("GitHub data sync completed successfully for repository: " + repositoryName);
                successfullySyncedRepositories++;
            } catch (Exception e) {
                logger.error("Error during GitHub data sync of repository " + repositoryName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        logger.info("GitHub data sync completed for " + successfullySyncedRepositories + "/"
                + repositoriesToMonitor.length + " repositories for the last " + timeframe + " day(s).");
    }

    private void syncRepository(String repositoryName) throws IOException {
        if (ghAuthToken == null || ghAuthToken.isEmpty() || ghAuthToken.equals("null")) {
            logger.error("No GitHub auth token provided!");
            return;
        }
        if (github == null) {
            github = new GitHubBuilder().withOAuthToken(ghAuthToken).build();
        }
        Repository repo = this.fetchRepository(repositoryName);
        logger.info("Synced repository until " + repo.getUpdatedAt());
    }

    /**
     * Rest API implementation of fetching a Github repository.
     * 
     * @return The repository corresponding to the given nameWithOwner.
     * @throws IOException
     */
    public Repository fetchRepository(String nameWithOwner) throws IOException {
        if (github == null) {
            logger.error("GitHub client not initialized correctly!");
            return null;
        }

        // Avoid double fetching of the same repository
        Repository existingRepository = repositoryRepository.findByNameWithOwnerWithEagerPullRequests(nameWithOwner);
        if (existingRepository != null) {
            logger.info("Found existing repository: " + existingRepository);
            return existingRepository;
        }

        GHRepository ghRepo = github.getRepository(nameWithOwner);
        Repository repository = repositoryConverter.convert(ghRepo);
        if (repository == null) {
            logger.error("Error while fetching repository!");
            return null;
        }
        // preliminary save to make it referenceable
        repository = repositoryRepository.save(repository);

        Set<PullRequest> prs = getPullRequestsFromGHRepository(ghRepo, repository);
        logger.info("Found " + prs.size() + " PRs");
        repository.setPullRequests(prs);

        pullRequestRepository.saveAll(prs);
        userRepository.saveAll(users);
        repositoryRepository.save(repository);
        prReviewRepository.saveAll(reviews);
        return repository;
    }

    private Set<PullRequest> getPullRequestsFromGHRepository(GHRepository ghRepo, Repository repository)
            throws IOException {
        PagedIterator<GHPullRequest> pullRequests = ghRepo.queryPullRequests()
                // .state(GHIssueState.ALL)
                .sort(Sort.UPDATED)
                .direction(GHDirection.DESC)
                .list().withPageSize(100).iterator();
        Set<PullRequest> prs = new HashSet<>();
        while (pullRequests.hasNext()) {
            List<GHPullRequest> nextPage = pullRequests.nextPage();
            if (!isResourceRecent(nextPage.get(0))) {
                break;
            }
            logger.info("Fetched " + nextPage.size() + " PRs");
            prs.addAll(nextPage.stream().map(pr -> {
                if (!isResourceRecent(pr)) {
                    return null;
                }
                PullRequest pullRequest = pullRequestRepository.save(pullRequestConverter.convert(pr));
                pullRequest.setRepository(repository);
                try {
                    Collection<IssueComment> comments = getCommentsFromGHPullRequest(pr, pullRequest);
                    comments = commentRepository.saveAll(comments);
                    for (IssueComment c : comments) {
                        pullRequest.addComment(c);
                    }
                } catch (IOException e) {
                    logger.error("Error while fetching PR comments!");
                    pullRequest.setComments(new HashSet<>());
                }
                try {
                    User prAuthor = getUserFromGHUser(pr.getUser());
                    prAuthor.addPullRequest(pullRequest);
                    pullRequest.setAuthor(prAuthor);
                } catch (IOException e) {
                    // logger.error("Error while fetching PR author!");
                    pullRequest.setAuthor(null);
                }

                try {
                    Set<PullRequestReview> newReviews = pr.listReviews().toList().stream().map(review -> {
                        PullRequestReview prReview = prReviewRepository
                                .save(reviewConverter.convert(review));
                        try {
                            User reviewAuthor = getUserFromGHUser(review.getUser());
                            reviewAuthor.addReview(prReview);
                            prReview.setAuthor(reviewAuthor);
                        } catch (IOException e) {
                            // Dont mind this error as it occurs only for bots
                            // logger.error("Error while fetching review owner!");
                        }
                        prReview.setPullRequest(pullRequest);
                        return prReview;
                    }).collect(Collectors.toSet());
                    for (PullRequestReview prReview : newReviews) {
                        pullRequest.addReview(prReview);
                        reviews.add(prReview);
                    }
                    logger.info("Found " + newReviews.size() + " reviews for PR " + pullRequest.getId());
                } catch (IOException e) {
                    logger.error("Error while fetching PR reviews!");
                    pullRequest.setReviews(new HashSet<>());
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
            // logger.error("Error while fetching author!" + comment.getPullRequestUrl() + "
            // " + comment.getCreatedAt());
            commentAuthor = null;
        }
        prrc.setAuthor(commentAuthor);
        prReview.addComment(prrc);
        return prrc;
    }

    /**
     * Retrieves the comments of a given pull request.
     * 
     * @param pr          The GH pull request.
     * @param pullRequest Stored PR to which the comments belong.
     * @return The comments of the given pull request.
     * @throws IOException
     */
    @Transactional
    private Set<IssueComment> getCommentsFromGHPullRequest(GHPullRequest pr, PullRequest pullRequest)
            throws IOException {
        return pr.queryComments().list().withPageSize(100).toList().stream()
                .map(comment -> {
                    IssueComment c = commentRepository.save(commentConverter.convert(comment));
                    c.setPullRequest(pullRequest);
                    User commentAuthor;
                    try {
                        commentAuthor = getUserFromGHUser(comment.getUser());
                        commentAuthor.addIssueComment(c);
                    } catch (IOException e) {
                        // Dont mind this error as it occurs only for bots
                        // logger.error("Error while fetching author!" + comment.getHtmlUrl() + " " +
                        // comment.getCreatedAt());
                        commentAuthor = null;
                    }
                    c.setAuthor(commentAuthor);
                    return c;
                }).collect(Collectors.toSet());
    }

    private User getUserFromGHUser(org.kohsuke.github.GHUser user) {
        Optional<User> ghUser = users.stream().filter(u -> u.getLogin().equals(user.getLogin())).findFirst();
        if (ghUser.isPresent()) {
            return ghUser.get();
        }
        ghUser = userRepository.findUserEagerly(user.getLogin());
        if (ghUser.isPresent()) {
            User u = ghUser.get();
            if (!users.contains(u)) {
                users.add(u);
            }
            return u;
        }
        logger.info("Creating user " + user.getLogin());
        User u = userRepository.save(userConverter.convert(user));
        users.add(u);
        return u;
    }

    private PullRequestReview getPRRFromReviewId(Long reviewId) {
        Optional<PullRequestReview> prReview = reviews.stream().filter(prr -> prr.getId().equals(reviewId)).findFirst();
        if (prReview.isPresent()) {
            return prReview.get();
        }
        logger.error("Cannot find PRR with ID " + reviewId);
        return null;
    }

    /**
     * Checks if the resource has been created within the last week.
     * 
     * @param obj
     * @return
     */
    private boolean isResourceRecent(GHObject obj) {
        if (obj.getClass().equals(GHPullRequest.class)) {
            try {
                logger.info("Checking if PR is recent: " + obj.getUpdatedAt() + ", "
                        + obj.getCreatedAt());
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try {
            return obj.getUpdatedAt() != null
                    && obj.getUpdatedAt().after(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * timeframe));
        } catch (IOException e) {
            logger.error("Error while fetching createdAt! Resource ID: " + obj.getId());
            return false;
        }
    }
}
