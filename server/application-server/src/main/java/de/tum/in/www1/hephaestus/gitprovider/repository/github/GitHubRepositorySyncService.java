package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import java.io.IOException;
import java.util.List;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositorySearchBuilder;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import de.tum.in.www1.hephaestus.gitprovider.common.DateUtil;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;

@Service
public class GitHubRepositorySyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubRepositorySyncService.class);

    private final GitHub github;
    private final RepositoryRepository repositoryRepository;
    private final GitHubRepositoryConverter repositoryConverter;

    public GitHubRepositorySyncService(
            GitHub github,
            RepositoryRepository repositoryRepository,
            UserRepository userRepository,
            GitHubRepositoryConverter repositoryConverter,
            GitHubUserConverter userConverter) {
        this.github = github;
        this.repositoryRepository = repositoryRepository;
        this.repositoryConverter = repositoryConverter;
    }

    /**
     * Fetches all repositories owned by a specific GitHub user or organization and
     * processes them to synchronize with the local repository.
     *
     * @param owner The GitHub username (login) of the repository owner.
     */
    public void fetchAllRepositoriesOfOwner(String owner) {
        var builder = github.searchRepositories().user(owner);
        fetchRepositoriesWithBuilder(builder);
    }

    /**
     * Fetches a list of repositories specified by their full names (e.g.,
     * "owner/repo") and processes them to synchronize with the local repository.
     *
     * @param nameWithOwners A list of repository full names in the format
     *                       "owner/repo".
     */
    public void fetchRepositories(List<String> nameWithOwners) {
        var builder = github.searchRepositories()
                .q(String.join(" OR ", nameWithOwners.stream().map(nameWithOwner -> "repo:" + nameWithOwner).toList()));
        fetchRepositoriesWithBuilder(builder);
    }

    /**
     * Fetches repositories based on the provided search builder and processes each
     * repository.
     *
     * @param builder The GHRepositorySearchBuilder configured with search
     *                parameters.
     */
    private void fetchRepositoriesWithBuilder(GHRepositorySearchBuilder builder) {
        var iterator = builder.list().withPageSize(100).iterator();
        while (iterator.hasNext()) {
            var ghRepositories = iterator.nextPage();
            ghRepositories.forEach(this::processRepository);
        }
    }

    /**
     * Processes a single GitHub repository by either updating the existing
     * repository in the local repository
     * or creating a new one if it does not exist.
     *
     * @param ghRepository The GitHub repository data to process.
     * @return The updated or newly created Repository entity, or {@code null} if an
     *         error occurred during update.
     */
    @Transactional
    public Repository processRepository(GHRepository ghRepository) {
        var result = repositoryRepository.findById(ghRepository.getId())
                .map(repository -> {
                    try {
                        if (repository.getUpdatedAt()
                                .isBefore(DateUtil.convertToOffsetDateTime(ghRepository.getUpdatedAt()))) {
                            return repositoryConverter.update(ghRepository, repository);
                        }
                        return repository;
                    } catch (IOException e) {
                        logger.error("Failed to update repository {}: {}", ghRepository.getId(), e.getMessage());
                        return null;
                    }
                }).orElseGet(() -> repositoryConverter.convert(ghRepository));

        if (result == null) {
            return null;
        }

        return repositoryRepository.save(result);
    }
}
