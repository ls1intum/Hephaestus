package de.tum.in.www1.hephaestus.gitprovider.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.RepositorySelection;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.client.HttpGraphQlClient;

/**
 * Abstract base class for live GitHub API integration tests using GraphQL and REST.
 * <p>
 * This class provides pure GraphQL mutations and REST API calls for operations
 * not supported by GraphQL.
 */
public abstract class AbstractGitHubLiveSyncIntegrationTest extends BaseGitHubLiveIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(AbstractGitHubLiveSyncIntegrationTest.class);

    @Autowired
    protected GitHubAppTokenService gitHubAppTokenService;

    @Autowired
    protected WorkspaceRepository workspaceRepository;

    @Autowired
    protected RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    protected HttpGraphQlClient gitHubGraphQlClient;

    protected final List<String> repositoriesToDelete = new ArrayList<>();
    protected final List<TeamToDelete> teamsToDelete = new ArrayList<>();

    protected GitHubTestFixtureService fixtureService;
    protected Workspace workspace;

    @BeforeAll
    void setUpFixtureService() {
        assumeGitHubCredentialsConfigured();
        String token = gitHubAppTokenService.getInstallationTokenDetails(githubInstallationId()).token();
        fixtureService = new GitHubTestFixtureService(
            gitHubGraphQlClient.mutate().header("Authorization", "Bearer " + token).build(),
            token
        );
    }

    @BeforeEach
    void initializeWorkspace() {
        databaseTestUtils.cleanDatabase();
        repositoriesToDelete.clear();
        teamsToDelete.clear();
        workspace = createWorkspace();
    }

    @AfterEach
    void tearDownGitHubArtifacts() {
        for (String fullName : repositoriesToDelete) {
            fixtureService.deleteRepository(fullName);
        }
        for (TeamToDelete team : teamsToDelete) {
            fixtureService.deleteTeam(team.orgLogin(), team.teamSlug());
        }
        workspace = null;
    }

    protected Workspace createWorkspace() {
        var ws = new Workspace();
        ws.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        ws.setInstallationId(githubInstallationId());
        ws.setWorkspaceSlug(generateWorkspaceSlug());
        ws.setDisplayName(githubOrganization());
        ws.setAccountLogin(githubOrganization());
        ws.setAccountType(AccountType.ORG);
        ws.setGithubRepositorySelection(RepositorySelection.ALL);
        return workspaceRepository.save(ws);
    }

    private String generateWorkspaceSlug() {
        String lowerCase = githubOrganization().toLowerCase();
        String normalized = lowerCase.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            normalized = "workspace-" + Long.toString(System.currentTimeMillis(), 36);
        }
        if (normalized.length() < 3) {
            normalized = normalized + "-ws";
        }
        return normalized;
    }

    protected GitHubTestFixtureService.CreatedRepository createEphemeralRepository(String suffix)
        throws InterruptedException {
        String repositoryName = nextEphemeralSlug(suffix);
        GitHubTestFixtureService.CreatedRepository repository = fixtureService.createRepository(
            githubOrganization(),
            repositoryName,
            true
        );
        repositoriesToDelete.add(repository.fullName());
        return repository;
    }

    protected RepositoryToMonitor registerRepositoryToMonitor(String fullName) {
        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setNameWithOwner(fullName);
        monitor.setWorkspace(workspace);
        RepositoryToMonitor saved = repositoryToMonitorRepository.save(monitor);
        workspace.getRepositoriesToMonitor().add(saved);
        workspaceRepository.save(workspace);
        return saved;
    }

    protected RepositoryToMonitor registerRepositoryToMonitor(GitHubTestFixtureService.CreatedRepository repository) {
        return registerRepositoryToMonitor(repository.fullName());
    }

    protected List<String> seedOrganizationMembers() {
        return fixtureService.getOrganizationMemberLogins(githubOrganization());
    }

    protected GitHubTestFixtureService.CreatedLabel createRepositoryLabel(
        String repositoryNodeId,
        String prefix,
        String color,
        String description
    ) {
        String uniqueSuffix = Long.toString(Instant.now().toEpochMilli(), 36);
        String labelName = (prefix + "-" + uniqueSuffix).toLowerCase();
        return fixtureService.createLabel(repositoryNodeId, labelName, color, description);
    }

    protected GitHubTestFixtureService.CreatedMilestone createRepositoryMilestone(
        String fullName,
        String prefix,
        String description
    ) {
        String uniqueSuffix = Long.toString(Instant.now().toEpochMilli(), 36);
        String milestoneTitle = prefix + " " + uniqueSuffix;
        return fixtureService.createMilestone(fullName, milestoneTitle, description);
    }

    protected CreatedTeam createEphemeralTeam(
        GitHubTestFixtureService.CreatedRepository repository,
        String maintainerLogin,
        String permission
    ) {
        String teamName = "it-team-" + nextEphemeralSlug("team");
        GitHubTestFixtureService.CreatedTeam team = fixtureService.createTeam(githubOrganization(), teamName);
        teamsToDelete.add(new TeamToDelete(githubOrganization(), team.slug()));

        // Add repository to team
        fixtureService.addRepositoryToTeam(githubOrganization(), team.slug(), repository.fullName(), permission);

        return new CreatedTeam(team.databaseId(), team.name(), maintainerLogin);
    }

    protected CreatedIssue createIssueWithComment(GitHubTestFixtureService.CreatedRepository repository)
        throws InterruptedException {
        String issueTitle = "IT issue " + nextEphemeralSlug("issue");
        String issueBody = "Live integration issue created at " + Instant.now();

        // Get repository info for node ID
        GitHubTestFixtureService.RepositoryInfo repoInfo = fixtureService.getRepositoryInfo(repository.fullName());

        GitHubTestFixtureService.CreatedIssue issue = fixtureService.createIssue(
            repoInfo.nodeId(),
            issueTitle,
            issueBody
        );

        String commentBody = "Issue comment seed " + Instant.now();
        GitHubTestFixtureService.CreatedIssueComment comment = fixtureService.addIssueComment(
            issue.nodeId(),
            commentBody
        );

        return new CreatedIssue(
            issue.databaseId(),
            issue.number(),
            issueTitle,
            issueBody,
            comment.databaseId(),
            commentBody
        );
    }

    protected PullRequestArtifacts createPullRequestWithReview(GitHubTestFixtureService.CreatedRepository repository)
        throws Exception {
        String branchSuffix = nextEphemeralSlug("branch");
        String branchName = "feature-" + branchSuffix;

        // Get repository info
        GitHubTestFixtureService.RepositoryInfo repoInfo = fixtureService.getRepositoryInfo(repository.fullName());
        String defaultBranch = repoInfo.defaultBranch();
        String headCommitSha = repoInfo.headCommitSha();

        // Create feature branch
        fixtureService.createBranch(repoInfo.nodeId(), branchName, headCommitSha);

        // Create a file on the feature branch
        String filePath = "integration/test-" + branchSuffix + ".txt";
        String fileContent = "Integration content generated at " + Instant.now();
        String commitSha = fixtureService.createCommitOnBranch(
            repository.fullName(),
            branchName,
            "Add " + filePath,
            filePath,
            fileContent
        );

        // Create pull request
        String prTitle = "IT pull request " + nextEphemeralSlug("pr");
        GitHubTestFixtureService.CreatedPullRequest pullRequest = fixtureService.createPullRequest(
            repoInfo.nodeId(),
            prTitle,
            "Integration test PR",
            branchName,
            defaultBranch
        );

        // Add review with comment
        String reviewBody = "Initial review message " + Instant.now();
        String reviewCommentBody = "Review inline note " + Instant.now();

        GitHubTestFixtureService.CreatedReview review = fixtureService.addPullRequestReview(
            pullRequest.nodeId(),
            reviewBody,
            "COMMENT",
            commitSha,
            List.of(new GitHubTestFixtureService.ReviewComment(filePath, 1, reviewCommentBody))
        );

        return new PullRequestArtifacts(
            pullRequest.databaseId(),
            pullRequest.number(),
            prTitle,
            review.databaseId(),
            reviewBody,
            0L, // Review comment ID - we don't get this from the mutation response
            reviewCommentBody,
            filePath,
            1,
            commitSha
        );
    }

    protected void awaitCondition(String description, CheckedCondition condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(45));
        while (Instant.now().isBefore(deadline)) {
            try {
                if (condition.evaluate()) {
                    return;
                }
            } catch (Exception ex) {
                logger.debug("Condition '{}' not yet satisfied: {}", description, ex.getMessage());
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("Timed out waiting for " + description);
    }

    protected void addRepositoryCollaborator(String fullName, String username) {
        fixtureService.addCollaborator(fullName, username, "push");
    }

    protected void removeRepositoryCollaborator(String fullName, String username) {
        fixtureService.removeCollaborator(fullName, username);
    }

    // ========== RECORD TYPES ==========

    protected record CreatedIssue(
        long issueId,
        int issueNumber,
        String issueTitle,
        String issueBody,
        long commentId,
        String commentBody
    ) {}

    protected record CreatedTeam(long id, String name, String maintainerLogin) {}

    protected record TeamToDelete(String orgLogin, String teamSlug) {}

    protected record PullRequestArtifacts(
        long pullRequestId,
        int pullRequestNumber,
        String pullRequestTitle,
        long reviewId,
        String reviewBody,
        long reviewCommentId,
        String reviewCommentBody,
        String reviewCommentPath,
        int reviewCommentLine,
        String commitSha
    ) {}

    @FunctionalInterface
    protected interface CheckedCondition {
        boolean evaluate() throws Exception;
    }
}
