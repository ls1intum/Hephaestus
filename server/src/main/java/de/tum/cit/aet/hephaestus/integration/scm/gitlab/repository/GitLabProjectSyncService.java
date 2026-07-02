package de.tum.cit.aet.hephaestus.integration.scm.gitlab.repository;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlResponseHandler;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabProperties;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabSyncException;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.graphql.GitLabGroupResponse;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.graphql.GitLabProjectResponse;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.organization.GitLabGroupProcessor;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;

/**
 * Service for syncing a single GitLab project via GraphQL API.
 * <p>
 * Fetches project metadata using the {@code GetProject} query, which also
 * includes the parent group. Both the project and its group are persisted
 * in a single transaction.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.gitlab.enabled", havingValue = "true", matchIfMissing = false)
public class GitLabProjectSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabProjectSyncService.class);

    private static final String GET_PROJECT_DOCUMENT = "GetProject";

    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabGraphQlResponseHandler responseHandler;
    private final GitLabProjectProcessor projectProcessor;
    private final GitLabGroupProcessor groupProcessor;
    private final GitLabProperties gitLabProperties;
    private final IdentityProviderRepository gitProviderRepository;

    public GitLabProjectSyncService(
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabGraphQlResponseHandler responseHandler,
        GitLabProjectProcessor projectProcessor,
        GitLabGroupProcessor groupProcessor,
        GitLabProperties gitLabProperties,
        IdentityProviderRepository gitProviderRepository
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.responseHandler = responseHandler;
        this.projectProcessor = projectProcessor;
        this.groupProcessor = groupProcessor;
        this.gitLabProperties = gitLabProperties;
        this.gitProviderRepository = gitProviderRepository;
    }

    /**
     * Resolves the GitLab provider entity from the database.
     *
     * @return the GitLab provider
     * @throws IllegalStateException if no GitLab provider is found
     */
    private IdentityProvider resolveProvider() {
        return gitProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITLAB, gitLabProperties.defaultServerUrl())
            .orElseThrow(() ->
                new IllegalStateException(
                    "IdentityProvider not found for type=GITLAB, serverUrl=" + gitLabProperties.defaultServerUrl()
                )
            );
    }

    /**
     * Syncs a single GitLab project by its full path.
     * <p>
     * Also syncs the parent group (if present) to ensure the Organization
     * entity exists before linking it to the Repository.
     *
     * @param scopeId         the workspace/scope ID for authentication
     * @param projectFullPath the full path of the project (e.g., {@code org/my-project})
     * @return the synced Repository entity, or empty if not found or on error
     */
    public Optional<Repository> syncProject(Long scopeId, String projectFullPath) {
        if (projectFullPath == null || projectFullPath.isBlank()) {
            log.warn("Skipped project sync: reason=nullOrBlankProjectPath, scopeId={}", scopeId);
            return Optional.empty();
        }
        String safeProjectPath = sanitizeForLog(projectFullPath);

        try {
            IdentityProvider provider = resolveProvider();
            Long providerId = provider.getId();
            graphQlClientProvider.acquirePermission();
            HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

            ClientGraphQlResponse response = client
                .documentName(GET_PROJECT_DOCUMENT)
                .variable("fullPath", projectFullPath)
                .execute()
                .block(gitLabProperties.graphqlTimeout());

            var handleResult = responseHandler.handle(response, "project " + safeProjectPath, log);
            if (handleResult.action() != GitLabGraphQlResponseHandler.HandleResult.Action.CONTINUE) {
                graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                return Optional.empty();
            }

            graphQlClientProvider.recordSuccess();

            GitLabProjectResponse project = response.field("project").toEntity(GitLabProjectResponse.class);
            if (project == null) {
                log.warn(
                    "Skipped project sync: reason=notFoundOnGitLab, scopeId={}, projectPath={}",
                    scopeId,
                    safeProjectPath
                );
                return Optional.empty();
            }

            // Ensure parent group exists as Organization
            Organization organization = null;
            GitLabGroupResponse groupData = project.group();
            if (groupData != null) {
                organization = groupProcessor.process(groupData, providerId);
                if (organization == null) {
                    log.warn(
                        "Skipped project sync: reason=groupProcessingFailed, scopeId={}, projectPath={}",
                        scopeId,
                        safeProjectPath
                    );
                    return Optional.empty();
                }
            }

            Repository repository = projectProcessor.processGraphQlResponse(project, organization, provider);
            if (repository != null) {
                log.info(
                    "Synced project: scopeId={}, repoId={}, projectPath={}",
                    scopeId,
                    repository.getId(),
                    safeProjectPath
                );
            }

            return Optional.ofNullable(repository);
        } catch (Exception e) {
            graphQlClientProvider.recordFailure(e);
            log.error("Failed to sync project: scopeId={}, projectPath={}", scopeId, safeProjectPath, e);
            return Optional.empty();
        }
    }
}
