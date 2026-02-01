package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHOrganization;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRepository;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRepositoryOwner;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHUser;
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

    private static final String QUERY_DOCUMENT = "GetRepository";

    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final RepositoryRepository repositoryRepository;
    private final OrganizationRepository organizationRepository;
    private final GitHubSyncProperties syncProperties;
    private final GitHubExceptionClassifier exceptionClassifier;

    public GitHubRepositorySyncService(
        GitHubGraphQlClientProvider graphQlClientProvider,
        RepositoryRepository repositoryRepository,
        OrganizationRepository organizationRepository,
        GitHubSyncProperties syncProperties,
        GitHubExceptionClassifier exceptionClassifier
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.repositoryRepository = repositoryRepository;
        this.organizationRepository = organizationRepository;
        this.syncProperties = syncProperties;
        this.exceptionClassifier = exceptionClassifier;
    }

    /**
     * Syncs a single repository from GitHub.
     *
     * @param scopeId the scope ID for authentication
     * @param nameWithOwner the full repository name (owner/repo)
     * @return the synced Repository entity, or empty if not found
     */
    @Transactional
    public Optional<Repository> syncRepository(Long scopeId, String nameWithOwner) {
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn(
                "Skipped repository sync: reason=invalidNameFormat, scopeId={}, repoName={}",
                scopeId,
                safeNameWithOwner
            );
            return Optional.empty();
        }
        String repoOwner = parsedName.get().owner();
        String repoName = parsedName.get().name();

        try {
            HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
            ClientGraphQlResponse response = client
                .documentName(QUERY_DOCUMENT)
                .variable("owner", repoOwner)
                .variable("name", repoName)
                .execute()
                .block(syncProperties.graphqlTimeout());

            if (response == null || !response.isValid()) {
                log.warn(
                    "Failed to fetch repository: scopeId={}, repoName={}, errors={}",
                    scopeId,
                    safeNameWithOwner,
                    response != null ? response.getErrors() : "null response"
                );
                return Optional.empty();
            }

            // Track rate limit from response
            graphQlClientProvider.trackRateLimit(scopeId, response);

            // Use typed GraphQL model for type-safe parsing
            var repoData = response.field("repository").toEntity(GHRepository.class);
            if (repoData == null) {
                log.warn(
                    "Skipped repository sync: reason=notFoundOnGitHub, scopeId={}, repoName={}",
                    scopeId,
                    safeNameWithOwner
                );
                return Optional.empty();
            }

            // Ensure organization exists
            GHRepositoryOwner owner = repoData.getOwner();
            Organization organization = ensureOrganization(owner);

            // Create or update repository using typed accessors
            Long githubDatabaseId = repoData.getDatabaseId() != null ? repoData.getDatabaseId().longValue() : null;
            if (githubDatabaseId == null) {
                log.warn(
                    "Skipped repository sync: reason=missingDatabaseId, scopeId={}, repoName={}",
                    scopeId,
                    safeNameWithOwner
                );
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

            // Set created at timestamp
            if (repoData.getCreatedAt() != null) {
                repository.setCreatedAt(repoData.getCreatedAt().toInstant());
            }

            // Set updated at timestamp
            if (repoData.getUpdatedAt() != null) {
                repository.setUpdatedAt(repoData.getUpdatedAt().toInstant());
            }

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
            log.info(
                "Synced repository: scopeId={}, repoId={}, repoName={}",
                scopeId,
                repository.getId(),
                safeNameWithOwner
            );

            return Optional.of(repository);
        } catch (InstallationNotFoundException e) {
            // Re-throw to abort the entire sync operation
            throw e;
        } catch (Exception e) {
            ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
            switch (classification.category()) {
                case RATE_LIMITED -> log.warn(
                    "Rate limited during repository sync: repoName={}, scopeId={}, message={}",
                    safeNameWithOwner,
                    scopeId,
                    classification.message()
                );
                case NOT_FOUND -> log.warn(
                    "Resource not found during repository sync: repoName={}, scopeId={}, message={}",
                    safeNameWithOwner,
                    scopeId,
                    classification.message()
                );
                case AUTH_ERROR -> {
                    log.error(
                        "Authentication error during repository sync: repoName={}, scopeId={}, message={}",
                        safeNameWithOwner,
                        scopeId,
                        classification.message()
                    );
                    throw e;
                }
                case RETRYABLE -> log.warn(
                    "Retryable error during repository sync: repoName={}, scopeId={}, message={}",
                    safeNameWithOwner,
                    scopeId,
                    classification.message()
                );
                default -> log.error(
                    "Unexpected error during repository sync: repoName={}, scopeId={}, message={}",
                    safeNameWithOwner,
                    scopeId,
                    classification.message(),
                    e
                );
            }
            return Optional.empty();
        }
    }

    /**
     * Ensures the organization exists, creating it if necessary.
     * Uses PostgreSQL upsert for thread-safe concurrent access.
     */
    @Nullable
    private Organization ensureOrganization(GHRepositoryOwner owner) {
        if (owner == null) {
            return null;
        }

        // GHRepositoryOwner can be GHOrganization or GHUser
        // Only handle Organization type for organization linking
        if (owner instanceof GHOrganization graphQlOrg) {
            Integer dbId = graphQlOrg.getDatabaseId();
            if (dbId == null) {
                return null;
            }

            Long databaseId = dbId.longValue();
            String login = graphQlOrg.getLogin();
            String name = graphQlOrg.getName() != null ? graphQlOrg.getName() : login;
            String url = graphQlOrg.getUrl() != null ? graphQlOrg.getUrl().toString() : null;
            String avatarUrl = graphQlOrg.getAvatarUrl() != null ? graphQlOrg.getAvatarUrl().toString() : null;

            // Use upsert for thread-safe concurrent inserts
            organizationRepository.upsert(databaseId, databaseId, login, name, avatarUrl, url);
            return organizationRepository.findById(databaseId).orElse(null);
        } else if (owner instanceof GHUser graphQlUser) {
            // User repositories - create a "virtual" organization from the user
            Integer dbId = graphQlUser.getDatabaseId();
            if (dbId == null) {
                return null;
            }

            Long databaseId = dbId.longValue();
            String login = graphQlUser.getLogin();
            String name = graphQlUser.getName() != null ? graphQlUser.getName() : login;
            String url = graphQlUser.getUrl() != null ? graphQlUser.getUrl().toString() : null;
            String avatarUrl = graphQlUser.getAvatarUrl() != null ? graphQlUser.getAvatarUrl().toString() : null;

            // Use upsert for thread-safe concurrent inserts
            organizationRepository.upsert(databaseId, databaseId, login, name, avatarUrl, url);
            return organizationRepository.findById(databaseId).orElse(null);
        }

        return null;
    }
}
