package de.tum.in.www1.hephaestus.gitprovider.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Test fixture service that creates GitHub resources via GraphQL mutations
 * and REST API (for resources not supported by GraphQL mutations).
 * <p>
 * All resources are created using authenticated API calls.
 * <p>
 * This is NOT a Spring bean - instantiate it manually in tests with a token.
 */
public class GitHubTestFixtureService {

    private static final Logger log = LoggerFactory.getLogger(GitHubTestFixtureService.class);
    private static final String GITHUB_API_URL = "https://api.github.com";

    private final HttpGraphQlClient graphQlClient;
    private final WebClient restClient;

    /**
     * Creates the fixture service with an authenticated GraphQL client and token.
     *
     * @param graphQlClient an already-authenticated GraphQL client
     * @param token the GitHub token for REST API calls
     */
    public GitHubTestFixtureService(HttpGraphQlClient graphQlClient, String token) {
        this.graphQlClient = graphQlClient;
        this.restClient = WebClient.builder()
            .baseUrl(GITHUB_API_URL)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
    }

    // ========== REPOSITORY OPERATIONS ==========

    /**
     * Creates an ephemeral private repository in the given organization.
     */
    public CreatedRepository createRepository(String orgLogin, String repositoryName, boolean autoInit)
        throws InterruptedException {
        // First, get organization node ID via GraphQL query
        String orgNodeId = getOrganizationNodeId(orgLogin);

        String mutation =
            """
            mutation CreateRepository($input: CreateRepositoryInput!) {
                createRepository(input: $input) {
                    repository {
                        id
                        databaseId
                        name
                        nameWithOwner
                        url
                        defaultBranchRef {
                            name
                        }
                    }
                }
            }
            """;

        Map<String, Object> input = Map.of(
            "name",
            repositoryName,
            "ownerId",
            orgNodeId,
            "visibility",
            "PRIVATE",
            "description",
            "Temporary repository for live integration testing"
        );

        ClientGraphQlResponse response = graphQlClient
            .document(mutation)
            .variable("input", input)
            .execute()
            .block(Duration.ofSeconds(30));

        if (response == null || !response.isValid()) {
            throw new RuntimeException(
                "Failed to create repository: " + (response != null ? response.getErrors() : "null response")
            );
        }

        Map<String, Object> repo = response.field("createRepository.repository").toEntity(Map.class);
        String nodeId = (String) repo.get("id");
        Long databaseId = ((Number) repo.get("databaseId")).longValue();
        String nameWithOwner = (String) repo.get("nameWithOwner");
        String url = (String) repo.get("url");

        // If autoInit, we need to create initial commit via REST
        // (GraphQL createRepository doesn't support autoInit directly)
        if (autoInit) {
            createInitialCommit(nameWithOwner);
            // Wait for default branch to be available
            awaitDefaultBranch(nameWithOwner);
        }

        return new CreatedRepository(nodeId, databaseId, repositoryName, nameWithOwner, url);
    }

