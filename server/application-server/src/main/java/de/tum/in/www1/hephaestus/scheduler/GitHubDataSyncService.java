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

import de.tum.in.www1.hephaestus.codereview.comment.IssueComment;
import de.tum.in.www1.hephaestus.codereview.comment.IssueCommentConverter;
import de.tum.in.www1.hephaestus.codereview.comment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequestConverter;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.codereview.repository.Repository;
import de.tum.in.www1.hephaestus.codereview.repository.RepositoryConverter;
import de.tum.in.www1.hephaestus.codereview.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.codereview.user.User;
import de.tum.in.www1.hephaestus.codereview.user.UserConverter;
import de.tum.in.www1.hephaestus.codereview.user.UserRepository;

@Service
public class GitHubDataSyncService {
    private static final Logger logger = LoggerFactory.getLogger(GitHubDataSyncService.class);

    @Value("${github.authToken}")
    private String ghAuthToken;

    private GitHub github;

    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullrequestRepository;
    private final IssueCommentRepository commentRepository;
    private final UserRepository userRepository;

    public GitHubDataSyncService(RepositoryRepository repositoryRepository, PullRequestRepository pullrequestRepository,
            IssueCommentRepository commentRepository, UserRepository userRepository) {
        logger.info("Hello from GitHubDataSyncService!");

        this.repositoryRepository = repositoryRepository;
        this.pullrequestRepository = pullrequestRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
    }

    public void syncData(String repositoryName) throws IOException {
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
        Repository existingRepository = repositoryRepository.findByNameWithOwner(nameWithOwner);
        if (existingRepository != null) {
            logger.info("Found existing repository: " + existingRepository);
            return existingRepository;
        }

        GHRepository ghRepo = github.getRepository(nameWithOwner);
        Repository repository = new RepositoryConverter().convert(ghRepo);
        if (repository == null) {
            logger.error("Error while fetching repository!");
            return null;
        }
        // preliminary save to make it referenceable
        repositoryRepository.save(repository);

        PullRequestConverter prConverter = new PullRequestConverter();

        // Retrieve PRs in pages of 10
        Set<PullRequest> prs = ghRepo.queryPullRequests().list().withPageSize(10).toList().stream().map(pr -> {
            PullRequest pullrequest = prConverter.convert(pr);
            pullrequest.setRepository(repository);
            pullrequestRepository.save(pullrequest);
            try {
                Set<IssueComment> comments = getCommentsFromGHPullRequest(pr, pullrequest);
                pullrequest.setComments(comments);
                commentRepository.saveAll(comments);
            } catch (IOException e) {
                logger.error("Error while fetching PR comments!");
                pullrequest.setComments(new HashSet<>());
            }
            try {
                pullrequest.setAuthor(getActorFromGHUser(pr.getUser()));
            } catch (IOException e) {
                logger.error("Error while fetching PR author!");
                pullrequest.setAuthor(null);
            }

            return pullrequest;
        }).collect(Collectors.toSet());
        repository.setPullRequests(prs);
        pullrequestRepository.saveAll(prs);
        repositoryRepository.save(repository);
        return repository;
    }

    /**
     * Retrieves the comments of a given pull request.
     * 
     * @param pr          The GH pull request.
     * @param pullrequest Stored PR to which the comments belong.
     * @return The comments of the given pull request.
     * @throws IOException
     */
    private Set<IssueComment> getCommentsFromGHPullRequest(GHPullRequest pr, PullRequest pullrequest)
            throws IOException {
        IssueCommentConverter commentConverter = new IssueCommentConverter();
        Set<IssueComment> comments = pr.queryComments().list().toList().stream()
                .map(comment -> {
                    IssueComment c = commentConverter.convert(comment);
                    c.setPullRequest(pullrequest);
                    User author;
                    try {
                        author = getActorFromGHUser(comment.getUser());
                        author.addComment(c);
                        author.addPullRequest(pullrequest);
                    } catch (IOException e) {
                        logger.error("Error while fetching author!");
                        author = null;
                    }
                    c.setAuthor(author);
                    return c;
                }).collect(Collectors.toSet());
        return comments;
    }

    private User getActorFromGHUser(org.kohsuke.github.GHUser user) {
        User ghUser = userRepository.findUser(user.getLogin()).orElse(null);
        if (ghUser == null) {
            ghUser = new UserConverter().convert(user);
            userRepository.save(ghUser);
        }
        return ghUser;

    }
}
