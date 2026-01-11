package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.*;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.RepositoryOwner;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for syncing GitHub repository metadata via GraphQL API.
 * <p>
 * This service fetches repository information from GitHub and creates/updates
 * the corresponding Repository entity in the database.
 */
@Service
public class GitHubRepositorySyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositorySyncService.class);

    private static final String REPOSITORY_QUERY = """
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
                isDisabled
                isFork
                isPrivate
                pushedAt
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

    public GitHubRepositorySyncService(
        GitHubGraphQlClientProvider graphQlClientProvider,
        RepositoryRepository repositoryRepository,
        OrganizationRepository organizationRepository
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.repositoryRepository = repositoryRepository;
        this.organizationRepository = organizationRepository;
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
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn("Invalid repository name format: {}", safeNameWithOwner);
            return Optional.empty();
        }
        String repoOwner = parsedName.get().owner();
        String repoName = parsedName.get().name();

        try {
            HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);
            ClientGraphQlResponse response = client
                .document(REPOSITORY_QUERY)
                .variable("owner", repoOwner)
                .variable("name", repoName)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response == null || !response.isValid()) {
                log.warn(
                    "Failed to fetch repository {}: {}",
                    safeNameWithOwner,
                    response != null ? response.getErrors() : "null response"
                );
                return Optional.empty();
            }

            // Use typed GraphQL model for type-safe parsing
            var repoData = response
                .field("repository")
                .toEntity(de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Repository.class);
            if (repoData == null) {
                log.warn("Repository {} not found on GitHub", safeNameWithOwner);
                return Optional.empty();
            }

            // Ensure organization exists
            RepositoryOwner owner = repoData.getOwner();
            Organization organization = ensureOrganization(owner);

            // Create or update repository using typed accessors
            Long githubDatabaseId = repoData.getDatabaseId() != null ? repoData.getDatabaseId().longValue() : null;
            if (githubDatabaseId == null) {
                log.warn("Repository {} missing databaseId", safeNameWithOwner);
                return Optional.empty();
            }

            Repository repository = repositoryRepository.findById(githubDatabaseId).orElseGet(Repository::new);

            repository.setId(githubDatabaseId);
            repository.setName(repoData.getName());
            repository.setNameWithOwner(repoData.getNameWithOwner());
            repository.setDescription(repoData.getDescription());
            repository.setHtmlUrl(repoData.getUrl() != null ? repoData.getUrl().toString() : null);
            repository.setOrganization(organization);

            // Set private status
            repository.setPrivate(repoData.getIsPrivate());

            // Set archived status
            repository.setArchived(repoData.getIsArchived());

            // Set disabled status
            repository.setDisabled(repoData.getIsDisabled());

            // Set pushed at timestamp
            if (repoData.getPushedAt() != null) {
                repository.setPushedAt(repoData.getPushedAt().toInstant());
            }

            // Set default branch
            if (repoData.getDefaultBranchRef() != null) {
                repository.setDefaultBranch(repoData.getDefaultBranchRef().getName());
            } else {
                repository.setDefaultBranch("main");
            }

            // Set visibility
            if (repoData.getVisibility() != null) {
                repository.setVisibility(Repository.Visibility.valueOf(repoData.getVisibility().name()));
            } else {
                repository.setVisibility(Repository.Visibility.PRIVATE);
            }

            // Mark sync timestamp
            repository.setLastSyncAt(Instant.now());

            repository = repositoryRepository.save(repository);
            log.debug("Synced repository: {}", safeNameWithOwner);

            return Optional.of(repository);
        } catch (Exception e) {
            log.error("Error syncing repository {}: {}", safeNameWithOwner, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Ensures the organization exists, creating it if necessary.
     * Uses typed RepositoryOwner model for type-safe access.
     */
    @Nullable
    private Organization ensureOrganization(RepositoryOwner owner) {
        if (owner == null) {
            return null;
        }

        // RepositoryOwner can be Organization or User
        // Only handle Organization type for organization linking
        if (owner instanceof de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Organization graphQlOrg) {
            Integer dbId = graphQlOrg.getDatabaseId();
            if (dbId == null) {
                return null;
            }

            Long databaseId = dbId.longValue();
            String login = graphQlOrg.getLogin();
            String name = graphQlOrg.getName();
            String url = graphQlOrg.getUrl() != null ? graphQlOrg.getUrl().toString() : null;
            String avatarUrl = graphQlOrg.getAvatarUrl() != null ? graphQlOrg.getAvatarUrl().toString() : null;

            Organization organization = organizationRepository
                .findByGithubId(databaseId)
                .orElseGet(() -> {
                    Organization org = new Organization();
                    org.setId(databaseId);
                    org.setGithubId(databaseId);
                    return org;
                });

            organization.setLogin(login);
            organization.setName(name != null ? name : login);
            organization.setHtmlUrl(url);
            organization.setAvatarUrl(avatarUrl);

            return organizationRepository.save(organization);
        } else if (owner instanceof de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.User graphQlUser) {
            // User repositories - create a "virtual" organization from the user
            Integer dbId = graphQlUser.getDatabaseId();
            if (dbId == null) {
                return null;
            }

            Long databaseId = dbId.longValue();
            String login = graphQlUser.getLogin();
            String name = graphQlUser.getName();
            String url = graphQlUser.getUrl() != null ? graphQlUser.getUrl().toString() : null;
            String avatarUrl = graphQlUser.getAvatarUrl() != null ? graphQlUser.getAvatarUrl().toString() : null;

            Organization organization = organizationRepository
                .findByGithubId(databaseId)
                .orElseGet(() -> {
                    Organization org = new Organization();
                    org.setId(databaseId);
                    org.setGithubId(databaseId);
                    return org;
                });

            organization.setLogin(login);
            organization.setName(name != null ? name : login);
            organization.setHtmlUrl(url);
            organization.setAvatarUrl(avatarUrl);

            return organizationRepository.save(organization);
        }

        return null;
    }
}
