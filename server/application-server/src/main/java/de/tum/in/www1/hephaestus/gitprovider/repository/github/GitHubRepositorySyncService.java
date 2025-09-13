package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GitHubRepositorySyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubRepositorySyncService.class);

    @Autowired
    private GitHub github;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private GitHubRepositoryConverter repositoryConverter;

    /**
     * Syncs all repositories owned by a specific GitHub user or organization.
     *
     * @param owner The GitHub username (login) of the repository owner.
     */
    public void syncAllRepositoriesOfOwner(String owner) {
        var builder = github.searchRepositories().user(owner);
        var iterator = builder.list().withPageSize(100).iterator();
        while (iterator.hasNext()) {
            var ghRepositories = iterator.nextPage();
            ghRepositories.forEach(this::processRepository);
        }
    }

    /**
     * Syncs a list of repositories specified by their full names (e.g.,
     * "owner/repo").
     *
     * @param nameWithOwners A list of repository full names in the format
     *                       "owner/repo".
     * @return A list of successfully fetched GitHub repositories.
     */
    public List<GHRepository> syncAllRepositories(Set<String> nameWithOwners) {
        return nameWithOwners
            .stream()
            .map(this::syncRepository)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    /**
     * Syncs a single GitHub repository by its full name (e.g., "owner/repo").
     *
     * @param nameWithOwner The full name of the repository in the format
     *                      "owner/repo".
     * @return An optional containing the fetched GitHub repository, or an empty
     *         optional if the repository could not be fetched.
     */
    public Optional<GHRepository> syncRepository(String nameWithOwner) {
        try {
            var repository = github.getRepository(nameWithOwner);
            processRepository(repository);
            return Optional.of(repository);
        } catch (IOException e) {
            logger.error("Failed to fetch repository {}: {}", nameWithOwner, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Processes a single GitHub repository by updating or creating it in the local
     * repository.
     *
     * @param ghRepository The GitHub repository data to process.
     * @return The updated or newly created Repository entity, or {@code null} if an
     *         error occurred during update.
     */
    @Transactional
    public Repository processRepository(GHRepository ghRepository) {
        var result = repositoryRepository
            .findById(ghRepository.getId())
            .map(repository -> {
                try {
                    if (
                        repository.getUpdatedAt() == null ||
                        repository.getUpdatedAt().isBefore(ghRepository.getUpdatedAt())
                    ) {
                        return repositoryConverter.update(ghRepository, repository);
                    }
                    return repository;
                } catch (IOException e) {
                    logger.error("Failed to update repository {}: {}", ghRepository.getId(), e.getMessage());
                    return null;
                }
            })
            .orElseGet(() -> repositoryConverter.convert(ghRepository));

        if (result == null) {
            return null;
        }

        return repositoryRepository.save(result);
    }

    /**
     * Upserts a repository entity from minimal installation webhook data.
     *
     * @param id            GitHub repository ID
     * @param nameWithOwner Full name (owner/repo)
     * @param name          Repository name
     * @param isPrivate     Whether the repository is private
     * @return persisted Repository entity
     */
    @Transactional
    public Repository upsertFromInstallationPayload(long id, String nameWithOwner, String name, boolean isPrivate) {
        var now = Instant.now();
        var repository = repositoryRepository
            .findById(id)
            .orElseGet(() -> {
                var r = new Repository();
                r.setId(id);
                r.setCreatedAt(now);
                return r;
            });

        repository.setUpdatedAt(now);
        repository.setName(name);
        repository.setNameWithOwner(nameWithOwner);
        repository.setPrivate(isPrivate);
        repository.setHtmlUrl("https://github.com/" + nameWithOwner);
        // Fields not present in installation payload – set safe defaults
        if (repository.getPushedAt() == null) {
            repository.setPushedAt(now);
        }
        repository.setArchived(false);
        repository.setDisabled(false);
        repository.setVisibility(isPrivate ? Repository.Visibility.PRIVATE : Repository.Visibility.PUBLIC);
        if (repository.getDefaultBranch() == null) {
            repository.setDefaultBranch("main");
        }
        repository.setHasIssues(true);
        repository.setHasProjects(false);
        repository.setHasWiki(false);
        return repositoryRepository.save(repository);
    }

    /**
     * Deletes repositories by id, ensuring team join cleanup before removal.
     *
     * @param ids repository ids to delete; no-op if null or empty
     */
    @Transactional
    public void deleteRepositoriesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        var repos = repositoryRepository.findAllById(ids);
        repositoryRepository.deleteAll(repos);
    }
}
