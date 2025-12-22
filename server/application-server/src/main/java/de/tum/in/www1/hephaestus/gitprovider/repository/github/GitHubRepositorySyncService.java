package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubClientExecutor;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @deprecated Use webhook handlers and GraphQL-based repository sync instead.
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("deprecation")
@Service
public class GitHubRepositorySyncService {

    private GitHubRepositorySyncService self;

    private static final Logger logger = LoggerFactory.getLogger(GitHubRepositorySyncService.class);

    @Autowired
    private GitHubClientExecutor gitHubClientExecutor;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private GitHubRepositoryConverter repositoryConverter;

    @Lazy
    @Autowired
    private WorkspaceService workspaceService;

    /**
     * Syncs all repositories owned by a specific GitHub user or organization.
     *
     * @param owner The GitHub username (login) of the repository owner.
     */
    //TODO: Consider deleting this method -> not used in the application
    public void syncAllRepositoriesOfOwner(Long workspaceId, String owner) {
        try {
            gitHubClientExecutor.execute(workspaceId, gh -> {
                var builder = gh.searchRepositories().user(owner);
                var iterator = builder.list().withPageSize(100).iterator();
                while (iterator.hasNext()) {
                    var ghRepositories = iterator.nextPage();
                    ghRepositories.forEach(this::processRepository);
                }
                return null;
            });
        } catch (IOException e) {
            logger.error(
                "Failed to obtain GitHub client or list repositories for owner {}: {}",
                owner,
                e.getMessage(),
                e
            );
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
    //TODO: Consider deleting this method -> not used in the application
    public List<GHRepository> syncAllRepositories(Set<String> nameWithOwners) {
        return workspaceService
            .listAllWorkspaces()
            .stream()
            .flatMap(ws ->
                nameWithOwners
                    .stream()
                    .map(nameWithOwner -> syncRepository(ws.getId(), nameWithOwner))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
            )
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
    public Optional<GHRepository> syncRepository(Long workspaceId, String nameWithOwner) {
        try {
            return gitHubClientExecutor.execute(workspaceId, gh -> {
                try {
                    var repository = gh.getRepository(nameWithOwner);
                    self.processRepository(repository);
                    return Optional.of(repository);
                } catch (GHFileNotFoundException notFound) {
                    throw new RepositorySyncException(
                        nameWithOwner,
                        RepositorySyncException.Reason.NOT_FOUND,
                        notFound
                    );
                }
            });
        } catch (RepositorySyncException exception) {
            throw exception;
        } catch (HttpException httpException) {
            if (httpException.getResponseCode() == 403) {
                throw new RepositorySyncException(
                    nameWithOwner,
                    RepositorySyncException.Reason.FORBIDDEN,
                    httpException
                );
            }
            logger.error(
                "HTTP {} while fetching repository {}: {}",
                httpException.getResponseCode(),
                nameWithOwner,
                httpException.getMessage()
            );
            return Optional.empty();
        } catch (IOException e) {
            logger.error("Failed to fetch repository {}: {}", nameWithOwner, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Processes a single GitHub repository by updating or creating it in the local
     * repository.
     * <p>
     * @param ghRepository The GitHub repository data to process.
     * @return The updated or newly created Repository entity, or {@code null} if an
     *         error occurred during update.
     */
    @Transactional
    public Repository processRepository(GHRepository ghRepository) {
        var existing = repositoryRepository.findById(ghRepository.getId()).orElse(null);

        if (existing != null) {
            var updated = updateRepositoryIfStale(ghRepository, existing);
            return updated != null ? repositoryRepository.save(updated) : existing;
        }

        var newRepository = repositoryConverter.convert(ghRepository);
        if (newRepository == null) {
            return null;
        }

        return repositoryRepository.save(newRepository);
    }

    private Repository updateRepositoryIfStale(GHRepository ghRepository, Repository repository) {
        try {
            if (repository.getUpdatedAt() == null || repository.getUpdatedAt().isBefore(ghRepository.getUpdatedAt())) {
                return repositoryConverter.update(ghRepository, repository);
            }
            // Even if not stale, ensure organization link is set (backward compatibility for
            // repositories created before organization linking was added)
            if (repository.getOrganization() == null) {
                return repositoryConverter.update(ghRepository, repository);
            }
            return repository;
        } catch (IOException e) {
            logger.error("Failed to update repository {}: {}", ghRepository.getId(), e.getMessage());
            return repository;
        }
    }

    @Autowired
    @Lazy
    public void setSelf(GitHubRepositorySyncService self) {
        this.self = self;
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

        // Link to organization if available (extracted from nameWithOwner)
        if (repository.getOrganization() == null && nameWithOwner != null && nameWithOwner.contains("/")) {
            String ownerLogin = nameWithOwner.split("/")[0];
            organizationRepository.findByLoginIgnoreCase(ownerLogin).ifPresent(repository::setOrganization);
        }

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

    /**
     * Deletes all repositories whose nameWithOwner starts with the given owner prefix.
     * Useful for cleaning up repositories when an installation is deleted and GitHub
     * doesn't provide the repository list in the webhook payload.
     *
     * @param ownerPrefix owner prefix including trailing slash, e.g., "org-name/"
     */
    @Transactional
    public void deleteRepositoriesByOwnerPrefix(String ownerPrefix) {
        if (ownerPrefix == null || ownerPrefix.isBlank()) {
            return;
        }
        var repos = repositoryRepository.findByNameWithOwnerStartingWithIgnoreCase(ownerPrefix);
        if (repos.isEmpty()) {
            return;
        }
        logger.info("Deleting {} repositories with owner prefix '{}'", repos.size(), ownerPrefix);
        repositoryRepository.deleteAll(repos);
    }
}
