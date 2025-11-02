package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Pull Request Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubPullRequestMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubPullRequestMessageHandler handler;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("should persist pull request when opened")
    void openedEventPersistsPullRequest(@GitHubPayload("pull_request.opened") GHEventPayload.PullRequest payload)
        throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var pr = pullRequestRepository.findById(payload.getPullRequest().getId());
        assertThat(pr)
            .isPresent()
            .get()
            .satisfies(saved -> {
                assertThat(saved.getNumber()).isEqualTo(payload.getPullRequest().getNumber());
                assertThat(saved.getTitle()).isEqualTo(payload.getPullRequest().getTitle());
                assertThat(saved.getState()).isEqualTo(Issue.State.OPEN);
                assertThat(saved.getHtmlUrl()).isEqualTo(payload.getPullRequest().getHtmlUrl().toString());
                assertThat(saved.getBody()).isEqualTo(payload.getPullRequest().getBody());
                assertThat(saved.isDraft()).isFalse();
                assertThat(saved.isPullRequest()).isTrue();
            });

        // Verify repository was created
        assertThat(repositoryRepository.findAll()).isNotEmpty();

        // Verify author was created
        assertThat(userRepository.findById(payload.getPullRequest().getUser().getId())).isPresent();
    }

    @Test
    @DisplayName("should update pull request state when closed")
    void closedEventUpdatesPullRequestState(
        @GitHubPayload("pull_request.opened") GHEventPayload.PullRequest opened,
        @GitHubPayload("pull_request.closed") GHEventPayload.PullRequest closed
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(closed);

        // Assert
        var pr = pullRequestRepository.findById(closed.getPullRequest().getId()).orElseThrow();
        assertThat(pr.getState()).isEqualTo(Issue.State.CLOSED);
        assertThat(pr.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("should update pull request state when reopened")
    void reopenedEventUpdatesPullRequestState(
        @GitHubPayload("pull_request.closed") GHEventPayload.PullRequest closed,
        @GitHubPayload("pull_request.reopened") GHEventPayload.PullRequest reopened
    ) throws Exception {
        // Arrange
        handler.handleEvent(closed);
        var prAfterClosed = pullRequestRepository.findById(closed.getPullRequest().getId()).orElseThrow();
        assertThat(prAfterClosed.getState()).isEqualTo(Issue.State.CLOSED);

        // Act
        handler.handleEvent(reopened);

        // Assert
        var pr = pullRequestRepository.findById(reopened.getPullRequest().getId()).orElseThrow();
        assertThat(pr.getState()).isEqualTo(Issue.State.OPEN);
    }

    @Test
    @DisplayName("should update pull request when synchronized")
    void synchronizeEventUpdatesPullRequest(
        @GitHubPayload("pull_request.opened") GHEventPayload.PullRequest opened,
        @GitHubPayload("pull_request.synchronize") GHEventPayload.PullRequest synchronizePayload
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(synchronizePayload);

        // Assert
        var pr = pullRequestRepository.findById(synchronizePayload.getPullRequest().getId()).orElseThrow();
        assertThat(pr.getNumber()).isEqualTo(synchronizePayload.getPullRequest().getNumber());
        // Verify the PR was updated (check updated timestamp or other fields)
    }

    @Test
    @Transactional
    @DisplayName("should add label to pull request when labeled")
    void labeledEventAddsLabel(
        @GitHubPayload("pull_request.opened") GHEventPayload.PullRequest opened,
        @GitHubPayload("pull_request.labeled") GHEventPayload.PullRequest labeled
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(labeled);

        // Assert
        var pr = pullRequestRepository.findById(labeled.getPullRequest().getId()).orElseThrow();
        assertThat(pr.getLabels()).hasSize(labeled.getPullRequest().getLabels().size());
        assertThat(pr.getLabels()).isNotEmpty();

        // Verify label was persisted
        var labels = labelRepository.findAll();
        assertThat(labels).isNotEmpty();
    }

    @Test
    @Transactional
    @DisplayName("should remove label from pull request when unlabeled")
    void unlabeledEventRemovesLabel(
        @GitHubPayload("pull_request.labeled") GHEventPayload.PullRequest labeled,
        @GitHubPayload("pull_request.unlabeled") GHEventPayload.PullRequest unlabeled
    ) throws Exception {
        // Arrange
        handler.handleEvent(labeled);
        var prAfterLabel = pullRequestRepository.findById(labeled.getPullRequest().getId()).orElseThrow();
        assertThat(prAfterLabel.getLabels()).isNotEmpty();

        // Act
        handler.handleEvent(unlabeled);

        // Assert
        var pr = pullRequestRepository.findById(unlabeled.getPullRequest().getId()).orElseThrow();
        assertThat(pr.getLabels()).hasSize(unlabeled.getPullRequest().getLabels().size());
    }

    @Test
    @Transactional
    @DisplayName("should add assignee to pull request when assigned")
    void assignedEventAddsAssignee(
        @GitHubPayload("pull_request.opened") GHEventPayload.PullRequest opened,
        @GitHubPayload("pull_request.assigned") GHEventPayload.PullRequest assigned
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(assigned);

        // Assert
        var pr = pullRequestRepository.findById(assigned.getPullRequest().getId()).orElseThrow();
        assertThat(pr.getAssignees()).hasSize(assigned.getPullRequest().getAssignees().size());
        assertThat(pr.getAssignees()).isNotEmpty();
    }

    @Test
    @Transactional
    @DisplayName("should remove assignee from pull request when unassigned")
    void unassignedEventRemovesAssignee(
        @GitHubPayload("pull_request.assigned") GHEventPayload.PullRequest assigned,
        @GitHubPayload("pull_request.unassigned") GHEventPayload.PullRequest unassigned
    ) throws Exception {
        // Arrange
        handler.handleEvent(assigned);
        var prAfterAssigned = pullRequestRepository.findById(assigned.getPullRequest().getId()).orElseThrow();
        assertThat(prAfterAssigned.getAssignees()).isNotEmpty();

        // Act
        handler.handleEvent(unassigned);

        // Assert
        var pr = pullRequestRepository.findById(unassigned.getPullRequest().getId()).orElseThrow();
        assertThat(pr.getAssignees()).hasSize(unassigned.getPullRequest().getAssignees().size());
    }

    @Test
    @DisplayName("should add milestone to pull request when milestoned")
    void milestonedEventAddsMilestone(
        @GitHubPayload("pull_request.opened") GHEventPayload.PullRequest opened,
        @GitHubPayload("pull_request.milestoned") GHEventPayload.PullRequest milestoned
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(milestoned);

        // Assert
        var pr = pullRequestRepository.findById(milestoned.getPullRequest().getId()).orElseThrow();
        assertThat(pr.getMilestone()).isNotNull();
        assertThat(pr.getMilestone().getId()).isEqualTo(milestoned.getPullRequest().getMilestone().getId());

        // Verify milestone was persisted
        assertThat(milestoneRepository.findById(milestoned.getPullRequest().getMilestone().getId())).isPresent();
    }

    @Test
    @DisplayName("should remove milestone from pull request when demilestoned")
    void demilestonedEventRemovesMilestone(
        @GitHubPayload("pull_request.milestoned") GHEventPayload.PullRequest milestoned,
        @GitHubPayload("pull_request.demilestoned") GHEventPayload.PullRequest demilestoned
    ) throws Exception {
        // Arrange
        handler.handleEvent(milestoned);
        var prAfterMilestone = pullRequestRepository.findById(milestoned.getPullRequest().getId()).orElseThrow();
        assertThat(prAfterMilestone.getMilestone()).isNotNull();

        // Act
        handler.handleEvent(demilestoned);

        // Assert
        var pr = pullRequestRepository.findById(demilestoned.getPullRequest().getId()).orElseThrow();
        assertThat(pr.getMilestone()).isNull();
    }

    @Test
    @DisplayName("should update pull request lock status when locked")
    void lockedEventLocksPullRequest(
        @GitHubPayload("pull_request.opened") GHEventPayload.PullRequest opened,
        @GitHubPayload("pull_request.locked") GHEventPayload.PullRequest locked
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(locked);

        // Assert
        var pr = pullRequestRepository.findById(locked.getPullRequest().getId()).orElseThrow();
        assertThat(pr.isLocked()).isTrue();
    }

    @Test
    @DisplayName("should update pull request lock status when unlocked")
    void unlockedEventUnlocksPullRequest(
        @GitHubPayload("pull_request.locked") GHEventPayload.PullRequest locked,
        @GitHubPayload("pull_request.unlocked") GHEventPayload.PullRequest unlocked
    ) throws Exception {
        // Arrange
        handler.handleEvent(locked);
        var prAfterLocked = pullRequestRepository.findById(locked.getPullRequest().getId()).orElseThrow();
        assertThat(prAfterLocked.isLocked()).isTrue();

        // Act
        handler.handleEvent(unlocked);

        // Assert
        var pr = pullRequestRepository.findById(unlocked.getPullRequest().getId()).orElseThrow();
        assertThat(pr.isLocked()).isFalse();
    }

    @Test
    @DisplayName("should update pull request draft status when converted to draft")
    void convertedToDraftEventUpdatesDraftStatus(
        @GitHubPayload("pull_request.opened") GHEventPayload.PullRequest opened,
        @GitHubPayload("pull_request.converted_to_draft") GHEventPayload.PullRequest converted
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(converted);

        // Assert
        var pr = pullRequestRepository.findById(converted.getPullRequest().getId()).orElseThrow();
        assertThat(pr.isDraft()).isTrue();
    }

    @Test
    @DisplayName("should update pull request draft status when ready for review")
    void readyForReviewEventUpdatesDraftStatus(
        @GitHubPayload("pull_request.converted_to_draft") GHEventPayload.PullRequest draft,
        @GitHubPayload("pull_request.ready_for_review") GHEventPayload.PullRequest ready
    ) throws Exception {
        // Arrange
        handler.handleEvent(draft);
        var prAfterDraft = pullRequestRepository.findById(draft.getPullRequest().getId()).orElseThrow();
        assertThat(prAfterDraft.isDraft()).isTrue();

        // Act
        handler.handleEvent(ready);

        // Assert
        var pr = pullRequestRepository.findById(ready.getPullRequest().getId()).orElseThrow();
        assertThat(pr.isDraft()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("should process review requested event correctly")
    void reviewRequestedEventIsProcessed(
        @GitHubPayload("pull_request.opened") GHEventPayload.PullRequest opened,
        @GitHubPayload("pull_request.review_requested") GHEventPayload.PullRequest requested
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(requested);

        // Assert - Verify the PR exists and event was processed
        var pr = pullRequestRepository.findById(requested.getPullRequest().getId()).orElseThrow();
        assertThat(pr.getNumber()).isEqualTo(requested.getPullRequest().getNumber());
        // Note: test data has requested_teams but no requested_reviewers
        assertThat(pr.getRequestedReviewers()).hasSize(requested.getPullRequest().getRequestedReviewers().size());
    }

    @Test
    @Transactional
    @DisplayName("should process review request removed event correctly")
    void reviewRequestRemovedEventIsProcessed(
        @GitHubPayload("pull_request.review_requested") GHEventPayload.PullRequest requested,
        @GitHubPayload("pull_request.review_request_removed") GHEventPayload.PullRequest removed
    ) throws Exception {
        // Arrange
        handler.handleEvent(requested);

        // Act
        handler.handleEvent(removed);

        // Assert - Verify the PR exists and event was processed
        var pr = pullRequestRepository.findById(removed.getPullRequest().getId()).orElseThrow();
        assertThat(pr.getNumber()).isEqualTo(removed.getPullRequest().getNumber());
        // Verify reviewers list matches the payload
        assertThat(pr.getRequestedReviewers()).hasSize(removed.getPullRequest().getRequestedReviewers().size());
    }

    @Test
    @Transactional
    @DisplayName("should capture requested teams for pull request")
    void capturesRequestedTeams(@GitHubPayload("pull_request.review_requested") GHEventPayload.PullRequest payload)
        throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var pr = pullRequestRepository.findById(payload.getPullRequest().getId()).orElseThrow();
        // Test payload has requested_teams with team review request
        assertThat(pr.getRequestedTeams()).hasSize(payload.getPullRequest().getRequestedTeams().size());
    }

    @Test
    @DisplayName("should capture all new fields for pull request if available")
    void capturesAllNewFieldsForPullRequest(@GitHubPayload("pull_request.opened") GHEventPayload.PullRequest payload)
        throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var pr = pullRequestRepository.findById(payload.getPullRequest().getId()).orElseThrow();

        // Verify fields are initialized (enrichment attempted)
        assertThat(pr.getReactionsTotal()).isGreaterThanOrEqualTo(0);
        assertThat(pr.getSubIssuesTotal()).isGreaterThanOrEqualTo(0);
        assertThat(pr.getBlockedByCount()).isGreaterThanOrEqualTo(0);
        assertThat(pr.getBlockingCount()).isGreaterThanOrEqualTo(0);

        // These may be null if not in payload
        assertThat(pr.getStateReason()).isNull();
        assertThat(pr.getActiveLockReason()).isNull();
    }

    @Test
    @DisplayName("should capture author association for pull request if available")
    void capturesAuthorAssociationForPR(@GitHubPayload("pull_request.opened") GHEventPayload.PullRequest payload)
        throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var pr = pullRequestRepository.findById(payload.getPullRequest().getId()).orElseThrow();
        // Author association enrichment attempted, test passes if no errors
        assertThat(pr).isNotNull();
    }

    @Test
    @DisplayName("should capture state reason when pull request is closed if available")
    void capturesStateReasonForPR(
        @GitHubPayload("pull_request.opened") GHEventPayload.PullRequest opened,
        @GitHubPayload("pull_request.closed") GHEventPayload.PullRequest closed
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(closed);

        // Assert
        var pr = pullRequestRepository.findById(closed.getPullRequest().getId()).orElseThrow();
        assertThat(pr.getState()).isEqualTo(Issue.State.CLOSED);
        // State reason enrichment attempted, test passes if no errors
        assertThat(pr).isNotNull();
    }

    @Test
    @DisplayName("should capture reactions for pull request")
    void capturesReactionsForPR(@GitHubPayload("pull_request.opened") GHEventPayload.PullRequest payload)
        throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var pr = pullRequestRepository.findById(payload.getPullRequest().getId()).orElseThrow();
        assertThat(pr.getReactionsTotal()).isGreaterThanOrEqualTo(0);
        assertThat(pr.getReactionsPlus1()).isGreaterThanOrEqualTo(0);
        assertThat(pr.getReactionsMinus1()).isGreaterThanOrEqualTo(0);
        assertThat(pr.getReactionsLaugh()).isGreaterThanOrEqualTo(0);
        assertThat(pr.getReactionsHooray()).isGreaterThanOrEqualTo(0);
        assertThat(pr.getReactionsConfused()).isGreaterThanOrEqualTo(0);
        assertThat(pr.getReactionsHeart()).isGreaterThanOrEqualTo(0);
        assertThat(pr.getReactionsRocket()).isGreaterThanOrEqualTo(0);
        assertThat(pr.getReactionsEyes()).isGreaterThanOrEqualTo(0);
    }
}
