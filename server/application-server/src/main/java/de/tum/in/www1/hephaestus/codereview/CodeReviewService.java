package de.tum.in.www1.hephaestus.codereview;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

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
import de.tum.in.www1.hephaestus.codereview.repository.Repository;
import de.tum.in.www1.hephaestus.codereview.repository.RepositoryConverter;

@Service
public class CodeReviewService {

        private static final Logger logger = LoggerFactory.getLogger(CodeReviewService.class);

        private final HttpSyncGraphQlClient graphQlClient;

        private GitHub github;

        private final CodeReviewRepository codeReviewRepository;

        private final EnvConfig envConfig;

        public CodeReviewService(CodeReviewRepository codeReviewRepository, EnvConfig envConfig) {
                logger.info("Hello from CodeReviewService!");

                this.codeReviewRepository = codeReviewRepository;
                this.envConfig = envConfig;

                RestClient restClient = RestClient.builder()
                                .baseUrl("https://api.github.com/graphql")
                                .build();

                String githubPat = this.envConfig.getGithubPat();
                logger.info("Github PAT: " + githubPat);

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

        public Repository fetchHephaestus() throws IOException {
                if (github == null) {
                        logger.error("GitHub client not initialized!");
                        return null;
                }

                GHRepository ghRepo = github.getRepository("ls1intum/hephaestus");
                System.out.println("Repository: " + ghRepo.toString());
                Repository repository = new RepositoryConverter().convert(ghRepo);
                return repository;
        }

        public Repository getHephaestusRepository() {
                Repository example = new Repository();
                example.setName("hephaestus");
                example.setNameWithOwner("ls1intum/hephaestus");
                Optional<Repository> foundRepo = codeReviewRepository.findOne(Example.of(example));
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
                codeReviewRepository.saveAndFlush(repository);
                return repository;
        }

        public List<Repository> getAllRepositories() {
                return codeReviewRepository.findAll();
        }

}
