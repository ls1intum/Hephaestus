package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.IssueCommentConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PageInfo;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO.GitHubCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub issue comments via GraphQL API.
 * <p>
 * Uses typed GraphQL models for type-safe deserialization and delegates
 * persistence to GitHubIssueCommentProcessor.
 */
@Service
public class GitHubIssueCommentSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueCommentSyncService.class);
    private static final String QUERY_DOCUMENT = "GetIssueComments";
    private static final int PAGE_SIZE = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final RepositoryRepository repositoryRepository;
    private final IssueRepository issueRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubIssueCommentProcessor commentProcessor;

    public GitHubIssueCommentSyncService(
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
     * Synchronizes all comments for all issues in a repository.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync comments for
     * @return number of comments synced
     */
    @Transactional
    public int syncForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.warn("Repository {} not found, skipping comment sync", repositoryId);
            return 0;
        }

        List<Issue> issues = issueRepository.findAllByRepository_Id(repositoryId);
        if (issues.isEmpty()) {
            log.info("No issues found for {}, skipping comment sync", repository.getNameWithOwner());
            return 0;
        }

        int totalSynced = 0;
        for (Issue issue : issues) {
            totalSynced += syncForIssue(workspaceId, issue);
        }

        log.info("Synced {} comments for {} issues in {}", totalSynced, issues.size(), repository.getNameWithOwner());
        return totalSynced;
    }

    /**
     * Synchronizes all comments for a single issue.
     */
    @Transactional
    public int syncForIssue(Long workspaceId, Issue issue) {
        if (issue == null || issue.getRepository() == null) {
            log.warn("Issue or repository is null, skipping comment sync");
            return 0;
        }

        Repository repository = issue.getRepository();
        String[] ownerAndName = parseOwnerAndName(repository.getNameWithOwner());
        if (ownerAndName == null) {
            log.warn("Invalid repository name format: {}", repository.getNameWithOwner());
            return 0;
        }

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);
        ProcessingContext context = ProcessingContext.forSync(workspaceId, repository);

        int totalSynced = 0;
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            try {
                ClientGraphQlResponse response = client
                    .documentName(QUERY_DOCUMENT)
                    .variable("owner", ownerAndName[0])
                    .variable("name", ownerAndName[1])
                    .variable("number", issue.getNumber())
                    .variable("first", PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(TIMEOUT);

                if (response == null || !response.isValid()) {
                    log.warn("Invalid GraphQL response: {}", response != null ? response.getErrors() : "null");
                    break;
                }

                IssueCommentConnection connection = response
                    .field("repository.issue.comments")
                    .toEntity(IssueCommentConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                for (var graphQlComment : connection.getNodes()) {
                    GitHubCommentDTO dto = GitHubCommentDTO.fromIssueComment(graphQlComment);
                    if (dto != null) {
                        IssueComment entity = commentProcessor.process(dto, issue.getId(), context);
                        if (entity != null) {
                            totalSynced++;
                        }
                    }
                }

                PageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            } catch (Exception e) {
                log.error(
                    "Error syncing comments for issue #{} in {}: {}",
                    issue.getNumber(),
                    repository.getNameWithOwner(),
                    e.getMessage(),
                    e
                );
                break;
            }
        }

        log.debug(
            "Synced {} comments for issue #{} in {}",
            totalSynced,
            issue.getNumber(),
            repository.getNameWithOwner()
        );
        return totalSynced;
    }

    private String[] parseOwnerAndName(String nameWithOwner) {
        if (nameWithOwner == null || !nameWithOwner.contains("/")) {
            return null;
        }
        String[] parts = nameWithOwner.split("/", 2);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return null;
        }
        return parts;
    }
}
