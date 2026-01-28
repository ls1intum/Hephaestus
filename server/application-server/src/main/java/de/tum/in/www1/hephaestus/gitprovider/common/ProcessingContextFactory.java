package de.tum.in.www1.hephaestus.gitprovider.common;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RepositoryScopeFilter;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Factory for creating ProcessingContext instances.
 * <p>
 * This factory creates contexts for repository-scoped webhook events (issues, PRs, etc.)
 * and sync operations. It resolves the scope ID from the repository's organization.
 * <p>
 * <b>Repository filtering:</b> Events are filtered against the configured scope filter
 * (e.g., {@code hephaestus.sync.filters.allowed-repositories}) to prevent processing
 * events for repositories that exist in the database but shouldn't be synced.
 * <p>
 * <b>Installation events are handled differently:</b>
 * Installation events (installation, installation_repositories, installation_target) are
 * account-level events without repository context. They use the installation ID directly
 * to resolve the scope via the host application's scope repository.
 * These handlers don't use ProcessingContext.
 */
@Service
public class ProcessingContextFactory {

    private static final Logger log = LoggerFactory.getLogger(ProcessingContextFactory.class);

    private final RepositoryRepository repositoryRepository;
    private final ScopeIdResolver scopeIdResolver;
    private final RepositoryScopeFilter repositoryScopeFilter;

    public ProcessingContextFactory(
        RepositoryRepository repositoryRepository,
        ScopeIdResolver scopeIdResolver,
        RepositoryScopeFilter repositoryScopeFilter
    ) {
        this.repositoryRepository = repositoryRepository;
        this.scopeIdResolver = scopeIdResolver;
        this.repositoryScopeFilter = repositoryScopeFilter;
    }

    /**
     * Create a ProcessingContext for a webhook event.
     * <p>
     * This method applies the configured repository scope filter to ensure events
     * are only processed for repositories that are explicitly allowed. A repository
     * may exist in the database (from workspace configuration) but still be filtered
     * out from active syncing.
     */
    @Transactional(readOnly = true)
    public Optional<ProcessingContext> forWebhookEvent(GitHubWebhookEvent event) {
        if (event.repository() == null || event.repository().fullName() == null) {
            log.warn("Skipped webhook event: reason=missingRepositoryData, action={}", event.action());
            return Optional.empty();
        }

        String repoFullName = event.repository().fullName();

        // Check filter BEFORE database lookup to avoid unnecessary queries
        if (!repositoryScopeFilter.isRepositoryAllowed(repoFullName)) {
            log.debug(
                "Skipped webhook event: reason=repositoryFiltered, repoName={}, action={}",
                sanitizeForLog(repoFullName),
                event.action()
            );
            return Optional.empty();
        }

        Repository repository = repositoryRepository.findByNameWithOwner(repoFullName).orElse(null);

        if (repository == null) {
            log.debug(
                "Skipped webhook event: reason=repositoryNotFound, repoName={}, action={}",
                sanitizeForLog(repoFullName),
                event.action()
            );
            return Optional.empty();
        }

        Long scopeId = resolveScopeId(repository);
        ProcessingContext context = ProcessingContext.forWebhook(scopeId, repository, event.action());

        return Optional.of(context);
    }

    /**
     * Create a ProcessingContext for a sync operation.
     */
    @Transactional(readOnly = true)
    public ProcessingContext forSync(Repository repository) {
        Long scopeId = resolveScopeId(repository);
        return ProcessingContext.forSync(scopeId, repository);
    }

    /**
     * Resolves the scope ID (workspace ID) for a repository.
     * <p>
     * Resolution strategy:
     * <ol>
     *   <li>For organization-owned repos: lookup by organization login</li>
     *   <li>For personal repos (no organization): lookup by repository nameWithOwner</li>
     *   <li>Fallback for org repos: if org lookup fails, try repository lookup</li>
     * </ol>
     * <p>
     * This ensures activity events are tracked for ALL repository types, not just
     * organization-owned ones.
     *
     * @param repository the repository to resolve scope for
     * @return the scope ID, or null if no matching workspace found
     */
    private Long resolveScopeId(Repository repository) {
        // Try organization-based lookup first (most common case)
        if (repository.getOrganization() != null) {
            String orgLogin = repository.getOrganization().getLogin();
            Long scopeId = scopeIdResolver.findScopeIdByOrgLogin(orgLogin).orElse(null);
            if (scopeId != null) {
                return scopeId;
            }
            // Organization lookup failed - fall through to repository-based lookup
            log.debug(
                "Organization lookup failed for repo, trying repository-based lookup: repo={}, org={}",
                sanitizeForLog(repository.getNameWithOwner()),
                sanitizeForLog(orgLogin)
            );
        }

        // Personal repo or fallback: lookup by repository nameWithOwner
        // This finds the workspace that has this repository in its monitored list
        return scopeIdResolver.findScopeIdByRepositoryName(repository.getNameWithOwner()).orElse(null);
    }
}
