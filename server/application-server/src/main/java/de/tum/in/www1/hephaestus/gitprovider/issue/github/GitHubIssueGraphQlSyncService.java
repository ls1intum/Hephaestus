package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GraphQlParsingUtils.*;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueTypeDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
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
 * Service for synchronizing GitHub issues via GraphQL API.
 * <p>
 * This service fetches issues via GraphQL and uses GitHubIssueProcessor for persistence.
 * Uses Map-based parsing to handle GitHub's large database IDs (exceeding 32-bit int).
 */
@Service
public class GitHubIssueGraphQlSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueGraphQlSyncService.class);
    private static final String GET_ISSUES_DOCUMENT = "GetRepositoryIssues";

    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubIssueProcessor issueProcessor;

    public GitHubIssueGraphQlSyncService(
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubIssueProcessor issueProcessor
    ) {
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.issueProcessor = issueProcessor;
    }

    /**
     * Synchronizes all issues for a repository using GraphQL.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync issues for
     * @return number of issues synced
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public int syncIssuesForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            logger.warn("Repository {} not found, cannot sync issues", repositoryId);
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
                    .documentName(GET_ISSUES_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", DEFAULT_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(DEFAULT_TIMEOUT);

                if (response == null || !response.isValid()) {
                    logger.warn(
                        "Invalid GraphQL response for issues: {}",
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                Map<String, Object> issuesData = response.field("repository.issues").toEntity(Map.class);
                if (issuesData == null) {
                    break;
                }

                List<Map<String, Object>> nodes = (List<Map<String, Object>>) issuesData.get("nodes");
                if (nodes == null || nodes.isEmpty()) {
                    break;
                }

                for (Map<String, Object> issueData : nodes) {
                    GitHubIssueDTO dto = convertMapToDTO(issueData);
                    Issue issue = issueProcessor.process(dto, context);
                    if (issue != null) {
                        totalSynced++;
                    }
                }

                Map<String, Object> pageInfo = (Map<String, Object>) issuesData.get("pageInfo");
                hasNextPage = pageInfo != null && parseBoolean(pageInfo.get("hasNextPage"));
                cursor = pageInfo != null ? getString(pageInfo, "endCursor") : null;
            }

            logger.info("Synced {} issues for repository {}", totalSynced, repository.getNameWithOwner());
            return totalSynced;
        } catch (Exception e) {
            logger.error(
                "Error syncing issues for repository {}: {}",
                repository.getNameWithOwner(),
                e.getMessage(),
                e
            );
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private GitHubIssueDTO convertMapToDTO(Map<String, Object> issueData) {
        // Parse author
        GitHubUserDTO author = parseUser((Map<String, Object>) issueData.get("author"));

        // Parse labels
        List<GitHubLabelDTO> labels = parseLabelList((Map<String, Object>) issueData.get("labels"));

        // Parse milestone
        GitHubMilestoneDTO milestone = parseMilestone((Map<String, Object>) issueData.get("milestone"));

        // Parse issue type
        GitHubIssueTypeDTO issueType = parseIssueType((Map<String, Object>) issueData.get("issueType"));

        // Parse assignees
        List<GitHubUserDTO> assignees = parseUserList((Map<String, Object>) issueData.get("assignees"));

        return new GitHubIssueDTO(
            null, // id
            parseLong(issueData.get("fullDatabaseId")), // databaseId
            getString(issueData, "id"), // nodeId
            parseIntOrDefault(issueData.get("number"), 0), // number
            getString(issueData, "title"), // title
            getString(issueData, "body"), // body
            convertState(getString(issueData, "state")), // state
            getString(issueData, "stateReason"), // stateReason - pass through as-is
            getString(issueData, "url"), // htmlUrl
            0, // commentsCount - not fetched
            parseInstant(issueData.get("createdAt")), // createdAt
            parseInstant(issueData.get("updatedAt")), // updatedAt
            parseInstant(issueData.get("closedAt")), // closedAt
            author, // author
            assignees, // assignees
            labels, // labels
            milestone, // milestone
            issueType, // issueType
            null // repository - in context
        );
    }

    private GitHubIssueTypeDTO parseIssueType(Map<String, Object> issueTypeData) {
        if (issueTypeData == null) {
            return null;
        }
        return new GitHubIssueTypeDTO(
            null,
            getString(issueTypeData, "id"),
            getString(issueTypeData, "name"),
            getString(issueTypeData, "description"),
            getString(issueTypeData, "color"),
            parseBoolean(issueTypeData.get("isEnabled"))
        );
    }
}
