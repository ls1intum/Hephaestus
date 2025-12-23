package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GraphQlParsingUtils.*;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubBranchRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub pull requests via GraphQL API.
 * <p>
 * This service fetches PRs via GraphQL and uses GitHubPullRequestProcessor for persistence.
 * Uses Map-based parsing to handle GitHub's large database IDs (exceeding 32-bit int).
 */
@Service
public class GitHubPullRequestGraphQlSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestGraphQlSyncService.class);
    private static final String GET_PRS_DOCUMENT = "GetRepositoryPullRequests";

    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubPullRequestProcessor pullRequestProcessor;

    public GitHubPullRequestGraphQlSyncService(
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubPullRequestProcessor pullRequestProcessor
    ) {
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.pullRequestProcessor = pullRequestProcessor;
    }

    /**
     * Synchronizes all pull requests for a repository using GraphQL.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync pull requests for
     * @return number of pull requests synced
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public int syncPullRequestsForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            logger.warn("Repository {} not found, cannot sync pull requests", repositoryId);
            return 0;
        }

        String[] parts = parseRepositoryName(repository.getNameWithOwner());
        if (parts == null) {
            logger.warn("Invalid repository nameWithOwner: {}", repository.getNameWithOwner());
            return 0;
        }
        String owner = parts[0];
        String name = parts[1];

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);
        ProcessingContext context = ProcessingContext.forSync(workspaceId, repository);

        try {
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;

            while (hasNextPage) {
                ClientGraphQlResponse response = client
                    .documentName(GET_PRS_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", DEFAULT_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(DEFAULT_TIMEOUT);

                if (response == null || !response.isValid()) {
                    logger.warn(
                        "Invalid GraphQL response for pull requests: {}",
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                Map<String, Object> prsData = response.field("repository.pullRequests").toEntity(Map.class);
                if (prsData == null) {
                    break;
                }

                List<Map<String, Object>> nodes = (List<Map<String, Object>>) prsData.get("nodes");
                if (nodes == null || nodes.isEmpty()) {
                    break;
                }

                for (Map<String, Object> prData : nodes) {
                    GitHubPullRequestDTO dto = convertMapToDTO(prData);
                    PullRequest pr = pullRequestProcessor.process(dto, context);
                    if (pr != null) {
                        totalSynced++;
                    }
                }

                Map<String, Object> pageInfo = (Map<String, Object>) prsData.get("pageInfo");
                hasNextPage = pageInfo != null && parseBoolean(pageInfo.get("hasNextPage"));
                cursor = pageInfo != null ? getString(pageInfo, "endCursor") : null;
            }

            logger.info("Synced {} pull requests for repository {}", totalSynced, repository.getNameWithOwner());
            return totalSynced;
        } catch (Exception e) {
            logger.error(
                "Error syncing pull requests for repository {}: {}",
                repository.getNameWithOwner(),
                e.getMessage(),
                e
            );
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private GitHubPullRequestDTO convertMapToDTO(Map<String, Object> prData) {
        // Parse author
        GitHubUserDTO author = parseUser((Map<String, Object>) prData.get("author"));

        // Parse merged by
        GitHubUserDTO mergedBy = parseUser((Map<String, Object>) prData.get("mergedBy"));

        // Parse labels
        List<GitHubLabelDTO> labels = parseLabelList((Map<String, Object>) prData.get("labels"));

        // Parse milestone
        GitHubMilestoneDTO milestone = parseMilestone((Map<String, Object>) prData.get("milestone"));

        // Parse assignees
        List<GitHubUserDTO> assignees = parseUserList((Map<String, Object>) prData.get("assignees"));

        // Parse requested reviewers
        List<GitHubUserDTO> requestedReviewers = parseRequestedReviewers(
            (Map<String, Object>) prData.get("reviewRequests")
        );

        // Determine if merged
        boolean isMerged = prData.get("mergedAt") != null;

        return new GitHubPullRequestDTO(
            null, // id
            parseLong(prData.get("fullDatabaseId")), // databaseId
            getString(prData, "id"), // nodeId
            parseIntOrDefault(prData.get("number"), 0), // number
            getString(prData, "title"), // title
            getString(prData, "body"), // body
            convertState(getString(prData, "state")), // state
            getString(prData, "url"), // htmlUrl
            parseInstant(prData.get("createdAt")), // createdAt
            parseInstant(prData.get("updatedAt")), // updatedAt
            parseInstant(prData.get("closedAt")), // closedAt
            parseInstant(prData.get("mergedAt")), // mergedAt
            mergedBy, // mergedBy
            null, // mergeCommitSha - not fetching nested commit
            parseBoolean(prData.get("isDraft")), // isDraft
            isMerged, // isMerged
            null, // mergeable - not fetched
            parseBoolean(prData.get("locked")), // locked
            parseIntOrDefault(prData.get("additions"), 0), // additions
            parseIntOrDefault(prData.get("deletions"), 0), // deletions
            parseIntOrDefault(prData.get("changedFiles"), 0), // changedFiles
            0, // commits - not fetched
            0, // commentsCount - not fetched
            0, // reviewCommentsCount - not fetched
            author, // author
            assignees, // assignees
            requestedReviewers, // requestedReviewers
            labels, // labels
            milestone, // milestone
            createBranchRef(getString(prData, "headRefName"), getString(prData, "headRefOid")), // head
            createBranchRef(getString(prData, "baseRefName"), getString(prData, "baseRefOid")), // base
            null // repository - in context
        );
    }

    private GitHubBranchRefDTO createBranchRef(String refName, String sha) {
        if (refName == null) {
            return null;
        }
        return new GitHubBranchRefDTO(refName, sha, null);
    }
}
