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
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaboratorRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.GitHubDataSyncService;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermissionRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GitHubRepositorySyncIntegrationTest extends AbstractGitHubSyncIntegrationTest {

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

        var storedIssue = issueRepository.findById(createdIssue.issueId()).orElseThrow();
        assertThat(storedIssue.getNumber()).isEqualTo(createdIssue.issueNumber());
        assertThat(storedIssue.getTitle()).isEqualTo(createdIssue.issueTitle());
        assertThat(storedIssue.getBody()).isEqualTo(createdIssue.issueBody());
        assertThat(issueCommentRepository.findById(createdIssue.commentId())).isPresent();
        var storedIssueComment = issueCommentRepository.findById(createdIssue.commentId()).orElseThrow();
        assertThat(storedIssueComment.getBody()).isEqualTo(createdIssue.commentBody());
        assertThat(storedIssueComment.getIssue().getId()).isEqualTo(createdIssue.issueId());

        var storedPullRequest = pullRequestRepository.findById(pullRequestArtifacts.pullRequestId()).orElseThrow();
        assertThat(storedPullRequest.getNumber()).isEqualTo(pullRequestArtifacts.pullRequestNumber());
        assertThat(storedPullRequest.getTitle()).isEqualTo(pullRequestArtifacts.pullRequestTitle());

        var storedReview = pullRequestReviewRepository.findById(pullRequestArtifacts.reviewId()).orElseThrow();
        assertThat(storedReview.getState()).isEqualTo(PullRequestReview.State.COMMENTED);
        assertThat(storedReview.getBody()).isEqualTo(pullRequestArtifacts.reviewBody());

        var storedReviewComment = pullRequestReviewCommentRepository
            .findById(pullRequestArtifacts.reviewCommentId())
            .orElseThrow();
        assertThat(storedReviewComment.getBody()).isEqualTo(pullRequestArtifacts.reviewCommentBody());
        assertThat(storedReviewComment.getReview()).isNotNull();
        assertThat(storedReviewComment.getPath()).isEqualTo(pullRequestArtifacts.reviewCommentPath());
        assertThat(storedReviewComment.getLine()).isEqualTo(pullRequestArtifacts.reviewCommentLine());
        assertThat(storedReviewComment.getCommitId()).isEqualTo(pullRequestArtifacts.commitSha());

        var storedLabel = labelRepository
            .findByRepositoryIdAndName(ghRepository.getId(), integrationLabelName)
            .orElseThrow();
        assertThat(storedLabel.getColor()).isEqualTo("0077ff");
        assertThat(storedLabel.getDescription()).isEqualTo("Integration label coverage");

        var storedMilestone = milestoneRepository
            .findAll()
            .stream()
            .filter(candidate -> candidate.getRepository().getId().equals(ghRepository.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(storedMilestone.getTitle()).isEqualTo(integrationMilestoneTitle);
        assertThat(storedMilestone.getState()).isEqualTo(Milestone.State.OPEN);

        var storedCollaborator = repositoryCollaboratorRepository
            .findByRepositoryIdAndUserId(ghRepository.getId(), collaboratorUserId)
            .orElseThrow();
        assertThat(storedCollaborator.getPermission()).isEqualTo(
            de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaborator.Permission.ADMIN
        );
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
            teamRepositoryPermissionRepository.findByTeam_IdAndRepository_Id(createdTeam.id(), ghRepository.getId())
        )
            .isPresent()
            .get()
            .satisfies(permission -> {
                assertThat(permission.getPermission())
                    .as("team repository permission level")
                    .isIn(
                        de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission.PermissionLevel.MAINTAIN,
                        de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission.PermissionLevel.WRITE
                    );
                assertThat(permission.isHiddenFromContributions()).isFalse();
            });
    }
}
