package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GraphQlParsingUtils.*;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO.GitHubCommentDTO;
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
 * Service for synchronizing GitHub issue comments via GraphQL API.
 * <p>
 * This service fetches issue comments via GraphQL and uses
 * GitHubIssueCommentProcessor for persistence. Uses Map-based parsing
 * to handle GitHub's polymorphic Actor interface.
 */
@Service
public class GitHubIssueCommentGraphQlSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueCommentGraphQlSyncService.class);
    private static final String GET_ISSUE_COMMENTS_DOCUMENT = "GetIssueComments";

    private final RepositoryRepository repositoryRepository;
    private final IssueRepository issueRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubIssueCommentProcessor commentProcessor;

    public GitHubIssueCommentGraphQlSyncService(
        RepositoryRepository repositoryRepository,
        IssueRepository issueRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubIssueCommentProcessor commentProcessor
    ) {
        this.repositoryRepository = repositoryRepository;
        this.issueRepository = issueRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.commentProcessor = commentProcessor;
    }

    /**
     * Synchronizes all comments for all issues in a repository using GraphQL.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync comments for
     * @return number of comments synced
     */
    @Transactional
    public int syncCommentsForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            logger.warn("Repository {} not found, cannot sync comments", repositoryId);
            return 0;
        }

        List<Issue> issues = issueRepository.findAllByRepository_Id(repositoryId);
        if (issues.isEmpty()) {
            logger.info("No issues found for repository {}, skipping comment sync", repository.getNameWithOwner());
            return 0;
        }

        int totalSynced = 0;
        for (Issue issue : issues) {
            int synced = syncCommentsForIssue(workspaceId, issue);
            totalSynced += synced;
        }

        logger.info(
            "Synced {} comments for {} issues in repository {}",
            totalSynced,
            issues.size(),
            repository.getNameWithOwner()
        );
        return totalSynced;
    }

    /**
     * Synchronizes all comments for a single issue using GraphQL.
     *
     * @param workspaceId the workspace ID for authentication
     * @param issue       the issue to sync comments for
     * @return number of comments synced
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public int syncCommentsForIssue(Long workspaceId, Issue issue) {
        if (issue == null) {
            logger.warn("Issue is null, cannot sync comments");
            return 0;
        }

        Repository repository = issue.getRepository();
        if (repository == null) {
            logger.warn("Issue {} has no repository, cannot sync comments", issue.getId());
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
                    .documentName(GET_ISSUE_COMMENTS_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("number", issue.getNumber())
                    .variable("first", DEFAULT_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(DEFAULT_TIMEOUT);

                if (response == null || !response.isValid()) {
                    logger.warn(
                        "Invalid GraphQL response for issue comments: {}",
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                Map<String, Object> commentsData = response.field("repository.issue.comments").toEntity(Map.class);
                if (commentsData == null) {
                    break;
                }

                List<Map<String, Object>> nodes = (List<Map<String, Object>>) commentsData.get("nodes");
                if (nodes == null || nodes.isEmpty()) {
                    break;
                }

                for (Map<String, Object> commentData : nodes) {
                    GitHubCommentDTO dto = convertMapToDTO(commentData);
                    IssueComment comment = commentProcessor.process(dto, issue.getId(), context);
                    if (comment != null) {
                        totalSynced++;
                    }
                }

                Map<String, Object> pageInfo = (Map<String, Object>) commentsData.get("pageInfo");
                hasNextPage = pageInfo != null && parseBoolean(pageInfo.get("hasNextPage"));
                cursor = pageInfo != null ? getString(pageInfo, "endCursor") : null;
            }

            logger.debug(
                "Synced {} comments for issue #{} in repository {}",
                totalSynced,
                issue.getNumber(),
                repository.getNameWithOwner()
            );
            return totalSynced;
        } catch (Exception e) {
            logger.error(
                "Error syncing comments for issue #{} in repository {}: {}",
                issue.getNumber(),
                repository.getNameWithOwner(),
                e.getMessage(),
                e
            );
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private GitHubCommentDTO convertMapToDTO(Map<String, Object> commentData) {
        // Parse author
        GitHubUserDTO author = parseUser((Map<String, Object>) commentData.get("author"));

        // Parse author association
        String authorAssociation = getString(commentData, "authorAssociation");

        return new GitHubCommentDTO(
            parseLong(commentData.get("databaseId")), // id
            getString(commentData, "id"), // nodeId
            getString(commentData, "url"), // htmlUrl
            getString(commentData, "body"), // body
            author, // user
            authorAssociation, // authorAssociation
            parseInstant(commentData.get("createdAt")), // createdAt
            parseInstant(commentData.get("updatedAt")) // updatedAt
        );
    }
}
