package de.tum.in.www1.hephaestus.gitprovider.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHOrganization.Permission;
import org.kohsuke.github.GHOrganization.RepositoryRole;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHPullRequestReviewEvent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositoryDiscussionsSupport;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

abstract class AbstractGitHubSyncIntegrationTest extends BaseGitHubIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(AbstractGitHubSyncIntegrationTest.class);

    @Autowired
    protected GitHubAppTokenService gitHubAppTokenService;

    @Autowired
    protected WorkspaceRepository workspaceRepository;

    @Autowired
    protected RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    protected GitHubUserSyncService gitHubUserSyncService;

    @Value("${github.meta.auth-token:}")
    private String personalAccessToken;

    protected final List<String> repositoriesToDelete = new ArrayList<>();
    protected final List<Long> teamsToDelete = new ArrayList<>();

    protected GitHub installationClient;
    protected GitHub patClient;
    protected Workspace workspace;

    @BeforeAll
    void setUpGitHubClients() throws IOException {
        assumeGitHubCredentialsConfigured();
        installationClient = gitHubAppTokenService.clientForInstallation(githubInstallationId());
        if (personalAccessToken != null && !personalAccessToken.isBlank()) {
            patClient = new GitHubBuilder().withOAuthToken(personalAccessToken).build();
        }
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
            deleteRepositoryQuietly(fullName);
        }
        for (Long teamId : teamsToDelete) {
            deleteTeamQuietly(teamId);
        }
        workspace = null;
    }

    protected Workspace createWorkspace() {
        var ws = new Workspace();
        ws.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        ws.setInstallationId(githubInstallationId());
        ws.setAccountLogin(githubOrganization());
        ws.setGithubRepositorySelection(org.kohsuke.github.GHRepositorySelection.ALL);
        return workspaceRepository.save(ws);
    }

    protected GHRepository createEphemeralRepository(String suffix) throws IOException, InterruptedException {
        String repositoryName = nextEphemeralSlug(suffix);
        GHOrganization organization = fetchOrganization();
        GHRepository repository;
        try {
            repository = organization
                .createRepository(repositoryName)
                .autoInit(true)
                .private_(true)
                .description("Temporary repository for live integration testing")
                .create();
        } catch (IOException creationError) {
            if (patClient == null) {
                throw creationError;
            }
            logger.warn("Repository creation via installation token failed, retrying with PAT", creationError);
            var patOrganization = patClient.getOrganization(organization.getLogin());
            repository = patOrganization
                .createRepository(repositoryName)
                .autoInit(true)
                .private_(true)
                .description("Temporary repository for live integration testing")
                .create();
        }
        String fullName = repository.getFullName();
        repositoriesToDelete.add(fullName);
        awaitCondition("repository default branch", () -> {
            var refetched = installationClient.getRepository(fullName);
            return refetched.getDefaultBranch() != null && !refetched.getDefaultBranch().isBlank();
        });
        return installationClient.getRepository(fullName);
    }

    protected RepositoryToMonitor registerRepositoryToMonitor(GHRepository repository) {
        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setNameWithOwner(repository.getFullName());
        monitor.setWorkspace(workspace);
        RepositoryToMonitor saved = repositoryToMonitorRepository.save(monitor);
        workspace.getRepositoriesToMonitor().add(saved);
        workspaceRepository.save(workspace);
        return saved;
    }

    protected List<GHUser> seedOrganizationMembers() throws IOException {
        GHOrganization org = fetchOrganization();
        PagedIterable<GHUser> memberIterable = org.listMembers().withPageSize(100);
        List<GHUser> members = memberIterable.toList();
        members.forEach(gitHubUserSyncService::processUser);
        return members;
    }

    protected GHOrganization fetchOrganization() throws IOException {
        return installationClient.getOrganization(githubOrganization());
    }

    protected GHLabel createRepositoryLabel(GHRepository repository, String prefix, String color, String description)
        throws IOException {
        String uniqueSuffix = Long.toString(Instant.now().toEpochMilli(), 36);
        String labelName = (prefix + "-" + uniqueSuffix).toLowerCase();
        return repository.createLabel(labelName, color, description);
    }

    protected GHMilestone createRepositoryMilestone(GHRepository repository, String prefix, String description)
        throws IOException {
        String uniqueSuffix = Long.toString(Instant.now().toEpochMilli(), 36);
        String milestoneTitle = prefix + " " + uniqueSuffix;
        return repository.createMilestone(milestoneTitle, description);
    }

    protected CreatedTeam createEphemeralTeam(GHRepository repository, GHOrganization organization, GHUser maintainer)
        throws IOException {
        return createEphemeralTeam(repository, organization, maintainer, RepositoryRole.from(Permission.MAINTAIN));
    }

    protected CreatedTeam createEphemeralTeam(
        GHRepository repository,
        GHOrganization organization,
        GHUser maintainer,
        RepositoryRole repositoryRole
    ) throws IOException {
        String teamName = "it-team-" + nextEphemeralSlug("team");
        GHTeam team;
        try {
            team = organization
                .createTeam(teamName)
                .privacy(GHTeam.Privacy.CLOSED)
                .maintainers(maintainer.getLogin())
                .create();
        } catch (IOException creationError) {
            if (patClient == null) {
                throw creationError;
            }
            logger.warn("Team creation via installation token failed, retrying with PAT", creationError);
            var patOrganization = patClient.getOrganization(organization.getLogin());
            team = patOrganization
                .createTeam(teamName)
                .privacy(GHTeam.Privacy.CLOSED)
                .maintainers(maintainer.getLogin())
                .create();
        }
        teamsToDelete.add(team.getId());
        if (!tryAddRepositoryToTeam(team, repository, repositoryRole)) {
            if (patClient == null) {
                throw new IOException(
                    "Failed to add repository to team via installation token and no PAT fallback available."
                );
            }
            var patTeam = patClient.getOrganization(organization.getLogin()).getTeam(team.getId());
            var patRepository = patClient.getRepository(repository.getFullName());
            if (!tryAddRepositoryToTeam(patTeam, patRepository, repositoryRole)) {
                throw new IOException("Failed to add repository to team using PAT fallback.");
            }
        }
        return new CreatedTeam(team.getId(), teamName, maintainer.getLogin());
    }

    protected CreatedIssue createIssueWithComment(GHRepository repository) throws IOException, InterruptedException {
        String issueTitle = "IT issue " + nextEphemeralSlug("issue");
        String issueBody = "Live integration issue created at " + Instant.now();
        var issue = repository.createIssue(issueTitle).body(issueBody).create();
        String commentBody = "Issue comment seed " + Instant.now();
        var comment = issue.comment(commentBody);
        awaitCondition(
            "issue comment availability",
            () -> repository.getIssue(issue.getNumber()).getCommentsCount() >= 1
        );
        return new CreatedIssue(issue.getId(), issue.getNumber(), issueTitle, issueBody, comment.getId(), commentBody);
    }

    protected PullRequestArtifacts createPullRequestWithReview(GHRepository repository) throws Exception {
        String branchSuffix = nextEphemeralSlug("branch");
        String branchName = "feature-" + branchSuffix;
        String defaultBranch = repository.getDefaultBranch();
        var defaultRef = repository.getRef("heads/" + defaultBranch);
        repository.createRef("refs/heads/" + branchName, defaultRef.getObject().getSha());

        String filePath = "integration/test-" + branchSuffix + ".txt";
        String fileContent = "Integration content generated at " + Instant.now();
        repository
            .createContent()
            .path(filePath)
            .message("Add " + filePath)
            .content(fileContent)
            .branch(branchName)
            .commit();

        String prTitle = "IT pull request " + nextEphemeralSlug("pr");
        var pullRequest = repository.createPullRequest(prTitle, branchName, defaultBranch, "Integration test PR");
        pullRequest.refresh();

        awaitCondition("pull request head commit", () -> pullRequest.getHead() != null);
        String headCommitSha = pullRequest.getHead().getSha();

        String reviewDraftBody = "Initial review message " + Instant.now();
        String reviewCommentBody = "Review inline note " + Instant.now();

        GHPullRequestReview commentReview = pullRequest
            .createReview()
            .body(reviewDraftBody)
            .comment(reviewCommentBody, filePath, 1)
            .commitId(headCommitSha)
            .event(GHPullRequestReviewEvent.COMMENT)
            .create();
        awaitCondition("review comment present", () -> !pullRequest.listReviewComments().toList().isEmpty());
        List<GHPullRequestReviewComment> reviewComments = pullRequest.listReviewComments().toList();
        GHPullRequestReviewComment reviewComment = reviewComments.get(0);
        awaitCondition("pull request review present", () ->
            pullRequest.listReviews().toList().stream().anyMatch(r -> r.getId() == commentReview.getId())
        );

        return new PullRequestArtifacts(
            pullRequest.getId(),
            pullRequest.getNumber(),
            prTitle,
            commentReview.getId(),
            reviewDraftBody,
            reviewComment.getId(),
            reviewCommentBody,
            filePath,
            reviewComment.getLine(),
            headCommitSha
        );
    }

    protected CreatedCommit createDefaultBranchCommit(GHRepository repository)
        throws IOException, InterruptedException {
        String filePath = "integration/commit-" + nextEphemeralSlug("commit") + ".txt";
        String commitMessage = "Add default branch seed " + filePath;
        var response = repository
            .createContent()
            .path(filePath)
            .message(commitMessage)
            .content("integration-default-branch " + Instant.now())
            .branch(repository.getDefaultBranch())
            .commit();
        String commitSha = response.getCommit().getSHA1();
        awaitCondition("default branch commit availability", () -> {
            try {
                repository.getCommit(commitSha);
                return true;
            } catch (IOException ex) {
                return false;
            }
        });
        return new CreatedCommit(commitSha, filePath, commitMessage);
    }

    protected Optional<DiscussionArtifacts> createRepositoryDiscussionWithComment(GHRepository repository)
        throws IOException, InterruptedException {
        try {
            GHRepositoryDiscussionsSupport.enableDiscussions(repository);
        } catch (IOException enableError) {
            logger.warn(
                "Repository {} does not expose discussion APIs with the current credentials: {}",
                repository.getFullName(),
                enableError.getMessage()
            );
            return Optional.empty();
        }

        awaitCondition("repository discussions enabled", () -> {
            try {
                return !GHRepositoryDiscussionsSupport.listDiscussionCategories(repository).isEmpty();
            } catch (IOException fetchError) {
                return false;
            }
        });
        var categories = GHRepositoryDiscussionsSupport.listDiscussionCategories(repository);
        if (categories.isEmpty()) {
            logger.warn("Repository {} does not report discussion categories", repository.getFullName());
            return Optional.empty();
        }

        var category = categories.getFirst();
        String title = "IT discussion " + nextEphemeralSlug("discussion");
        String body = "Integration discussion body " + Instant.now();
        var discussion = GHRepositoryDiscussionsSupport.createDiscussion(repository, title, body, category.getId());

        String commentBody = "Discussion comment seed " + Instant.now();
        var comment = GHRepositoryDiscussionsSupport.createDiscussionComment(
            repository,
            discussion.getNumber(),
            commentBody
        );
        return Optional.of(
            new DiscussionArtifacts(
                discussion.getId(),
                discussion.getNumber(),
                title,
                body,
                comment.getId(),
                commentBody
            )
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

    private void deleteRepositoryQuietly(String fullName) {
        try {
            installationClient.getRepository(fullName).delete();
            return;
        } catch (IOException installationDeletionError) {
            logger.warn("Failed to delete repo {} via installation token", fullName, installationDeletionError);
        }

        if (patClient != null) {
            try {
                patClient.getRepository(fullName).delete();
            } catch (IOException patDeletionError) {
                logger.warn("Failed to delete repo {} via PAT", fullName, patDeletionError);
            }
        }
    }

    private void deleteTeamQuietly(Long teamId) {
        if (teamId == null) {
            return;
        }
        try {
            fetchOrganization().getTeam(teamId).delete();
            return;
        } catch (IOException deletionError) {
            logger.warn("Failed to delete team {} via installation token", teamId, deletionError);
        }

        if (patClient != null) {
            try {
                patClient.getOrganization(githubOrganization()).getTeam(teamId).delete();
            } catch (IOException patDeletionError) {
                logger.warn("Failed to delete team {} via PAT", teamId, patDeletionError);
            }
        }
    }

    protected void addRepositoryCollaborator(GHRepository repository, GHUser collaborator) throws IOException {
        if (tryAddCollaborator(repository, collaborator)) {
            return;
        }
        if (patClient == null) {
            throw new IOException("Failed to add collaborator via installation token and no PAT available.");
        }
        var patRepository = patClient.getRepository(repository.getFullName());
        var patCollaborator = patClient.getUser(collaborator.getLogin());
        if (!tryAddCollaborator(patRepository, patCollaborator)) {
            throw new IOException("Failed to add collaborator using PAT fallback.");
        }
    }

    protected void removeRepositoryCollaborator(GHRepository repository, GHUser collaborator) throws IOException {
        if (tryRemoveCollaborator(repository, collaborator)) {
            return;
        }
        if (patClient == null) {
            throw new IOException("Failed to remove collaborator via installation token and no PAT available.");
        }
        var patRepository = patClient.getRepository(repository.getFullName());
        var patCollaborator = patClient.getUser(collaborator.getLogin());
        if (!tryRemoveCollaborator(patRepository, patCollaborator)) {
            throw new IOException("Failed to remove collaborator using PAT fallback.");
        }
    }

    private boolean tryAddRepositoryToTeam(GHTeam team, GHRepository repository, RepositoryRole role) {
        try {
            team.add(repository, role);
            return true;
        } catch (IOException linkingError) {
            logger.warn(
                "Failed to link repository {} to team {}",
                repository.getFullName(),
                team.getId(),
                linkingError
            );
            return false;
        }
    }

    private boolean tryAddCollaborator(GHRepository repository, GHUser collaborator) {
        try {
            repository.addCollaborators(collaborator);
            return true;
        } catch (IOException additionError) {
            logger.warn(
                "Failed to add collaborator {} to repository {}",
                collaborator.getLogin(),
                repository.getFullName(),
                additionError
            );
            return false;
        }
    }

    private boolean tryRemoveCollaborator(GHRepository repository, GHUser collaborator) {
        try {
            repository.removeCollaborators(collaborator);
            return true;
        } catch (IOException removalError) {
            logger.warn(
                "Failed to remove collaborator {} from repository {}",
                collaborator.getLogin(),
                repository.getFullName(),
                removalError
            );
            return false;
        }
    }

    protected record CreatedIssue(
        long issueId,
        int issueNumber,
        String issueTitle,
        String issueBody,
        long commentId,
        String commentBody
    ) {}

    protected record CreatedTeam(long id, String name, String maintainerLogin) {}

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

    protected record CreatedCommit(String sha, String path, String message) {}

    protected record DiscussionArtifacts(
        long discussionId,
        int discussionNumber,
        String discussionTitle,
        String discussionBody,
        long commentId,
        String commentBody
    ) {}

    @FunctionalInterface
    protected interface CheckedCondition {
        boolean evaluate() throws Exception;
    }
}
