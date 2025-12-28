package de.tum.in.www1.hephaestus.gitprovider.common;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Factory for creating ProcessingContext instances.
 */
@Service
public class ProcessingContextFactory {

    private static final Logger logger = LoggerFactory.getLogger(ProcessingContextFactory.class);

    private final RepositoryRepository repositoryRepository;
    private final WorkspaceIdResolver workspaceIdResolver;

    public ProcessingContextFactory(
        RepositoryRepository repositoryRepository,
        WorkspaceIdResolver workspaceIdResolver
    ) {
        this.repositoryRepository = repositoryRepository;
        this.workspaceIdResolver = workspaceIdResolver;
    }

    /**
     * Create a ProcessingContext for a webhook event.
     */
    public Optional<ProcessingContext> forWebhookEvent(GitHubWebhookEvent event) {
        if (event.repository() == null || event.repository().fullName() == null) {
            logger.warn("Webhook event missing repository data");
            return Optional.empty();
        }

        String repoFullName = event.repository().fullName();
        Repository repository = repositoryRepository.findByNameWithOwner(repoFullName).orElse(null);

        if (repository == null) {
            logger.warn("Repository {} not found for webhook event, skipping", repoFullName);
            return Optional.empty();
        }

        Long workspaceId = resolveWorkspaceId(repository);
        ProcessingContext context = ProcessingContext.forWebhook(workspaceId, repository, event.action());

        return Optional.of(context);
    }

    /**
     * Create a ProcessingContext for a sync operation.
     */
    public ProcessingContext forSync(Repository repository) {
        Long workspaceId = resolveWorkspaceId(repository);
        return ProcessingContext.forSync(workspaceId, repository);
    }

    private Long resolveWorkspaceId(Repository repository) {
        if (repository.getOrganization() == null) {
            return null;
        }
        String orgLogin = repository.getOrganization().getLogin();
        return workspaceIdResolver.findWorkspaceIdByOrgLogin(orgLogin).orElse(null);
    }
}
