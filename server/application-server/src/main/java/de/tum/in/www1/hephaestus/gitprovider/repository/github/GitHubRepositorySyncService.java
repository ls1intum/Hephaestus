package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import java.io.IOException;
import java.util.List;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositorySearchBuilder;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
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

    @Async
    public void fetchAllRepositoriesOfOwnerAsync(String owner) {
        var builder = github.searchRepositories().user(owner);
        fetchRepository(builder);
    }

    @Async
    public void fetchRepositoriesAsync(List<String> nameWithOwners) {
        var builder = github.searchRepositories()
                .q(String.join(" OR ", nameWithOwners.stream().map(nameWithOwner -> "repo:" + nameWithOwner).toList()));
        fetchRepository(builder);
    }

    private void fetchRepository(GHRepositorySearchBuilder builder) {
        var iterator = builder.list().withPageSize(100).iterator();
        while (iterator.hasNext()) {
            var ghRepositories = iterator.nextPage();
            ghRepositories.forEach(this::processRepository);
        }
    }

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
                }).orElseGet(
                        () -> {
                            var repository = repositoryConverter.convert(ghRepository);
                            return repositoryRepository.save(repository);
                        });

        if (result == null) {
            return null;
        }

        return repositoryRepository.save(result);
    }
}
