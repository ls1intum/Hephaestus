package de.tum.in.www1.hephaestus.gitprovider.common;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Factory for creating ProcessingContext instances.
 * <p>
 * This eliminates the 15+ lines of duplicate code in every message handler
 * for looking up repositories and workspaces.
 * <p>
 * <b>Before:</b> Each handler had copy-pasted:
 *
 * <pre>
 * Repository repository = repositoryRepository.findByNameWithOwner(...);
 * if (repository == null) { logger.warn(...); return; }
 * Long workspaceId = null;
 * if (repository.getOrganization() != null) {
 *     workspaceId = workspaceRepository.findByOrganization_Login(...)...
 * }
 * ProcessingContext context = ProcessingContext.forWebhook(...);
 * </pre>
 * <p>
 * <b>After:</b> One line:
 *
 * <pre>
 * ProcessingContext context = contextFactory.forWebhookEvent(event).orElseThrow();
 * </pre>
 */
@Service
public class ProcessingContextFactory {

    private static final Logger logger = LoggerFactory.getLogger(ProcessingContextFactory.class);

    private final RepositoryRepository repositoryRepository;
    private final WorkspaceRepository workspaceRepository;

    public ProcessingContextFactory(
        RepositoryRepository repositoryRepository,
        WorkspaceRepository workspaceRepository
    ) {
        this.repositoryRepository = repositoryRepository;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Create a ProcessingContext for a webhook event.
     *
     * @param event The webhook event DTO (must implement GitHubWebhookEvent)
     * @return Optional containing the context, or empty if repository not found
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
     *
     * @param repository The repository being synced
     * @return The processing context
     */
    public ProcessingContext forSync(Repository repository) {
        Long workspaceId = resolveWorkspaceId(repository);
        return ProcessingContext.forSync(workspaceId, repository);
    }

    /**
     * Resolve workspace ID from repository's organization.
     */
    private Long resolveWorkspaceId(Repository repository) {
        if (repository.getOrganization() == null) {
            return null;
        }
        String orgLogin = repository.getOrganization().getLogin();
        return workspaceRepository.findByOrganization_Login(orgLogin).map(w -> w.getId()).orElse(null);
    }
}
