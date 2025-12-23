package de.tum.in.www1.hephaestus.gitprovider.issue.github;

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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
 * This service provides GraphQL-based issue synchronization.
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
    @SuppressWarnings("unchecked")
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
                ClientGraphQlResponse response = client
                    .documentName(GET_ISSUES_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", GRAPHQL_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(GRAPHQL_TIMEOUT);

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
                hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.get("hasNextPage"));
                cursor = pageInfo != null ? (String) pageInfo.get("endCursor") : null;
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
        // Convert author
        GitHubUserDTO author = null;
        Map<String, Object> authorData = (Map<String, Object>) issueData.get("author");
        if (authorData != null) {
            author = new GitHubUserDTO(
                null,
                authorData.get("databaseId") != null ? ((Number) authorData.get("databaseId")).longValue() : null,
                (String) authorData.get("login"),
                (String) authorData.get("avatarUrl"),
                null,
                (String) authorData.get("name"),
                (String) authorData.get("email")
            );
        }

        // Convert labels
        List<GitHubLabelDTO> labels = new ArrayList<>();
        Map<String, Object> labelsData = (Map<String, Object>) issueData.get("labels");
        if (labelsData != null) {
            List<Map<String, Object>> labelNodes = (List<Map<String, Object>>) labelsData.get("nodes");
            if (labelNodes != null) {
                for (Map<String, Object> labelData : labelNodes) {
                    labels.add(
                        new GitHubLabelDTO(
                            null,
                            (String) labelData.get("id"),
                            (String) labelData.get("name"),
                            (String) labelData.get("description"),
                            (String) labelData.get("color")
                        )
                    );
                }
            }
        }

        // Convert milestone
        GitHubMilestoneDTO milestone = null;
        Map<String, Object> milestoneData = (Map<String, Object>) issueData.get("milestone");
        if (milestoneData != null) {
            milestone = new GitHubMilestoneDTO(
                null,
                milestoneData.get("number") != null ? ((Number) milestoneData.get("number")).intValue() : 0,
                (String) milestoneData.get("title"),
                (String) milestoneData.get("description"),
                convertMilestoneState((String) milestoneData.get("state")),
                milestoneData.get("dueOn") != null ? Instant.parse((String) milestoneData.get("dueOn")) : null,
                (String) milestoneData.get("url")
            );
        }

        // Convert issue type
        GitHubIssueTypeDTO issueType = null;
        Map<String, Object> issueTypeData = (Map<String, Object>) issueData.get("issueType");
        if (issueTypeData != null) {
            issueType = new GitHubIssueTypeDTO(
                null,
                (String) issueTypeData.get("id"),
                (String) issueTypeData.get("name"),
                (String) issueTypeData.get("description"),
                (String) issueTypeData.get("color"),
                Boolean.TRUE.equals(issueTypeData.get("isEnabled"))
            );
        }

        // Convert assignees
        List<GitHubUserDTO> assignees = new ArrayList<>();
        Map<String, Object> assigneesData = (Map<String, Object>) issueData.get("assignees");
        if (assigneesData != null) {
            List<Map<String, Object>> assigneeNodes = (List<Map<String, Object>>) assigneesData.get("nodes");
            if (assigneeNodes != null) {
                for (Map<String, Object> assigneeData : assigneeNodes) {
                    assignees.add(
                        new GitHubUserDTO(
                            null,
                            assigneeData.get("databaseId") != null
                                ? ((Number) assigneeData.get("databaseId")).longValue()
                                : null,
                            (String) assigneeData.get("login"),
                            (String) assigneeData.get("avatarUrl"),
                            null,
                            (String) assigneeData.get("name"),
                            null
                        )
                    );
                }
            }
        }

        return new GitHubIssueDTO(
            null,
            parseLong(issueData.get("fullDatabaseId")),
            (String) issueData.get("id"),
            parseIntOrDefault(issueData.get("number"), 0),
            (String) issueData.get("title"),
            (String) issueData.get("body"),
            convertState((String) issueData.get("state")),
            convertStateReason((String) issueData.get("stateReason")),
            (String) issueData.get("url"),
            0,
            parseInstant(issueData.get("createdAt")),
            parseInstant(issueData.get("updatedAt")),
            parseInstant(issueData.get("closedAt")),
            author,
            assignees,
            labels,
            milestone,
            issueType,
            null
        );
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) return Long.parseLong((String) value);
        return null;
    }

    private int parseIntOrDefault(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) return Integer.parseInt((String) value);
        return defaultValue;
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        if (value instanceof String) return Instant.parse((String) value);
        return null;
    }

    private String convertState(String state) {
        if (state == null) {
            return "open";
        }
        return switch (state) {
            case "OPEN" -> "open";
            case "CLOSED" -> "closed";
            default -> state.toLowerCase();
        };
    }

    private String convertStateReason(String stateReason) {
        return stateReason; // Pass through as-is
    }

    private String convertMilestoneState(String state) {
        if (state == null) {
            return "open";
        }
        return switch (state) {
            case "OPEN" -> "open";
            case "CLOSED" -> "closed";
            default -> state.toLowerCase();
        };
    }
}
