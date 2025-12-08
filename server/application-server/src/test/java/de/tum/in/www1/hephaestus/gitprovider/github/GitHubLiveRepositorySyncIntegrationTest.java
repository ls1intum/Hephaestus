package de.tum.in.www1.hephaestus.gitprovider.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaborator;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaboratorRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.GitHubDataSyncService;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermissionRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHIssueState;
import org.springframework.beans.factory.annotation.Autowired;

class GitHubLiveRepositorySyncIntegrationTest extends AbstractGitHubLiveSyncIntegrationTest {

    @Autowired
    private GitHubDataSyncService dataSyncService;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private IssueCommentRepository issueCommentRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private PullRequestReviewRepository pullRequestReviewRepository;

    @Autowired
    private PullRequestReviewCommentRepository pullRequestReviewCommentRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private RepositoryCollaboratorRepository repositoryCollaboratorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepositoryPermissionRepository teamRepositoryPermissionRepository;

    private CreatedTeam createdTeam;
    private String integrationLabelName;
    private String integrationMilestoneTitle;
    private long collaboratorUserId;

    @AfterEach
    void resetLocalState() {
        createdTeam = null;
        integrationLabelName = null;
        integrationMilestoneTitle = null;
        collaboratorUserId = 0L;
    }

    @Test
    void syncsRepositoryIssuesPullRequestsAndReviewsEndToEnd() throws Exception {
        var ghRepository = createEphemeralRepository("sync");
        var label = createRepositoryLabel(ghRepository, "it-label", "0077ff", "Integration label coverage");
        integrationLabelName = label.getName();
        var milestone = createRepositoryMilestone(ghRepository, "IT milestone", "Integration milestone coverage");
        integrationMilestoneTitle = milestone.getTitle();

        var seededMembers = seedOrganizationMembers();
        Assumptions.assumeFalse(
            seededMembers.isEmpty(),
            "Organization must have at least one member to validate sync."
        );

        var collaborator = seededMembers.getFirst();
        collaboratorUserId = collaborator.getId();
        addRepositoryCollaborator(ghRepository, collaborator);
        awaitCondition("collaborator added remotely", () -> {
            try {
                return ghRepository
                    .listCollaborators()
                    .withPageSize(100)
                    .toList()
                    .stream()
                    .anyMatch(remote -> remote.getId() == collaborator.getId());
            } catch (IOException listingError) {
                return false;
            }
        });
        createdTeam = createEphemeralTeam(ghRepository, fetchOrganization(), collaborator);

        var createdIssue = createIssueWithComment(ghRepository);
        var pullRequestArtifacts = createPullRequestWithReview(ghRepository);

        awaitCondition("issues/PRs visible via GitHub API", () ->
            ghRepository
                .queryIssues()
                .state(GHIssueState.ALL)
                .since(Instant.now().minus(Duration.ofHours(2)))
                .list()
                .iterator()
                .hasNext()
        );

        var repositoryToMonitor = registerRepositoryToMonitor(ghRepository);
        dataSyncService.syncRepositoryToMonitor(repositoryToMonitor);
        dataSyncService.syncUsers(workspace);
        dataSyncService.syncTeams(workspace);

        var refreshedMonitor = repositoryToMonitorRepository.findById(repositoryToMonitor.getId()).orElseThrow();
        assertThat(refreshedMonitor.getRepositorySyncedAt()).as("repository sync timestamp").isNotNull();
        assertThat(refreshedMonitor.getLabelsSyncedAt()).as("label sync timestamp").isNotNull();
        assertThat(refreshedMonitor.getMilestonesSyncedAt()).as("milestone sync timestamp").isNotNull();
        assertThat(refreshedMonitor.getIssuesAndPullRequestsSyncedAt()).as("issue sync timestamp").isNotNull();

        var storedRepository = repositoryRepository.findById(ghRepository.getId()).orElseThrow();
        assertThat(storedRepository.getNameWithOwner()).isEqualTo(ghRepository.getFullName());

        var storedIssue = awaitAndFetch("issue persisted", () -> issueRepository.findById(createdIssue.issueId()));
        assertThat(storedIssue.getNumber()).isEqualTo(createdIssue.issueNumber());
        assertThat(storedIssue.getTitle()).isEqualTo(createdIssue.issueTitle());
        assertThat(storedIssue.getBody()).isEqualTo(createdIssue.issueBody());
        var storedIssueComment = awaitAndFetch("issue comment persisted", () ->
            issueCommentRepository.findById(createdIssue.commentId())
        );
        assertThat(storedIssueComment.getBody()).isEqualTo(createdIssue.commentBody());
        assertThat(storedIssueComment.getIssue().getId()).isEqualTo(createdIssue.issueId());

        var storedPullRequest = awaitAndFetch("pull request persisted", () ->
            pullRequestRepository.findById(pullRequestArtifacts.pullRequestId())
        );
        assertThat(storedPullRequest.getNumber()).isEqualTo(pullRequestArtifacts.pullRequestNumber());
        assertThat(storedPullRequest.getTitle()).isEqualTo(pullRequestArtifacts.pullRequestTitle());

        var storedReview = awaitAndFetch("pull request review persisted", () ->
            pullRequestReviewRepository.findById(pullRequestArtifacts.reviewId())
        );
        assertThat(storedReview.getState()).isEqualTo(PullRequestReview.State.COMMENTED);
        assertThat(storedReview.getBody()).isEqualTo(pullRequestArtifacts.reviewBody());

        var storedReviewComment = awaitAndFetch("pull request review comment persisted", () ->
            pullRequestReviewCommentRepository.findById(pullRequestArtifacts.reviewCommentId())
        );
        assertThat(storedReviewComment.getBody()).isEqualTo(pullRequestArtifacts.reviewCommentBody());
        assertThat(storedReviewComment.getReview()).isNotNull();
        assertThat(storedReviewComment.getPath()).isEqualTo(pullRequestArtifacts.reviewCommentPath());
        assertThat(storedReviewComment.getLine()).isEqualTo(pullRequestArtifacts.reviewCommentLine());
        assertThat(storedReviewComment.getCommitId()).isEqualTo(pullRequestArtifacts.commitSha());

        var storedLabel = awaitAndFetch("label persisted", () ->
            labelRepository.findByRepositoryIdAndName(ghRepository.getId(), integrationLabelName)
        );
        assertThat(storedLabel.getColor()).isEqualTo("0077ff");
        assertThat(storedLabel.getDescription()).isEqualTo("Integration label coverage");

        var storedMilestone = awaitAndFetch("milestone persisted", () ->
            milestoneRepository
                .findAll()
                .stream()
                .filter(candidate -> candidate.getRepository().getId().equals(ghRepository.getId()))
                .findFirst()
        );
        assertThat(storedMilestone.getTitle()).isEqualTo(integrationMilestoneTitle);
        assertThat(storedMilestone.getState()).isEqualTo(Milestone.State.OPEN);

        var storedCollaborator = awaitAndFetch("collaborator persisted", () ->
            repositoryCollaboratorRepository.findByRepositoryIdAndUserId(ghRepository.getId(), collaboratorUserId)
        );
        assertThat(storedCollaborator.getPermission()).isEqualTo(RepositoryCollaborator.Permission.ADMIN);
        var collaboratorEntries = repositoryCollaboratorRepository.findByRepository_Id(ghRepository.getId());
        var collaboratorUserIdsBeforeSync = collaboratorEntries
            .stream()
            .map(entry -> entry.getId().getUserId())
            .collect(Collectors.toSet());
        assertThat(collaboratorUserIdsBeforeSync).contains(collaboratorUserId);
        var collaboratorUser = userRepository.findById(collaboratorUserId).orElseThrow();
        assertThat(collaboratorUser.getLogin().toLowerCase()).isIn(
            seededMembers.stream().map(member -> member.getLogin().toLowerCase()).toList()
        );

        workspace = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(workspace.getUsersSyncedAt()).isNotNull();
        var seededLoginsLowercase = seededMembers
            .stream()
            .map(member -> member.getLogin().toLowerCase())
            .collect(Collectors.toSet());
        var syncedLogins = userRepository
            .findAllByLoginLowerIn(seededLoginsLowercase)
            .stream()
            .map(user -> user.getLogin().toLowerCase())
            .toList();
        assertThat(syncedLogins).containsAll(seededLoginsLowercase);
        assertThat(collaboratorUserId).isNotZero();
        assertThat(syncedLogins).contains(collaboratorUser.getLogin().toLowerCase());

        Set<Long> collaboratorIdsBeforeSecondSync = collaboratorUserIdsBeforeSync;
        dataSyncService.syncRepositoryToMonitor(repositoryToMonitor);
        Set<Long> collaboratorIdsAfterSecondSync = repositoryCollaboratorRepository
            .findByRepository_Id(ghRepository.getId())
            .stream()
            .map(entry -> entry.getId().getUserId())
            .collect(Collectors.toSet());
        assertThat(collaboratorIdsAfterSecondSync)
            .as("collaborator ids after idempotent run")
            .isEqualTo(collaboratorIdsBeforeSecondSync);

        var teamMembers = userRepository
            .findAllByTeamId(createdTeam.id())
            .stream()
            .map(user -> user.getLogin().toLowerCase())
            .toList();
        assertThat(teamMembers).contains(createdTeam.maintainerLogin().toLowerCase());
        assertThat(
            awaitAndFetch("team permission persisted", () ->
                teamRepositoryPermissionRepository.findByTeam_IdAndRepository_Id(createdTeam.id(), ghRepository.getId())
            )
        ).satisfies(permission -> {
            assertThat(permission.getPermission())
                .as("team repository permission level")
                .isIn(
                    TeamRepositoryPermission.PermissionLevel.MAINTAIN,
                    TeamRepositoryPermission.PermissionLevel.WRITE
                );
            assertThat(permission.isHiddenFromContributions()).isFalse();
        });
    }

    private <T> T awaitAndFetch(String description, Supplier<Optional<T>> supplier) throws Exception {
        awaitCondition(description, () -> supplier.get().isPresent());
        return supplier.get().orElseThrow();
    }
}
