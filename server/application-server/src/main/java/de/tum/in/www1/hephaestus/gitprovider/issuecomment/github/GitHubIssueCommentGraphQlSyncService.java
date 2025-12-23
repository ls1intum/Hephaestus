package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Actor;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.CommentAuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.IssueCommentConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.User;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO.GitHubCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub issue comments via GraphQL API.
 * <p>
 * This service fetches issue comments via GraphQL and uses
 * GitHubIssueCommentProcessor for persistence. It supports syncing
 * comments for a single issue or all issues in a repository.
 */
@Service
public class GitHubIssueCommentGraphQlSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueCommentGraphQlSyncService.class);
    private static final int GRAPHQL_PAGE_SIZE = 50;
    private static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(60);
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

        String[] parts = repository.getNameWithOwner().split("/");
        if (parts.length != 2) {
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
                IssueCommentConnection response = client
                    .documentName(GET_ISSUE_COMMENTS_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("number", issue.getNumber())
                    .variable("first", GRAPHQL_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("repository.issue.comments")
                    .toEntity(IssueCommentConnection.class)
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || response.getNodes() == null) {
                    break;
                }

                for (var graphQlComment : response.getNodes()) {
                    GitHubCommentDTO dto = convertToDTO(graphQlComment);
                    IssueComment comment = commentProcessor.process(dto, issue.getId(), context);
                    if (comment != null) {
                        totalSynced++;
                    }
                }

                var pageInfo = response.getPageInfo();
                hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
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

    /**
     * Converts a GraphQL IssueComment to a GitHubCommentDTO.
     *
     * @param graphQlComment the GraphQL issue comment
     * @return the DTO for processing
     */
    private GitHubCommentDTO convertToDTO(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.IssueComment graphQlComment
    ) {
        // Convert author
        GitHubUserDTO author = null;
        Actor graphQlAuthor = graphQlComment.getAuthor();
        if (graphQlAuthor instanceof User graphQlUser) {
            author = new GitHubUserDTO(
                null, // id
                graphQlUser.getDatabaseId() != null ? graphQlUser.getDatabaseId().longValue() : null, // databaseId
                graphQlUser.getLogin(), // login
                graphQlUser.getAvatarUrl() != null ? graphQlUser.getAvatarUrl().toString() : null, // avatarUrl
                null, // htmlUrl
                graphQlUser.getName(), // name
                graphQlUser.getEmail() // email
            );
        }

        // Convert author association
        String authorAssociation = convertAuthorAssociation(graphQlComment.getAuthorAssociation());

        return new GitHubCommentDTO(
            graphQlComment.getDatabaseId() != null ? graphQlComment.getDatabaseId().longValue() : null, // id
            graphQlComment.getId(), // nodeId
            graphQlComment.getUrl() != null ? graphQlComment.getUrl().toString() : null, // htmlUrl
            graphQlComment.getBody(), // body
            author, // author
            authorAssociation, // authorAssociation
            graphQlComment.getCreatedAt() != null ? graphQlComment.getCreatedAt().toInstant() : null, // createdAt
            graphQlComment.getUpdatedAt() != null ? graphQlComment.getUpdatedAt().toInstant() : null // updatedAt
        );
    }

    /**
     * Converts a GraphQL CommentAuthorAssociation to its string representation.
     *
     * @param association the GraphQL author association
     * @return the string representation
     */
    private String convertAuthorAssociation(CommentAuthorAssociation association) {
        if (association == null) {
            return "NONE";
        }
        return association.name();
    }
}
