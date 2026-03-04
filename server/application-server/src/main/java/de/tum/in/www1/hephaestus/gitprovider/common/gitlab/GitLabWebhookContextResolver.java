package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RepositoryScopeFilter;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Shared context resolver for GitLab webhook handlers.
 * <p>
 * Encapsulates repository lookup, scope filtering, and scope-ID resolution
 * so that all GitLab message handlers use the same logic without duplication.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabWebhookContextResolver {

    private static final Logger log = LoggerFactory.getLogger(GitLabWebhookContextResolver.class);

    private final RepositoryRepository repositoryRepository;
    private final RepositoryScopeFilter repositoryScopeFilter;
    private final ScopeIdResolver scopeIdResolver;

    GitLabWebhookContextResolver(
        RepositoryRepository repositoryRepository,
        RepositoryScopeFilter repositoryScopeFilter,
        ScopeIdResolver scopeIdResolver
    ) {
        this.repositoryRepository = repositoryRepository;
        this.repositoryScopeFilter = repositoryScopeFilter;
        this.scopeIdResolver = scopeIdResolver;
    }

    /**
     * Resolves a {@link ProcessingContext} for the given repository path and action.
     *
     * @param pathWithNamespace the GitLab project path (e.g., "group/project")
     * @param action            the webhook action string
     * @param eventLabel        a label for log messages (e.g., "issue", "merge request")
     * @return the resolved context, or null if the repository is filtered or not found
     */
    @Nullable
    public ProcessingContext resolve(String pathWithNamespace, String action, String eventLabel) {
        String safePath = sanitizeForLog(pathWithNamespace);

        if (!repositoryScopeFilter.isRepositoryAllowed(pathWithNamespace)) {
            log.debug("Skipped {} event: reason=repositoryFiltered, repoName={}", eventLabel, safePath);
            return null;
        }

        Repository repository = repositoryRepository
            .findByNameWithOwnerWithOrganization(pathWithNamespace)
            .orElse(null);

        if (repository == null) {
            log.debug("Skipped {} event: reason=repositoryNotFound, repoName={}", eventLabel, safePath);
            return null;
        }

        Long scopeId = resolveScopeId(repository);
        return ProcessingContext.forWebhook(scopeId, repository, action);
    }

    private Long resolveScopeId(Repository repository) {
        if (repository.getOrganization() != null) {
            String orgLogin = repository.getOrganization().getLogin();
            Long scopeId = scopeIdResolver.findScopeIdByOrgLogin(orgLogin).orElse(null);
            if (scopeId != null) {
                return scopeId;
            }
        }
        return scopeIdResolver.findScopeIdByRepositoryName(repository.getNameWithOwner()).orElse(null);
    }
}
