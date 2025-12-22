package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.IssueConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.IssueState;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Label;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.User;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueTypeDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub issues via GraphQL API.
 * <p>
 * This service replaces the deprecated hub4j-based GitHubIssueSyncService.
 * It fetches issues via GraphQL and uses GitHubIssueProcessor for persistence.
 */
@Service
public class GitHubIssueGraphQlSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueGraphQlSyncService.class);
    private static final int GRAPHQL_PAGE_SIZE = 50;
    private static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(60);
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
    public int syncIssuesForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            logger.warn("Repository {} not found, cannot sync issues", repositoryId);
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
                IssueConnection response = client
                    .documentName(GET_ISSUES_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", GRAPHQL_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("repository.issues")
                    .toEntity(IssueConnection.class)
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || response.getNodes() == null) {
                    break;
                }

                for (var graphQlIssue : response.getNodes()) {
                    GitHubIssueDTO dto = convertToDTO(graphQlIssue);
                    Issue issue = issueProcessor.process(dto, context);
                    if (issue != null) {
                        totalSynced++;
                    }
                }

                var pageInfo = response.getPageInfo();
                hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
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

    private GitHubIssueDTO convertToDTO(de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Issue graphQlIssue) {
        // Convert author
        GitHubUserDTO author = null;
        var graphQlAuthor = graphQlIssue.getAuthor();
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

        // Convert labels
        List<GitHubLabelDTO> labels = new ArrayList<>();
        if (graphQlIssue.getLabels() != null && graphQlIssue.getLabels().getNodes() != null) {
            for (Label l : graphQlIssue.getLabels().getNodes()) {
                if (l != null) {
                    labels.add(
                        new GitHubLabelDTO(
                            null, // id - GraphQL doesn't return databaseId for labels
                            l.getId(), // nodeId
                            l.getName(),
                            l.getDescription(),
                            l.getColor()
                        )
                    );
                }
            }
        }

        // Convert milestone
        GitHubMilestoneDTO milestone = null;
        var graphQlMilestone = graphQlIssue.getMilestone();
        if (graphQlMilestone != null) {
            milestone = new GitHubMilestoneDTO(
                null, // id - GraphQL doesn't return databaseId for milestones
                graphQlMilestone.getNumber(),
                graphQlMilestone.getTitle(),
                graphQlMilestone.getDescription(),
                convertMilestoneState(graphQlMilestone.getState()),
                graphQlMilestone.getDueOn() != null ? graphQlMilestone.getDueOn().toInstant() : null,
                graphQlMilestone.getUrl() != null ? graphQlMilestone.getUrl().toString() : null
            );
        }

        // Convert issue type
        GitHubIssueTypeDTO issueType = null;
        var graphQlIssueType = graphQlIssue.getIssueType();
        if (graphQlIssueType != null) {
            issueType = new GitHubIssueTypeDTO(
                null, // id
                graphQlIssueType.getId(), // nodeId
                graphQlIssueType.getName(),
                graphQlIssueType.getDescription(),
                graphQlIssueType.getColor() != null ? graphQlIssueType.getColor().name() : null,
                Boolean.TRUE.equals(graphQlIssueType.getIsEnabled())
            );
        }

        // Convert assignees
        List<GitHubUserDTO> assignees = new ArrayList<>();
        if (graphQlIssue.getAssignees() != null && graphQlIssue.getAssignees().getNodes() != null) {
            for (User a : graphQlIssue.getAssignees().getNodes()) {
                if (a != null) {
                    assignees.add(
                        new GitHubUserDTO(
                            null, // id
                            a.getDatabaseId() != null ? a.getDatabaseId().longValue() : null, // databaseId
                            a.getLogin(),
                            a.getAvatarUrl() != null ? a.getAvatarUrl().toString() : null,
                            null, // htmlUrl
                            a.getName(),
                            null // email
                        )
                    );
                }
            }
        }

        return new GitHubIssueDTO(
            null, // id
            graphQlIssue.getDatabaseId() != null ? graphQlIssue.getDatabaseId().longValue() : null, // databaseId
            graphQlIssue.getId(), // nodeId
            graphQlIssue.getNumber(),
            graphQlIssue.getTitle(),
            graphQlIssue.getBody(),
            convertState(graphQlIssue.getState()),
            convertStateReason(graphQlIssue.getStateReason()),
            graphQlIssue.getUrl() != null ? graphQlIssue.getUrl().toString() : null,
            0, // commentsCount - not fetched
            graphQlIssue.getCreatedAt() != null ? graphQlIssue.getCreatedAt().toInstant() : null,
            graphQlIssue.getUpdatedAt() != null ? graphQlIssue.getUpdatedAt().toInstant() : null,
            graphQlIssue.getClosedAt() != null ? graphQlIssue.getClosedAt().toInstant() : null,
            author,
            assignees,
            labels,
            milestone,
            issueType,
            null // repository - already in context
        );
    }

    private String convertState(IssueState state) {
        if (state == null) {
            return "open";
        }
        return switch (state) {
            case OPEN -> "open";
            case CLOSED -> "closed";
        };
    }

    private String convertStateReason(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.IssueStateReason stateReason
    ) {
        if (stateReason == null) {
            return null;
        }
        return stateReason.name();
    }

    private String convertMilestoneState(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.MilestoneState state
    ) {
        if (state == null) {
            return "open";
        }
        return switch (state) {
            case OPEN -> "open";
            case CLOSED -> "closed";
        };
    }
}
