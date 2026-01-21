package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryEventDTO;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub repository webhook events (deleted, archived, renamed, etc.).
 * <p>
 * This handler processes repository-level lifecycle events that affect the repository
 * entity itself, ensuring local state stays in sync with GitHub. Key actions:
 * <ul>
 *   <li><b>deleted</b>: Removes the repository and monitors, preventing orphan sync errors</li>
 *   <li><b>archived/unarchived</b>: Updates repository visibility state</li>
 *   <li><b>renamed</b>: Updates the repository name to prevent NOT_FOUND errors</li>
 * </ul>
 *
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#repository">
 *      GitHub Repository Webhook Events</a>
 */
@Component
public class GitHubRepositoryMessageHandler extends GitHubMessageHandler<GitHubRepositoryEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryMessageHandler.class);

    private final ProvisioningListener provisioningListener;
    private final RepositoryRepository repositoryRepository;

    GitHubRepositoryMessageHandler(
        ProvisioningListener provisioningListener,
        RepositoryRepository repositoryRepository,
        NatsMessageDeserializer deserializer
    ) {
        super(GitHubRepositoryEventDTO.class, deserializer);
        this.provisioningListener = provisioningListener;
        this.repositoryRepository = repositoryRepository;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.REPOSITORY;
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.REPOSITORY;
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubRepositoryEventDTO event) {
        var repositoryRef = event.repository();

        if (repositoryRef == null) {
            log.warn("Received repository event with missing repository data: action={}", event.action());
            return;
        }

        String fullName = repositoryRef.fullName();
        String safeFullName = sanitizeForLog(fullName);
        Long repositoryId = repositoryRef.id();
        GitHubEventAction.Repository action = event.actionType();

        log.info(
            "Received repository event: action={}, repoName={}, repoId={}",
            event.action(),
            safeFullName,
            repositoryId
        );

        switch (action) {
            case DELETED -> handleRepositoryDeleted(event, fullName, safeFullName);
            case ARCHIVED -> handleRepositoryArchived(fullName, safeFullName, true);
            case UNARCHIVED -> handleRepositoryArchived(fullName, safeFullName, false);
            case RENAMED -> handleRepositoryRenamed(event, safeFullName);
            case PRIVATIZED -> handleVisibilityChanged(fullName, safeFullName, true);
            case PUBLICIZED -> handleVisibilityChanged(fullName, safeFullName, false);
            default -> log.debug(
                "Skipped repository event: reason=unhandledAction, action={}, repoName={}",
                event.action(),
                safeFullName
            );
        }
    }

    /**
     * Handles repository deletion by cleaning up local state.
     * <p>
     * Uses the ProvisioningListener SPI to remove monitors and delete orphaned repository
     * entities, preventing future sync attempts from failing with NOT_FOUND errors.
     */
    private void handleRepositoryDeleted(GitHubRepositoryEventDTO event, String fullName, String safeFullName) {
        var installation = event.installation();

        if (installation == null || installation.id() == null) {
            log.warn("Cannot clean up deleted repository - missing installation ID: repoName={}", safeFullName);
            // Still try to delete the repository directly if we can find it
            deleteRepositoryByName(fullName, safeFullName);
            return;
        }

        Long installationId = installation.id();

        log.info("Processing repository deletion: repoName={}, installationId={}", safeFullName, installationId);

        // Use the ProvisioningListener SPI to properly clean up monitors AND repository
        // This triggers onRepositoriesRemoved which will remove monitors and orphaned repos
        provisioningListener.onRepositoriesRemoved(installationId, List.of(fullName));

        log.info("Completed repository deletion cleanup: repoName={}, installationId={}", safeFullName, installationId);
    }

    /**
     * Fallback deletion when installation ID is not available.
     */
    private void deleteRepositoryByName(String fullName, String safeFullName) {
        repositoryRepository
            .findByNameWithOwner(fullName)
            .ifPresent(repository -> {
                repositoryRepository.delete(repository);
                log.info("Deleted repository directly: repoName={}", safeFullName);
            });
    }

    /**
     * Handles repository archive/unarchive by updating the archived flag.
     */
    private void handleRepositoryArchived(String fullName, String safeFullName, boolean archived) {
        repositoryRepository
            .findByNameWithOwner(fullName)
            .ifPresentOrElse(
                repository -> {
                    repository.setArchived(archived);
                    repositoryRepository.save(repository);
                    log.info("Updated repository archived status: repoName={}, archived={}", safeFullName, archived);
                },
                () ->
                    log.debug(
                        "Repository not found locally for archive update: repoName={}, archived={}",
                        safeFullName,
                        archived
                    )
            );
    }

    /**
     * Handles repository rename by updating the name and full name.
     */
    private void handleRepositoryRenamed(GitHubRepositoryEventDTO event, String safeNewFullName) {
        String oldFullName = event.getOldFullName();
        String newFullName = event.repository().fullName();
        String newName = event.repository().name();

        if (oldFullName == null) {
            log.warn("Cannot process repository rename - missing old name in changes: newRepoName={}", safeNewFullName);
            return;
        }

        String safeOldFullName = sanitizeForLog(oldFullName);

        repositoryRepository
            .findByNameWithOwner(oldFullName)
            .ifPresentOrElse(
                repository -> {
                    repository.setName(newName);
                    repository.setNameWithOwner(newFullName);
                    repository.setHtmlUrl("https://github.com/" + newFullName);
                    repositoryRepository.save(repository);
                    log.info("Renamed repository: oldName={}, newName={}", safeOldFullName, safeNewFullName);
                },
                () ->
                    log.debug(
                        "Repository not found locally for rename: oldName={}, newName={}",
                        safeOldFullName,
                        safeNewFullName
                    )
            );
    }

    /**
     * Handles visibility changes (privatized/publicized).
     */
    private void handleVisibilityChanged(String fullName, String safeFullName, boolean isPrivate) {
        repositoryRepository
            .findByNameWithOwner(fullName)
            .ifPresentOrElse(
                repository -> {
                    repository.setVisibility(isPrivate ? Repository.Visibility.PRIVATE : Repository.Visibility.PUBLIC);
                    repositoryRepository.save(repository);
                    log.info(
                        "Updated repository visibility: repoName={}, visibility={}",
                        safeFullName,
                        isPrivate ? "PRIVATE" : "PUBLIC"
                    );
                },
                () ->
                    log.debug(
                        "Repository not found locally for visibility update: repoName={}, private={}",
                        safeFullName,
                        isPrivate
                    )
            );
    }
}
