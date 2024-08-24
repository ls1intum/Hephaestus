package de.tum.in.www1.hephaestus.codereview;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import org.springframework.data.domain.Example;
import org.springframework.graphql.client.HttpSyncGraphQlClient;

import de.tum.in.www1.hephaestus.EnvConfig;
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

        private final EnvConfig envConfig;

        public CodeReviewService(EnvConfig envConfig, RepositoryRepository repositoryRepository,
                        PullrequestRepository pullrequestRepository, CommentRepository commentRepository) {
                logger.info("Hello from CodeReviewService!");

                this.envConfig = envConfig;
                this.repositoryRepository = repositoryRepository;
                this.pullrequestRepository = pullrequestRepository;
                this.commentRepository = commentRepository;

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
         * Rest API implementation of fetching the hephaestus Github repository.
         * 
         * @return The hephaestus repository.
         */
        public Repository fetchHephaestus() throws IOException {
                if (github == null) {
                        logger.error("GitHub client not initialized!");
                        return null;
                }

                GHRepository ghRepo = github.getRepository("ls1intum/hephaestus");
                logger.info("Fetched from GitHub: " + ghRepo.toString());
                Repository repository = new RepositoryConverter().convert(ghRepo);
                if (repository == null) {
                        logger.error("Error while fetching repository!");
                        return null;
                }
                // preliminary save to make it referenceable
                repositoryRepository.save(repository);

                PullrequestConverter prConverter = new PullrequestConverter();
                CommentConverter commentConverter = new CommentConverter();

                List<Pullrequest> prs = ghRepo.queryPullRequests().list().withPageSize(1).toList().stream().map(pr -> {
                        Pullrequest pullrequest = prConverter.convert(pr);
                        if (pullrequest != null) {
                                pullrequest.setRepository(repository);
                                pullrequestRepository.save(pullrequest);
                                try {
                                        List<Comment> comments = pr.queryComments().list().toList().stream()
                                                        .map(comment -> {
                                                                Comment c = commentConverter.convert(comment);
                                                                if (c != null) {
                                                                        c.setPullrequest(pullrequest);
                                                                }
                                                                return c;
                                                        }).collect(Collectors.toList());
                                        pullrequest.setComments(comments);
                                        commentRepository.saveAll(comments);
                                } catch (IOException e) {
                                        logger.error("Error while fetching comments!");
                                        pullrequest.setComments(new ArrayList<>());
                                }
                        }
                        return pullrequest;
                }).collect(Collectors.toList());
                repository.setPullRequests(prs);
                pullrequestRepository.saveAll(prs);
                repositoryRepository.save(repository);
                return repository;
        }

        /**
         * GraphQL implementation of fetching the hephaestus Github repository.
         * 
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

        public List<Repository> getAllRepositories() {
                return repositoryRepository.findAll();
        }

}
