package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for syncing GitHub repository metadata via GraphQL API.
 * <p>
 * This service fetches repository information from GitHub and creates/updates
 * the corresponding Repository entity in the database.
 */
@Service
public class GitHubRepositoryGraphQlSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubRepositoryGraphQlSyncService.class);

    private static final String REPOSITORY_QUERY =
        """
        query GetRepository($owner: String!, $name: String!) {
            repository(owner: $owner, name: $name) {
                id
                databaseId
                name
                nameWithOwner
                description
                url
                visibility
                isArchived
                isFork
                isPrivate
                defaultBranchRef {
                    name
                }
                owner {
                    __typename
                    ... on Organization {
                        id
                        databaseId
                        login
                        name
                        description
                        url
                        avatarUrl
                    }
                    ... on User {
                        id
                        databaseId
                        login
                        name
                        url
                        avatarUrl
                    }
                }
            }
        }
        """;

    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final RepositoryRepository repositoryRepository;
    private final OrganizationRepository organizationRepository;
    private final WorkspaceRepository workspaceRepository;

    public GitHubRepositoryGraphQlSyncService(
        GitHubGraphQlClientProvider graphQlClientProvider,
        RepositoryRepository repositoryRepository,
        OrganizationRepository organizationRepository,
        WorkspaceRepository workspaceRepository
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.repositoryRepository = repositoryRepository;
        this.organizationRepository = organizationRepository;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Syncs a single repository from GitHub.
     *
     * @param workspaceId the workspace ID for authentication
     * @param nameWithOwner the full repository name (owner/repo)
     * @return the synced Repository entity, or empty if not found
     */
    @Transactional
    public Optional<Repository> syncRepository(Long workspaceId, String nameWithOwner) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            logger.warn("Workspace {} not found", workspaceId);
            return Optional.empty();
        }

        String[] parts = nameWithOwner.split("/");
        if (parts.length != 2) {
            logger.warn("Invalid repository name: {}", nameWithOwner);
            return Optional.empty();
        }

        String owner = parts[0];
        String name = parts[1];

        try {
            HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);
            ClientGraphQlResponse response = client
                .document(REPOSITORY_QUERY)
                .variable("owner", owner)
                .variable("name", name)
                .execute()
                .block(Duration.ofSeconds(30));

            if (response == null || !response.isValid()) {
                logger.warn(
                    "Failed to fetch repository {}: {}",
                    nameWithOwner,
                    response != null ? response.getErrors() : "null response"
                );
                return Optional.empty();
            }

            Map<String, Object> repoData = response.field("repository").toEntity(Map.class);
            if (repoData == null) {
                logger.warn("Repository {} not found on GitHub", nameWithOwner);
                return Optional.empty();
            }

            // Ensure organization exists
            Map<String, Object> ownerData = (Map<String, Object>) repoData.get("owner");
            Organization organization = ensureOrganization(ownerData);

            // Create or update repository
            // Repository.id is the GitHub database ID (BaseGitServiceEntity pattern)
            Long githubDatabaseId = ((Number) repoData.get("databaseId")).longValue();
            Repository repository = repositoryRepository.findById(githubDatabaseId).orElseGet(Repository::new);

            repository.setId(githubDatabaseId);
            repository.setName((String) repoData.get("name"));
            repository.setNameWithOwner((String) repoData.get("nameWithOwner"));
            repository.setDescription((String) repoData.get("description"));
            repository.setHtmlUrl((String) repoData.get("url"));
            repository.setOrganization(organization);

            Map<String, Object> defaultBranch = (Map<String, Object>) repoData.get("defaultBranchRef");
            if (defaultBranch != null) {
                repository.setDefaultBranch((String) defaultBranch.get("name"));
            }

            String visibility = (String) repoData.get("visibility");
            repository.setVisibility(Repository.Visibility.valueOf(visibility));

            repository = repositoryRepository.save(repository);
            logger.debug("Synced repository: {}", nameWithOwner);

            return Optional.of(repository);
        } catch (Exception e) {
            logger.error("Error syncing repository {}: {}", nameWithOwner, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Organization ensureOrganization(Map<String, Object> ownerData) {
        Long databaseId = ((Number) ownerData.get("databaseId")).longValue();
        String login = (String) ownerData.get("login");
        String name = (String) ownerData.get("name");
        String url = (String) ownerData.get("url");
        String avatarUrl = (String) ownerData.get("avatarUrl");

        Organization organization = organizationRepository.findByGithubId(databaseId).orElseGet(Organization::new);

        organization.setGithubId(databaseId);
        organization.setLogin(login);
        organization.setName(name != null ? name : login);
        organization.setHtmlUrl(url);
        organization.setAvatarUrl(avatarUrl);

        return organizationRepository.save(organization);
    }
}
