package de.tum.in.www1.hephaestus.codereview;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import org.springframework.data.domain.Example;
import org.springframework.graphql.client.HttpSyncGraphQlClient;

import de.tum.in.www1.hephaestus.EnvConfig;
import de.tum.in.www1.hephaestus.codereview.actor.Actor;
import de.tum.in.www1.hephaestus.codereview.actor.ActorConverter;
import de.tum.in.www1.hephaestus.codereview.actor.ActorRepository;
import de.tum.in.www1.hephaestus.codereview.comment.Comment;
import de.tum.in.www1.hephaestus.codereview.comment.CommentConverter;
import de.tum.in.www1.hephaestus.codereview.comment.CommentRepository;
import de.tum.in.www1.hephaestus.codereview.pullrequest.Pullrequest;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullrequestConverter;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullrequestRepository;
import de.tum.in.www1.hephaestus.codereview.repository.Repository;
import de.tum.in.www1.hephaestus.codereview.repository.RepositoryConverter;
import de.tum.in.www1.hephaestus.codereview.repository.RepositoryRepository;

@Service
public class CodeReviewService {

        private static final Logger logger = LoggerFactory.getLogger(CodeReviewService.class);

        private final HttpSyncGraphQlClient graphQlClient;

        private GitHub github;

        private final RepositoryRepository repositoryRepository;
        private final PullrequestRepository pullrequestRepository;
        private final CommentRepository commentRepository;
        private final ActorRepository actorRepository;

        private final EnvConfig envConfig;

        public CodeReviewService(EnvConfig envConfig, RepositoryRepository repositoryRepository,
                        PullrequestRepository pullrequestRepository, CommentRepository commentRepository,
                        ActorRepository actorRepository) {
                logger.info("Hello from CodeReviewService!");

                this.envConfig = envConfig;
                this.repositoryRepository = repositoryRepository;
                this.pullrequestRepository = pullrequestRepository;
                this.commentRepository = commentRepository;
                this.actorRepository = actorRepository;

                RestClient restClient = RestClient.builder()
                                .baseUrl("https://api.github.com/graphql")
                                .build();

                String githubPat = this.envConfig.getGithubPat();

                graphQlClient = HttpSyncGraphQlClient.builder(restClient)
                                .headers(headers -> headers.setBearerAuth(githubPat))
                                .build();
                try {
                        github = new GitHubBuilder().withOAuthToken(envConfig.getGithubPat()).build();
                } catch (IOException e) {
                        logger.error("Error while creating GitHub client: " + e.getMessage());
                        return;
                }
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

                PullrequestConverter prConverter = new PullrequestConverter();

                // Retrieve PRs in pages of 10
                Set<Pullrequest> prs = ghRepo.queryPullRequests().list().withPageSize(10).toList().stream().map(pr -> {
                        Pullrequest pullrequest = prConverter.convert(pr);
                        pullrequest.setRepository(repository);
                        pullrequestRepository.save(pullrequest);
                        try {
                                Set<Comment> comments = getCommentsFromGHPullRequest(pr, pullrequest);
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
        private Set<Comment> getCommentsFromGHPullRequest(GHPullRequest pr, Pullrequest pullrequest)
                        throws IOException {
                CommentConverter commentConverter = new CommentConverter();
                Set<Comment> comments = pr.queryComments().list().toList().stream()
                                .map(comment -> {
                                        Comment c = commentConverter.convert(comment);
                                        c.setPullrequest(pullrequest);
                                        Actor author;
                                        try {
                                                author = getActorFromGHUser(comment.getUser());
                                                author.addComment(c);
                                                author.addPullrequest(pullrequest);
                                        } catch (IOException e) {
                                                logger.error("Error while fetching author!");
                                                author = null;
                                        }
                                        c.setAuthor(author);
                                        return c;
                                }).collect(Collectors.toSet());
                return comments;
        }

        private Actor getActorFromGHUser(GHUser user) {
                Actor actor = actorRepository.findByLogin(user.getLogin());
                if (actor == null) {
                        actor = new ActorConverter().convert(user);
                        actorRepository.save(actor);
                }
                return actor;
        }

        /**
         * GraphQL implementation of fetching the hephaestus Github repository.
         * 
         * @see #fetchRepository(String)
         * @return The hephaestus repository.
         */
        public Repository getHephaestusRepository() {
                Repository example = new Repository();
                example.setName("hephaestus");
                example.setNameWithOwner("ls1intum/hephaestus");
                Optional<Repository> foundRepo = repositoryRepository.findOne(Example.of(example));
                if (foundRepo.isPresent()) {
                        return foundRepo.get();
                }

                logger.info("No repo found, creating new one...");
                HashMap<String, Object> variables = new HashMap<>();
                variables.put("owner", "ls1intum");
                variables.put("name", "hephaestus");
                variables.put("first", 10);

                Repository repository = graphQlClient.documentName("getrepositoryprs")
                                .variables(variables)
                                .retrieveSync("repository")
                                .toEntity(Repository.class);
                if (repository == null) {
                        logger.error("Error while fetching repository!");
                        return null;
                }
                repository.setAddedAt(Instant.now());
                repositoryRepository.saveAndFlush(repository);
                return repository;
        }
}