    /**
     * Deletes a repository by its full name (owner/repo).
     */
    public void deleteRepository(String fullName) {
        try {
            String[] parts = fullName.split("/");
            restClient
                .delete()
                .uri("/repos/{owner}/{repo}", parts[0], parts[1])
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(30));
            log.debug("Deleted repository: {}", fullName);
        } catch (Exception e) {
            log.warn("Failed to delete repository {}: {}", fullName, e.getMessage());
        }
    }

    // ========== LABEL OPERATIONS ==========

    /**
     * Creates a label in a repository via GraphQL mutation.
     */
    public CreatedLabel createLabel(String repositoryNodeId, String name, String color, String description) {
        String mutation =
            """
            mutation CreateLabel($input: CreateLabelInput!) {
                createLabel(input: $input) {
                    label {
                        id
                        name
                        color
                        description
                    }
                }
            }
            """;

        Map<String, Object> input = Map.of(
            "repositoryId",
            repositoryNodeId,
            "name",
            name,
            "color",
            color,
            "description",
            description != null ? description : ""
        );

        ClientGraphQlResponse response = graphQlClient
            .document(mutation)
            .variable("input", input)
            .execute()
            .block(Duration.ofSeconds(30));

        if (response == null || !response.isValid()) {
            throw new RuntimeException(
                "Failed to create label: " + (response != null ? response.getErrors() : "null response")
            );
        }

        Map<String, Object> label = response.field("createLabel.label").toEntity(Map.class);
        return new CreatedLabel((String) label.get("id"), (String) label.get("name"), (String) label.get("color"));
    }

    /**
     * Updates a label via GraphQL mutation.
     */
    public void updateLabel(String labelNodeId, String newColor, String newDescription) {
        String mutation =
            """
            mutation UpdateLabel($input: UpdateLabelInput!) {
                updateLabel(input: $input) {
                    label {
                        id
                        color
                    }
                }
            }
            """;

        Map<String, Object> input = new java.util.HashMap<>();
        input.put("id", labelNodeId);
        if (newColor != null) input.put("color", newColor);
        if (newDescription != null) input.put("description", newDescription);

        graphQlClient.document(mutation).variable("input", input).execute().block(Duration.ofSeconds(30));
    }

    /**
     * Deletes a label via GraphQL mutation.
     */
    public void deleteLabel(String labelNodeId) {
        String mutation =
            """
            mutation DeleteLabel($input: DeleteLabelInput!) {
                deleteLabel(input: $input) {
                    clientMutationId
                }
            }
            """;

        graphQlClient
            .document(mutation)
            .variable("input", Map.of("id", labelNodeId))
            .execute()
            .block(Duration.ofSeconds(30));
    }

    // ========== MILESTONE OPERATIONS (REST API - no GraphQL mutation) ==========

    /**
     * Creates a milestone via REST API (no GraphQL mutation exists).
     */
    public CreatedMilestone createMilestone(String fullName, String title, String description) {
        Map<String, Object> body = Map.of(
            "title",
            title,
            "description",
            description != null ? description : "",
            "state",
            "open"
        );

        String[] parts = fullName.split("/");
        MilestoneResponse response = restClient
            .post()
            .uri("/repos/{owner}/{repo}/milestones", parts[0], parts[1])
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(MilestoneResponse.class)
            .block(Duration.ofSeconds(30));

        if (response == null) {
            throw new RuntimeException("Failed to create milestone");
        }

        return new CreatedMilestone(response.nodeId(), response.id(), response.number(), response.title());
    }

    /**
     * Closes a milestone via REST API.
     */
    public void closeMilestone(String fullName, int milestoneNumber) {
        String[] parts = fullName.split("/");
        restClient
            .patch()
            .uri("/repos/{owner}/{repo}/milestones/{number}", parts[0], parts[1], milestoneNumber)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("state", "closed"))
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(30));
    }

    /**
     * Deletes a milestone via REST API.
     */
    public void deleteMilestone(String fullName, int milestoneNumber) {
        String[] parts = fullName.split("/");
        restClient
            .delete()
            .uri("/repos/{owner}/{repo}/milestones/{number}", parts[0], parts[1], milestoneNumber)
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(30));
    }

    /**
     * Lists milestones to check if one exists.
     */
    public List<MilestoneResponse> listMilestones(String fullName, String state) {
        String[] parts = fullName.split("/");
        return restClient
            .get()
            .uri(uri ->
                uri.path("/repos/{owner}/{repo}/milestones").queryParam("state", state).build(parts[0], parts[1])
            )
            .retrieve()
            .bodyToFlux(MilestoneResponse.class)
            .collectList()
            .block(Duration.ofSeconds(30));
    }

    // ========== ISSUE OPERATIONS ==========

    /**
     * Creates an issue via GraphQL mutation.
     */
    public CreatedIssue createIssue(String repositoryNodeId, String title, String body) {
        String mutation =
            """
            mutation CreateIssue($input: CreateIssueInput!) {
                createIssue(input: $input) {
                    issue {
                        id
                        databaseId
                        number
                        title
                        url
                    }
                }
            }
            """;

        Map<String, Object> input = new java.util.HashMap<>();
        input.put("repositoryId", repositoryNodeId);
        input.put("title", title);
        if (body != null) input.put("body", body);

        ClientGraphQlResponse response = graphQlClient
            .document(mutation)
            .variable("input", input)
            .execute()
            .block(Duration.ofSeconds(30));

        if (response == null || !response.isValid()) {
            throw new RuntimeException(
                "Failed to create issue: " + (response != null ? response.getErrors() : "null response")
            );
        }

        Map<String, Object> issue = response.field("createIssue.issue").toEntity(Map.class);
        return new CreatedIssue(
            (String) issue.get("id"),
            ((Number) issue.get("databaseId")).longValue(),
            ((Number) issue.get("number")).intValue(),
            (String) issue.get("title")
        );
    }

    /**
     * Adds a comment to an issue via GraphQL.
     */
    public CreatedIssueComment addIssueComment(String issueNodeId, String body) {
        String mutation =
            """
            mutation AddComment($input: AddCommentInput!) {
                addComment(input: $input) {
                    commentEdge {
                        node {
                            id
                            databaseId
                            body
                        }
                    }
                }
            }
            """;

        Map<String, Object> input = Map.of("subjectId", issueNodeId, "body", body);

        ClientGraphQlResponse response = graphQlClient
            .document(mutation)
            .variable("input", input)
            .execute()
            .block(Duration.ofSeconds(30));

        if (response == null || !response.isValid()) {
            throw new RuntimeException(
                "Failed to add comment: " + (response != null ? response.getErrors() : "null response")
            );
        }

        Map<String, Object> comment = response.field("addComment.commentEdge.node").toEntity(Map.class);
        return new CreatedIssueComment(
            (String) comment.get("id"),
            ((Number) comment.get("databaseId")).longValue(),
            (String) comment.get("body")
        );
    }

    // ========== PULL REQUEST OPERATIONS ==========

    /**
     * Creates a pull request via GraphQL mutation.
     * Requires a feature branch with commits.
     */
    public CreatedPullRequest createPullRequest(
        String repositoryNodeId,
        String title,
        String body,
        String headBranch,
        String baseBranch
    ) {
        String mutation =
            """
            mutation CreatePullRequest($input: CreatePullRequestInput!) {
                createPullRequest(input: $input) {
                    pullRequest {
                        id
                        databaseId
                        number
                        title
                        url
                        headRefOid
                    }
                }
            }
            """;

        Map<String, Object> input = Map.of(
            "repositoryId",
            repositoryNodeId,
            "title",
            title,
            "body",
            body != null ? body : "",
            "headRefName",
            headBranch,
            "baseRefName",
            baseBranch
        );

        ClientGraphQlResponse response = graphQlClient
            .document(mutation)
            .variable("input", input)
            .execute()
            .block(Duration.ofSeconds(30));

        if (response == null || !response.isValid()) {
            throw new RuntimeException(
                "Failed to create pull request: " + (response != null ? response.getErrors() : "null response")
            );
        }

        Map<String, Object> pr = response.field("createPullRequest.pullRequest").toEntity(Map.class);
        return new CreatedPullRequest(
            (String) pr.get("id"),
            ((Number) pr.get("databaseId")).longValue(),
            ((Number) pr.get("number")).intValue(),
            (String) pr.get("title"),
            (String) pr.get("headRefOid")
        );
    }

    /**
     * Creates a pull request review via GraphQL.
     */
    public CreatedReview addPullRequestReview(
        String pullRequestNodeId,
        String body,
        String event,
        String commitOid,
        List<ReviewComment> comments
    ) {
        String mutation =
            """
            mutation AddPullRequestReview($input: AddPullRequestReviewInput!) {
                addPullRequestReview(input: $input) {
                    pullRequestReview {
                        id
                        databaseId
                        body
                        state
                    }
                }
            }
            """;

        Map<String, Object> input = new java.util.HashMap<>();
        input.put("pullRequestId", pullRequestNodeId);
        input.put("body", body);
        input.put("event", event); // APPROVE, REQUEST_CHANGES, COMMENT
        if (commitOid != null) input.put("commitOID", commitOid);
        if (comments != null && !comments.isEmpty()) {
            input.put(
                "comments",
                comments
                    .stream()
                    .map(c -> Map.of("path", c.path(), "position", c.position(), "body", c.body()))
                    .toList()
            );
        }

        ClientGraphQlResponse response = graphQlClient
            .document(mutation)
            .variable("input", input)
            .execute()
            .block(Duration.ofSeconds(30));

        if (response == null || !response.isValid()) {
            throw new RuntimeException(
                "Failed to add review: " + (response != null ? response.getErrors() : "null response")
            );
        }

        Map<String, Object> review = response.field("addPullRequestReview.pullRequestReview").toEntity(Map.class);
        return new CreatedReview(
            (String) review.get("id"),
            ((Number) review.get("databaseId")).longValue(),
            (String) review.get("body"),
            (String) review.get("state")
        );
    }

    // ========== BRANCH OPERATIONS ==========

    /**
     * Creates a branch (ref) via GraphQL mutation.
     */
    public void createBranch(String repositoryNodeId, String branchName, String commitSha) {
        String mutation =
            """
            mutation CreateRef($input: CreateRefInput!) {
                createRef(input: $input) {
                    ref {
                        id
                        name
                    }
                }
            }
            """;

        Map<String, Object> input = Map.of(
            "repositoryId",
            repositoryNodeId,
            "name",
            "refs/heads/" + branchName,
            "oid",
            commitSha
        );

        ClientGraphQlResponse response = graphQlClient
            .document(mutation)
            .variable("input", input)
            .execute()
            .block(Duration.ofSeconds(30));

        if (response == null || !response.isValid()) {
            throw new RuntimeException(
                "Failed to create branch: " + (response != null ? response.getErrors() : "null response")
            );
        }
    }

    /**
     * Creates a commit on a branch via GraphQL mutation.
     */
    public String createCommitOnBranch(
        String repositoryFullName,
        String branchName,
        String commitMessage,
        String filePath,
        String fileContent
    ) {
        String[] parts = repositoryFullName.split("/");
        String owner = parts[0];
        String repo = parts[1];

        // First get branch info including head commit
        String query =
            """
            query GetBranchInfo($owner: String!, $name: String!, $qualifiedName: String!) {
                repository(owner: $owner, name: $name) {
                    id
                    ref(qualifiedName: $qualifiedName) {
                        target {
                            oid
                        }
                    }
                }
            }
            """;

        ClientGraphQlResponse infoResponse = graphQlClient
            .document(query)
            .variable("owner", owner)
            .variable("name", repo)
            .variable("qualifiedName", "refs/heads/" + branchName)
            .execute()
            .block(Duration.ofSeconds(30));

        if (infoResponse == null || !infoResponse.isValid()) {
            throw new RuntimeException(
                "Failed to get branch info: " + (infoResponse != null ? infoResponse.getErrors() : "null response")
            );
        }

        String repositoryId = infoResponse.field("repository.id").toEntity(String.class);
        String expectedHeadOid = infoResponse.field("repository.ref.target.oid").toEntity(String.class);

        String mutation =
            """
            mutation CreateCommitOnBranch($input: CreateCommitOnBranchInput!) {
                createCommitOnBranch(input: $input) {
                    commit {
                        oid
                        url
                    }
                }
            }
            """;

        String base64Content = Base64.getEncoder().encodeToString(fileContent.getBytes());

        Map<String, Object> input = Map.of(
            "branch",
            Map.of("repositoryNameWithOwner", repositoryFullName, "branchName", branchName),
            "message",
            Map.of("headline", commitMessage),
            "expectedHeadOid",
            expectedHeadOid,
            "fileChanges",
            Map.of("additions", List.of(Map.of("path", filePath, "contents", base64Content)))
        );

        ClientGraphQlResponse response = graphQlClient
            .document(mutation)
            .variable("input", input)
            .execute()
            .block(Duration.ofSeconds(30));

        if (response == null || !response.isValid()) {
            throw new RuntimeException(
                "Failed to create commit: " + (response != null ? response.getErrors() : "null response")
            );
        }

        return response.field("createCommitOnBranch.commit.oid").toEntity(String.class);
    }

    // ========== TEAM OPERATIONS (REST API) ==========

    /**
     * Creates a team via REST API.
     */
    public CreatedTeam createTeam(String orgLogin, String teamName) {
        Map<String, Object> body = Map.of("name", teamName, "privacy", "closed");

        TeamResponse response = restClient
            .post()
            .uri("/orgs/{org}/teams", orgLogin)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(TeamResponse.class)
            .block(Duration.ofSeconds(30));

        if (response == null) {
            throw new RuntimeException("Failed to create team");
        }

        return new CreatedTeam(response.nodeId(), response.id(), response.name(), response.slug());
    }

    /**
     * Deletes a team via REST API.
     */
    public void deleteTeam(String orgLogin, String teamSlug) {
        try {
            restClient
                .delete()
                .uri("/orgs/{org}/teams/{team_slug}", orgLogin, teamSlug)
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            log.warn("Failed to delete team {}/{}: {}", orgLogin, teamSlug, e.getMessage());
        }
    }

    /**
     * Adds a repository to a team.
     */
    public void addRepositoryToTeam(String orgLogin, String teamSlug, String fullName, String permission) {
        restClient
            .put()
            .uri(
                "/orgs/{org}/teams/{team_slug}/repos/{owner}/{repo}",
                orgLogin,
                teamSlug,
                fullName.split("/")[0],
                fullName.split("/")[1]
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("permission", permission))
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(30));
    }

    // ========== COLLABORATOR OPERATIONS (REST API) ==========

    /**
     * Adds a collaborator to a repository.
     */
    public void addCollaborator(String fullName, String username, String permission) {
        String[] parts = fullName.split("/");
        restClient
            .put()
            .uri("/repos/{owner}/{repo}/collaborators/{username}", parts[0], parts[1], username)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("permission", permission))
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(30));
    }

    /**
     * Removes a collaborator from a repository.
     */
    public void removeCollaborator(String fullName, String username) {
        String[] parts = fullName.split("/");
        restClient
            .delete()
            .uri("/repos/{owner}/{repo}/collaborators/{username}", parts[0], parts[1], username)
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(30));
    }

    // ========== ORGANIZATION QUERIES ==========

    /**
     * Gets the node ID of an organization.
     */
    public String getOrganizationNodeId(String login) {
        String query =
            """
            query GetOrganization($login: String!) {
                organization(login: $login) {
                    id
                }
            }
            """;

        ClientGraphQlResponse response = graphQlClient
            .document(query)
            .variable("login", login)
            .execute()
            .block(Duration.ofSeconds(30));

        if (response == null || !response.isValid()) {
            throw new RuntimeException(
                "Failed to get organization: " + (response != null ? response.getErrors() : "null response")
            );
        }

        return response.field("organization.id").toEntity(String.class);
    }

    /**
     * Gets organization members.
     */
    public List<String> getOrganizationMemberLogins(String login) {
        String query =
            """
            query GetOrgMembers($login: String!) {
                organization(login: $login) {
                    membersWithRole(first: 100) {
                        nodes {
                            login
                        }
                    }
                }
            }
            """;

        ClientGraphQlResponse response = graphQlClient
            .document(query)
            .variable("login", login)
            .execute()
            .block(Duration.ofSeconds(30));

        if (response == null || !response.isValid()) {
            throw new RuntimeException(
                "Failed to get organization members: " + (response != null ? response.getErrors() : "null response")
            );
        }

        List<Map<String, Object>> nodes = response.field("organization.membersWithRole.nodes").toEntity(List.class);
        return nodes.stream().map(n -> (String) n.get("login")).toList();
    }

    // ========== REPOSITORY QUERIES ==========

    /**
     * Gets repository info including node ID and default branch.
     */
    public RepositoryInfo getRepositoryInfo(String fullName) {
        String[] parts = fullName.split("/");
        String query =
            """
            query GetRepository($owner: String!, $name: String!) {
                repository(owner: $owner, name: $name) {
                    id
                    databaseId
                    name
                    nameWithOwner
                    url
                    defaultBranchRef {
                        name
                        target {
                            oid
                        }
                    }
                }
            }
            """;

        ClientGraphQlResponse response = graphQlClient
            .document(query)
            .variable("owner", parts[0])
            .variable("name", parts[1])
            .execute()
            .block(Duration.ofSeconds(30));

        if (response == null || !response.isValid()) {
            throw new RuntimeException(
                "Failed to get repository: " + (response != null ? response.getErrors() : "null response")
            );
        }

        Map<String, Object> repo = response.field("repository").toEntity(Map.class);
        Map<String, Object> defaultBranch = (Map<String, Object>) repo.get("defaultBranchRef");
        Map<String, Object> target = defaultBranch != null ? (Map<String, Object>) defaultBranch.get("target") : null;

        return new RepositoryInfo(
            (String) repo.get("id"),
            ((Number) repo.get("databaseId")).longValue(),
            (String) repo.get("nameWithOwner"),
            defaultBranch != null ? (String) defaultBranch.get("name") : null,
            target != null ? (String) target.get("oid") : null
        );
    }

    /**
     * Lists labels in a repository.
     */
    public List<LabelInfo> listLabels(String fullName) {
        String[] parts = fullName.split("/");
        String query =
            """
            query ListLabels($owner: String!, $name: String!) {
                repository(owner: $owner, name: $name) {
                    labels(first: 100) {
                        nodes {
                            id
                            name
                            color
                        }
                    }
                }
            }
            """;

        ClientGraphQlResponse response = graphQlClient
            .document(query)
            .variable("owner", parts[0])
            .variable("name", parts[1])
            .execute()
            .block(Duration.ofSeconds(30));

        if (response == null || !response.isValid()) {
            return List.of();
        }

        List<Map<String, Object>> nodes = response.field("repository.labels.nodes").toEntity(List.class);
        if (nodes == null) return List.of();

        return nodes
            .stream()
            .map(n -> new LabelInfo((String) n.get("id"), (String) n.get("name"), (String) n.get("color")))
            .toList();
    }

    // ========== HELPER METHODS ==========

    private void createInitialCommit(String fullName) {
        String[] parts = fullName.split("/");
        String owner = parts[0];
        String repo = parts[1];
        String readmeContent = "# " + repo + "\n\nTemporary repository for integration testing.";

        // First try to get default branch - if it exists, repo is already initialized
        try {
            RepositoryInfo info = getRepositoryInfo(fullName);
            if (info.defaultBranch() != null) {
                return; // Already initialized
            }
        } catch (Exception e) {
            // Continue to create initial commit
        }

        // Create initial commit via REST (file creation)
        // Use separate path variables to avoid URL encoding issues with slash
        Map<String, Object> body = Map.of(
            "message",
            "Initial commit",
            "content",
            Base64.getEncoder().encodeToString(readmeContent.getBytes())
        );

        restClient
            .put()
            .uri("/repos/{owner}/{repo}/contents/README.md", owner, repo)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(30));
    }

    private void awaitDefaultBranch(String fullName) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            try {
                RepositoryInfo info = getRepositoryInfo(fullName);
                if (info.defaultBranch() != null && !info.defaultBranch().isBlank()) {
                    return;
                }
            } catch (Exception e) {
                // Retry
            }
            TimeUnit.SECONDS.sleep(1);
        }
        throw new RuntimeException("Timed out waiting for default branch on " + fullName);
    }

    // ========== RECORD TYPES ==========

    public record CreatedRepository(String nodeId, Long databaseId, String name, String fullName, String url) {}

    public record CreatedLabel(String nodeId, String name, String color) {}

    public record CreatedMilestone(String nodeId, Long databaseId, int number, String title) {}

    public record CreatedIssue(String nodeId, Long databaseId, int number, String title) {}

    public record CreatedIssueComment(String nodeId, Long databaseId, String body) {}

    public record CreatedPullRequest(String nodeId, Long databaseId, int number, String title, String headCommitSha) {}

    public record CreatedReview(String nodeId, Long databaseId, String body, String state) {}

    public record CreatedTeam(String nodeId, Long databaseId, String name, String slug) {}

    public record ReviewComment(String path, int position, String body) {}

    public record RepositoryInfo(
        String nodeId,
        Long databaseId,
        String fullName,
        String defaultBranch,
        String headCommitSha
    ) {}

    public record LabelInfo(String nodeId, String name, String color) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MilestoneResponse(
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("id") Long id,
        @JsonProperty("number") int number,
        @JsonProperty("title") String title,
        @JsonProperty("state") String state
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamResponse(
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("slug") String slug
    ) {}
}
