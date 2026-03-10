package de.tum.in.www1.hephaestus.gitprovider.repository.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.gitlab.dto.GitLabProjectEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitLab project webhook events for real-time repository lifecycle updates.
 * <p>
 * Processes {@code project_create}, {@code project_destroy}, {@code project_rename},
 * and {@code project_transfer} events that are normalized to the "project" event key
 * by the webhook-ingest layer.
 * <p>
 * These events are only available on group-level webhooks.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabProjectEventMessageHandler extends GitLabMessageHandler<GitLabProjectEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabProjectEventMessageHandler.class);

    private final RepositoryRepository repositoryRepository;
    private final OrganizationRepository organizationRepository;
    private final GitProviderRepository gitProviderRepository;
    private final GitLabProperties gitLabProperties;

    GitLabProjectEventMessageHandler(
        RepositoryRepository repositoryRepository,
        OrganizationRepository organizationRepository,
        GitProviderRepository gitProviderRepository,
        GitLabProperties gitLabProperties,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitLabProjectEventDTO.class, deserializer, transactionTemplate);
        this.repositoryRepository = repositoryRepository;
        this.organizationRepository = organizationRepository;
        this.gitProviderRepository = gitProviderRepository;
        this.gitLabProperties = gitLabProperties;
    }

    @Override
    public GitLabEventType getEventType() {
        return GitLabEventType.PROJECT;
    }

    @Override
    protected void handleEvent(GitLabProjectEventDTO event) {
        String safePath = sanitizeForLog(event.pathWithNamespace());
        log.info(
            "Received project event: eventName={}, path={}, projectId={}",
            event.eventName(),
            safePath,
            event.projectId()
        );

        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, gitLabProperties.defaultServerUrl())
            .orElse(null);

        if (provider == null) {
            log.warn("GitProvider not found for GITLAB, skipping project event");
            return;
        }

        if (event.isCreation()) {
            handleProjectCreate(event, provider);
        } else if (event.isDeletion()) {
            handleProjectDestroy(event, provider);
        } else if (event.isRename() || event.isTransfer()) {
            handleProjectRenameOrTransfer(event, provider);
        } else {
            log.debug("Unhandled project event action: eventName={}", event.eventName());
        }
    }

    private void handleProjectCreate(GitLabProjectEventDTO event, GitProvider provider) {
        Long providerId = provider.getId();
        long nativeId = event.projectId();

        // Check if already exists
        if (repositoryRepository.findByNativeIdAndProviderId(nativeId, providerId).isPresent()) {
            log.debug("Repository already exists, skipping create: nativeId={}", nativeId);
            return;
        }

        // Create new repository entity
        Repository repo = new Repository();
        repo.setNativeId(nativeId);
        repo.setProvider(provider);
        repo.setName(event.name());
        repo.setNameWithOwner(event.pathWithNamespace());
        repo.setPrivate("private".equalsIgnoreCase(event.projectVisibility()));

        // Construct HTML URL from server URL + path
        String serverUrl = gitLabProperties.defaultServerUrl();
        if (serverUrl != null && event.pathWithNamespace() != null) {
            String baseUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
            repo.setHtmlUrl(baseUrl + event.pathWithNamespace());
        }

        // Link to organization if the group is already synced
        String groupPath = extractGroupPath(event.pathWithNamespace());
        if (groupPath != null) {
            Organization org = organizationRepository
                .findByLoginIgnoreCaseAndProviderId(groupPath, providerId)
                .orElse(null);
            if (org != null) {
                repo.setOrganization(org);
            }
        }

        repositoryRepository.save(repo);
        log.info(
            "Created repository from project event: nativeId={}, path={}",
            nativeId,
            sanitizeForLog(event.pathWithNamespace())
        );
    }

    private void handleProjectDestroy(GitLabProjectEventDTO event, GitProvider provider) {
        Long providerId = provider.getId();
        long nativeId = event.projectId();

        repositoryRepository
            .findByNativeIdAndProviderId(nativeId, providerId)
            .ifPresentOrElse(
                repo -> {
                    repositoryRepository.delete(repo);
                    log.info(
                        "Deleted repository from project event: nativeId={}, path={}",
                        nativeId,
                        sanitizeForLog(event.pathWithNamespace())
                    );
                },
                () ->
                    log.debug(
                        "Repository not found for deletion: nativeId={}, path={}",
                        nativeId,
                        sanitizeForLog(event.pathWithNamespace())
                    )
            );
    }

    private void handleProjectRenameOrTransfer(GitLabProjectEventDTO event, GitProvider provider) {
        Long providerId = provider.getId();
        long nativeId = event.projectId();

        repositoryRepository
            .findByNativeIdAndProviderId(nativeId, providerId)
            .ifPresentOrElse(
                repo -> {
                    String oldPath = repo.getNameWithOwner();
                    String newPath = event.pathWithNamespace();
                    String newName = event.name();

                    if (newPath != null) {
                        repo.setNameWithOwner(newPath);
                    }
                    if (newName != null) {
                        repo.setName(newName);
                    }

                    // Update HTML URL
                    String serverUrl = gitLabProperties.defaultServerUrl();
                    if (serverUrl != null && newPath != null) {
                        String baseUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
                        repo.setHtmlUrl(baseUrl + newPath);
                    }

                    // Update visibility if provided
                    if (event.projectVisibility() != null) {
                        repo.setPrivate("private".equalsIgnoreCase(event.projectVisibility()));
                    }

                    // Re-link organization for transfers (new group may differ)
                    if (event.isTransfer()) {
                        String groupPath = extractGroupPath(newPath);
                        if (groupPath != null) {
                            Organization org = organizationRepository
                                .findByLoginIgnoreCaseAndProviderId(groupPath, providerId)
                                .orElse(null);
                            if (org != null) {
                                repo.setOrganization(org);
                            }
                        }
                    }

                    repositoryRepository.save(repo);
                    log.info(
                        "Updated repository from {} event: nativeId={}, oldPath={}, newPath={}",
                        event.eventName(),
                        nativeId,
                        sanitizeForLog(oldPath),
                        sanitizeForLog(newPath)
                    );
                },
                () ->
                    log.debug(
                        "Repository not found for {}: nativeId={}, path={}",
                        event.eventName(),
                        nativeId,
                        sanitizeForLog(event.pathWithNamespace())
                    )
            );
    }

    /**
     * Extracts the parent group path from a project's path_with_namespace.
     */
    @org.springframework.lang.Nullable
    private static String extractGroupPath(@org.springframework.lang.Nullable String pathWithNamespace) {
        if (pathWithNamespace == null || pathWithNamespace.isBlank()) {
            return null;
        }
        int lastSlash = pathWithNamespace.lastIndexOf('/');
        if (lastSlash <= 0) {
            return null;
        }
        return pathWithNamespace.substring(0, lastSlash);
    }
}
