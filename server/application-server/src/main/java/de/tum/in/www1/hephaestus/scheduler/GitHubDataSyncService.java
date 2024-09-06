package de.tum.in.www1.hephaestus.scheduler;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.hephaestus.codereview.comment.IssueComment;
import de.tum.in.www1.hephaestus.codereview.comment.IssueCommentConverter;
import de.tum.in.www1.hephaestus.codereview.comment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.codereview.comment.review.ReviewComment;
import de.tum.in.www1.hephaestus.codereview.comment.review.ReviewCommentConverter;
import de.tum.in.www1.hephaestus.codereview.comment.review.ReviewCommentRepository;
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

    private GitHub github;

    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewRepository prReviewRepository;
    private final IssueCommentRepository commentRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final UserRepository userRepository;

    private final RepositoryConverter repositoryConverter;
    private final PullRequestConverter pullRequestConverter;
    private final PullRequestReviewConverter reviewConverter;
    private final IssueCommentConverter commentConverter;
    private final ReviewCommentConverter reviewCommentConverter;
    private final UserConverter userConverter;

    public GitHubDataSyncService(RepositoryRepository repositoryRepository, PullRequestRepository pullRequestRepository,
            PullRequestReviewRepository prReviewRepository,
            IssueCommentRepository commentRepository, ReviewCommentRepository reviewCommentRepository,
            UserRepository userRepository,
            RepositoryConverter repositoryConverter, PullRequestConverter pullRequestConverter,
            PullRequestReviewConverter reviewConverter, IssueCommentConverter commentConverter,
            ReviewCommentConverter reviewCommentConverter, UserConverter userConverter) {
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

    public void syncRepository(String repositoryName) throws IOException {
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
    @Transactional
    public Repository fetchRepository(String nameWithOwner) throws IOException {
        if (github == null) {
            logger.error("GitHub client not initialized correctly!");
            return null;
        }

        // Avoid double fetching of the same repository
        Repository existingRepository = repositoryRepository.findByNameWithOwner(nameWithOwner);
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
        repositoryRepository.save(repository);

        Set<PullRequest> prs = getPullRequestsFromGHRepository(ghRepo, repository);
        repository.setPullRequests(prs);
        pullRequestRepository.saveAll(prs);

        repositoryRepository.save(repository);
        return repository;
    }

    private Set<PullRequest> getPullRequestsFromGHRepository(GHRepository ghRepo, Repository repository)
            throws IOException {
        // Retrieve PRs in pages of 10
        return ghRepo.queryPullRequests().list().withPageSize(10).toList().stream().map(pr -> {
            PullRequest pullRequest = pullRequestConverter.convert(pr);
            pullRequest.setRepository(repository);
            pullRequestRepository.save(pullRequest);
            try {
                Set<IssueComment> comments = getCommentsFromGHPullRequest(pr, pullRequest);
                pullRequest.setComments(comments);
                commentRepository.saveAll(comments);
            } catch (IOException e) {
                logger.error("Error while fetching PR comments!");
                pullRequest.setComments(new HashSet<>());
            }
            try {
                User prAuthor = getUserFromGHUser(pr.getUser());
                prAuthor.addPullRequest(pullRequest);
                pullRequest.setAuthor(prAuthor);
            } catch (IOException e) {
                logger.error("Error while fetching PR author!");
                pullRequest.setAuthor(null);
            }

            try {
                Set<PullRequestReview> reviews = pr.listReviews().toList().stream().map(review -> {
                    PullRequestReview prReview = reviewConverter.convert(review);
                    try {
                        User reviewAuthor = getUserFromGHUser(review.getUser());
                        reviewAuthor.addReview(prReview);
                        prReview.setAuthor(reviewAuthor);
                    } catch (IOException e) {
                        logger.error("Error while fetching review owner!");
                    }
                    prReview.setPullRequest(pullRequest);
                    return prReview;
                }).collect(Collectors.toSet());
                prReviewRepository.saveAll(reviews);
                pullRequest.setReviews(reviews);
            } catch (IOException e) {
                logger.error("Error while fetching PR reviews!");
                pullRequest.setReviews(new HashSet<>());
            }

            try {
                pr.listReviewComments().toList().stream().forEach(comment -> {
                    ReviewComment c = reviewCommentConverter.convert(comment);
                    // First save the comment, so that it is referencable
                    reviewCommentRepository.save(c);

                    PullRequestReview review = getPullRequestReviewByReviewId(comment.getPullRequestReviewId());
                    c.setReview(review);
                    User commentAuthor;
                    try {
                        commentAuthor = getUserFromGHUser(comment.getUser());
                        commentAuthor.addReviewComment(c);
                    } catch (IOException e) {
                        logger.error("Error while fetching author!");
                        commentAuthor = null;
                    }
                    c.setAuthor(commentAuthor);
                    review.addComment(c);
                });
            } catch (IOException e) {
                logger.error("Error while fetching PR review comments!");
            }

            return pullRequest;
        }).collect(Collectors.toSet());
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
        return pr.queryComments().list().toList().stream()
                .map(comment -> {
                    IssueComment c = commentConverter.convert(comment);
                    c.setPullRequest(pullRequest);
                    User commentAuthor;
                    try {
                        commentAuthor = getUserFromGHUser(comment.getUser());
                        commentAuthor.addIssueComment(c);
                    } catch (IOException e) {
                        logger.error("Error while fetching author!");
                        commentAuthor = null;
                    }
                    c.setAuthor(commentAuthor);
                    return c;
                }).collect(Collectors.toSet());
    }

    private PullRequestReview getPullRequestReviewByReviewId(Long reviewId) {
        return prReviewRepository.findById(reviewId).orElse(null);
    }

    private User getUserFromGHUser(org.kohsuke.github.GHUser user) {
        User ghUser = userRepository.findUser(user.getLogin()).orElse(null);
        if (ghUser == null) {
            ghUser = userConverter.convert(user);
            userRepository.save(ghUser);
        }
        return ghUser;

    }
}
