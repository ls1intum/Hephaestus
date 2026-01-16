package de.tum.in.www1.hephaestus.gitprovider.common;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Factory for creating ProcessingContext instances.
 * <p>
 * This factory creates contexts for repository-scoped webhook events (issues, PRs, etc.)
 * and sync operations. It resolves the scope ID from the repository's organization.
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

    public ProcessingContextFactory(RepositoryRepository repositoryRepository, ScopeIdResolver scopeIdResolver) {
        this.repositoryRepository = repositoryRepository;
        this.scopeIdResolver = scopeIdResolver;
    }

    /**
     * Create a ProcessingContext for a webhook event.
     */
    public Optional<ProcessingContext> forWebhookEvent(GitHubWebhookEvent event) {
        if (event.repository() == null || event.repository().fullName() == null) {
            log.warn("Skipped webhook event: reason=missingRepositoryData, action={}", event.action());
            return Optional.empty();
        }

        String repoFullName = event.repository().fullName();
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
    public ProcessingContext forSync(Repository repository) {
        Long scopeId = resolveScopeId(repository);
        return ProcessingContext.forSync(scopeId, repository);
    }

    private Long resolveScopeId(Repository repository) {
        if (repository.getOrganization() == null) {
            return null;
        }
        String orgLogin = repository.getOrganization().getLogin();
        return scopeIdResolver.findScopeIdByOrgLogin(orgLogin).orElse(null);
    }
}
