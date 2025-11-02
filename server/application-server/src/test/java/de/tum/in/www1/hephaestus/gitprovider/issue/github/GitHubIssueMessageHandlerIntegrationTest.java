package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.issue.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.LockReason;
import de.tum.in.www1.hephaestus.gitprovider.issue.StateReason;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayloadIssueExtended;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Issue Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubIssueMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubIssueMessageHandler handler;

    @Autowired
    private IssueRepository issueRepository;

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
    @DisplayName("should persist issue when opened")
    void openedEventPersistsIssue(@GitHubPayload("issues.opened") GHEventPayloadIssueExtended payload)
        throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var issue = issueRepository.findById(payload.getIssue().getId());
        assertThat(issue)
            .isPresent()
            .get()
            .satisfies(saved -> {
                assertThat(saved.getNumber()).isEqualTo(payload.getIssue().getNumber());
                assertThat(saved.getTitle()).isEqualTo(payload.getIssue().getTitle());
                assertThat(saved.getState()).isEqualTo(Issue.State.OPEN);
                assertThat(saved.getHtmlUrl()).isEqualTo(payload.getIssue().getHtmlUrl().toString());
                assertThat(saved.getBody()).isEqualTo(payload.getIssue().getBody());
                assertThat(saved.isLocked()).isFalse();

                // Verify new fields - they may be null if not in payload or not accessible
                // The enrichment will populate these when available
                assertThat(saved.getReactionsTotal()).isGreaterThanOrEqualTo(0);
                assertThat(saved.getSubIssuesTotal()).isGreaterThanOrEqualTo(0);
                assertThat(saved.getSubIssuesCompleted()).isGreaterThanOrEqualTo(0);
                assertThat(saved.getBlockedByCount()).isGreaterThanOrEqualTo(0);
                assertThat(saved.getBlockingCount()).isGreaterThanOrEqualTo(0);
            });

        // Verify repository was created
        assertThat(repositoryRepository.findAll()).isNotEmpty();

        // Verify author was created
        assertThat(userRepository.findById(payload.getIssue().getUser().getId())).isPresent();
    }

    @Test
    @DisplayName("should update issue state when closed")
    void closedEventUpdatesIssueState(
        @GitHubPayload("issues.opened") GHEventPayloadIssueExtended opened,
        @GitHubPayload("issues.closed") GHEventPayloadIssueExtended closed
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(closed);

        // Assert
        var issue = issueRepository.findById(closed.getIssue().getId()).orElseThrow();
        assertThat(issue.getState()).isEqualTo(Issue.State.CLOSED);
        assertThat(issue.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("should update issue state when reopened")
    void reopenedEventUpdatesIssueState(
        @GitHubPayload("issues.closed") GHEventPayloadIssueExtended closed,
        @GitHubPayload("issues.reopened") GHEventPayloadIssueExtended reopened
    ) throws Exception {
        // Arrange
        handler.handleEvent(closed);
        var issueAfterClosed = issueRepository.findById(closed.getIssue().getId()).orElseThrow();
        assertThat(issueAfterClosed.getState()).isEqualTo(Issue.State.CLOSED);

        // Act
        handler.handleEvent(reopened);

        // Assert
        var issue = issueRepository.findById(reopened.getIssue().getId()).orElseThrow();
        assertThat(issue.getState()).isEqualTo(Issue.State.OPEN);
    }

    @Test
    @DisplayName("should delete issue when deleted")
    void deletedEventDeletesIssue(
        @GitHubPayload("issues.opened") GHEventPayloadIssueExtended opened,
        @GitHubPayload("issues.deleted") GHEventPayloadIssueExtended deleted
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);
        assertThat(issueRepository.findById(opened.getIssue().getId())).isPresent();

        // Act
        handler.handleEvent(deleted);

        // Assert
        assertThat(issueRepository.findById(deleted.getIssue().getId())).isEmpty();
    }

    @Test
    @DisplayName("should update issue title and body when edited")
    void editedEventUpdatesIssue(
        @GitHubPayload("issues.opened") GHEventPayloadIssueExtended opened,
        @GitHubPayload("issues.edited") GHEventPayloadIssueExtended edited
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(edited);

        // Assert
        var issue = issueRepository.findById(edited.getIssue().getId()).orElseThrow();
        assertThat(issue.getTitle()).isEqualTo(edited.getIssue().getTitle());
        assertThat(issue.getBody()).isEqualTo(edited.getIssue().getBody());
    }

    @Test
    @Transactional
    @DisplayName("should add label to issue when labeled")
    void labeledEventAddsLabel(
        @GitHubPayload("issues.opened") GHEventPayloadIssueExtended opened,
        @GitHubPayload("issues.labeled") GHEventPayloadIssueExtended labeled
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(labeled);

        // Assert
        var issue = issueRepository.findById(labeled.getIssue().getId()).orElseThrow();
        assertThat(issue.getLabels()).hasSize(labeled.getIssue().getLabels().size());
        assertThat(issue.getLabels()).isNotEmpty();

        // Verify label was persisted
        var labels = labelRepository.findAll();
        assertThat(labels).isNotEmpty();
    }

    @Test
    @Transactional
    @DisplayName("should remove label from issue when unlabeled")
    void unlabeledEventRemovesLabel(
        @GitHubPayload("issues.labeled") GHEventPayloadIssueExtended labeled,
        @GitHubPayload("issues.unlabeled") GHEventPayloadIssueExtended unlabeled
    ) throws Exception {
        // Arrange
        handler.handleEvent(labeled);
        var issueAfterLabel = issueRepository.findById(labeled.getIssue().getId()).orElseThrow();
        assertThat(issueAfterLabel.getLabels()).isNotEmpty();

        // Act
        handler.handleEvent(unlabeled);

        // Assert
        var issue = issueRepository.findById(unlabeled.getIssue().getId()).orElseThrow();
        assertThat(issue.getLabels()).hasSize(unlabeled.getIssue().getLabels().size());
    }

    @Test
    @Transactional
    @DisplayName("should add assignee to issue when assigned")
    void assignedEventAddsAssignee(
        @GitHubPayload("issues.opened") GHEventPayloadIssueExtended opened,
        @GitHubPayload("issues.assigned") GHEventPayloadIssueExtended assigned
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(assigned);

        // Assert
        var issue = issueRepository.findById(assigned.getIssue().getId()).orElseThrow();
        assertThat(issue.getAssignees()).hasSize(assigned.getIssue().getAssignees().size());
        assertThat(issue.getAssignees()).isNotEmpty();
    }

    @Test
    @Transactional
    @DisplayName("should remove assignee from issue when unassigned")
    void unassignedEventRemovesAssignee(
        @GitHubPayload("issues.assigned") GHEventPayloadIssueExtended assigned,
        @GitHubPayload("issues.unassigned") GHEventPayloadIssueExtended unassigned
    ) throws Exception {
        // Arrange
        handler.handleEvent(assigned);
        var issueAfterAssigned = issueRepository.findById(assigned.getIssue().getId()).orElseThrow();
        assertThat(issueAfterAssigned.getAssignees()).isNotEmpty();

        // Act
        handler.handleEvent(unassigned);

        // Assert
        var issue = issueRepository.findById(unassigned.getIssue().getId()).orElseThrow();
        assertThat(issue.getAssignees()).hasSize(unassigned.getIssue().getAssignees().size());
    }

    @Test
    @DisplayName("should add milestone to issue when milestoned")
    void milestonedEventAddsMilestone(
        @GitHubPayload("issues.opened") GHEventPayloadIssueExtended opened,
        @GitHubPayload("issues.milestoned") GHEventPayloadIssueExtended milestoned
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(milestoned);

        // Assert
        var issue = issueRepository.findById(milestoned.getIssue().getId()).orElseThrow();
        assertThat(issue.getMilestone()).isNotNull();
        assertThat(issue.getMilestone().getId()).isEqualTo(milestoned.getIssue().getMilestone().getId());

        // Verify milestone was persisted
        assertThat(milestoneRepository.findById(milestoned.getIssue().getMilestone().getId())).isPresent();
    }

    @Test
    @DisplayName("should remove milestone from issue when demilestoned")
    void demilestonedEventRemovesMilestone(
        @GitHubPayload("issues.milestoned") GHEventPayloadIssueExtended milestoned,
        @GitHubPayload("issues.demilestoned") GHEventPayloadIssueExtended demilestoned
    ) throws Exception {
        // Arrange
        handler.handleEvent(milestoned);
        var issueAfterMilestone = issueRepository.findById(milestoned.getIssue().getId()).orElseThrow();
        assertThat(issueAfterMilestone.getMilestone()).isNotNull();

        // Act
        handler.handleEvent(demilestoned);

        // Assert
        var issue = issueRepository.findById(demilestoned.getIssue().getId()).orElseThrow();
        assertThat(issue.getMilestone()).isNull();
    }

    @Test
    @DisplayName("should update issue lock status when locked")
    void lockedEventLocksIssue(
        @GitHubPayload("issues.opened") GHEventPayloadIssueExtended opened,
        @GitHubPayload("issues.locked") GHEventPayloadIssueExtended locked
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(locked);

        // Assert
        var issue = issueRepository.findById(locked.getIssue().getId()).orElseThrow();
        assertThat(issue.isLocked()).isTrue();
    }

    @Test
    @DisplayName("should update issue lock status when unlocked")
    void unlockedEventUnlocksIssue(
        @GitHubPayload("issues.locked") GHEventPayloadIssueExtended locked,
        @GitHubPayload("issues.unlocked") GHEventPayloadIssueExtended unlocked
    ) throws Exception {
        // Arrange
        handler.handleEvent(locked);
        var issueAfterLocked = issueRepository.findById(locked.getIssue().getId()).orElseThrow();
        assertThat(issueAfterLocked.isLocked()).isTrue();

        // Act
        handler.handleEvent(unlocked);

        // Assert
        var issue = issueRepository.findById(unlocked.getIssue().getId()).orElseThrow();
        assertThat(issue.isLocked()).isFalse();
    }

    @Test
    @DisplayName("should handle issue pinned event")
    void pinnedEventHandled(@GitHubPayload("issues.pinned") GHEventPayloadIssueExtended payload) throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert - Issue should be persisted
        var issue = issueRepository.findById(payload.getIssue().getId());
        assertThat(issue)
            .isPresent()
            .get()
            .satisfies(saved -> {
                assertThat(saved.getNumber()).isEqualTo(payload.getIssue().getNumber());
                assertThat(saved.getTitle()).isEqualTo(payload.getIssue().getTitle());
            });
    }

    @Test
    @DisplayName("should handle issue unpinned event")
    void unpinnedEventHandled(
        @GitHubPayload("issues.pinned") GHEventPayloadIssueExtended pinned,
        @GitHubPayload("issues.unpinned") GHEventPayloadIssueExtended unpinned
    ) throws Exception {
        // Arrange
        handler.handleEvent(pinned);

        // Act
        handler.handleEvent(unpinned);

        // Assert - Issue should still exist
        var issue = issueRepository.findById(unpinned.getIssue().getId());
        assertThat(issue).isPresent();
    }

    @Test
    @DisplayName("should handle issue transferred event")
    void transferredEventHandled(@GitHubPayload("issues.transferred") GHEventPayloadIssueExtended payload)
        throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert - Issue should be persisted with new repository reference
        var issue = issueRepository.findById(payload.getIssue().getId());
        assertThat(issue)
            .isPresent()
            .get()
            .satisfies(saved -> {
                assertThat(saved.getNumber()).isEqualTo(payload.getIssue().getNumber());
                assertThat(saved.getTitle()).isEqualTo(payload.getIssue().getTitle());
            });

        // Verify repository was created
        assertThat(repositoryRepository.findAll()).isNotEmpty();
    }

    @Test
    @DisplayName("should handle issue typed event")
    void typedEventHandled(
        @GitHubPayload("issues.opened") GHEventPayloadIssueExtended opened,
        @GitHubPayload("issues.typed") GHEventPayloadIssueExtended typed
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(typed);

        // Assert - Issue should still exist and be updated
        var issue = issueRepository.findById(typed.getIssue().getId());
        assertThat(issue).isPresent();
    }

    @Test
    @DisplayName("should handle issue untyped event")
    void untypedEventHandled(
        @GitHubPayload("issues.typed") GHEventPayloadIssueExtended typed,
        @GitHubPayload("issues.untyped") GHEventPayloadIssueExtended untyped
    ) throws Exception {
        // Arrange
        handler.handleEvent(typed);

        // Act
        handler.handleEvent(untyped);

        // Assert - Issue should still exist and be updated
        var issue = issueRepository.findById(untyped.getIssue().getId());
        assertThat(issue).isPresent();
    }

    @Test
    @DisplayName("should capture author association for issue if available")
    void capturesAuthorAssociation(@GitHubPayload("issues.opened") GHEventPayloadIssueExtended payload)
        throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var issue = issueRepository.findById(payload.getIssue().getId()).orElseThrow();
        assertThat(issue.getAuthorAssociation()).isEqualTo(AuthorAssociation.CONTRIBUTOR);
    }

    @Test
    @DisplayName("should capture state reason when issue is closed if available")
    void capturesStateReason(
        @GitHubPayload("issues.opened") GHEventPayloadIssueExtended opened,
        @GitHubPayload("issues.closed") GHEventPayloadIssueExtended closed
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);

        // Act
        handler.handleEvent(closed);

        // Assert
        var issue = issueRepository.findById(closed.getIssue().getId()).orElseThrow();
        assertThat(issue.getState()).isEqualTo(Issue.State.CLOSED);
        assertThat(issue.getStateReason()).isEqualTo(StateReason.COMPLETED);
    }

    @Test
    @DisplayName("should capture lock reason when issue is locked if available")
    void capturesLockReason(@GitHubPayload("issues.locked") GHEventPayloadIssueExtended payload) throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var issue = issueRepository.findById(payload.getIssue().getId()).orElseThrow();
        assertThat(issue.isLocked()).isTrue();
        assertThat(issue.getActiveLockReason()).isEqualTo(LockReason.RESOLVED);
    }

    @Test
    @DisplayName("should capture reactions summary for issue")
    void capturesReactions(@GitHubPayload("issues.opened") GHEventPayloadIssueExtended payload) throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var issue = issueRepository.findById(payload.getIssue().getId()).orElseThrow();
        // Test payload shows 0 reactions in all categories
        assertThat(issue.getReactionsTotal()).isEqualTo(0);
        assertThat(issue.getReactionsPlus1()).isEqualTo(0);
        assertThat(issue.getReactionsMinus1()).isEqualTo(0);
        assertThat(issue.getReactionsLaugh()).isEqualTo(0);
        assertThat(issue.getReactionsHooray()).isEqualTo(0);
        assertThat(issue.getReactionsConfused()).isEqualTo(0);
        assertThat(issue.getReactionsHeart()).isEqualTo(0);
        assertThat(issue.getReactionsRocket()).isEqualTo(0);
        assertThat(issue.getReactionsEyes()).isEqualTo(0);
    }

    @Test
    @DisplayName("should capture sub-issues summary for issue")
    void capturesSubIssuesSummary(@GitHubPayload("issues.opened") GHEventPayloadIssueExtended payload)
        throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var issue = issueRepository.findById(payload.getIssue().getId()).orElseThrow();
        // Test payload shows 0 sub-issues
        assertThat(issue.getSubIssuesTotal()).isEqualTo(0);
        assertThat(issue.getSubIssuesCompleted()).isEqualTo(0);
    }

    @Test
    @DisplayName("should capture issue dependencies summary for issue")
    void capturesIssueDependenciesSummary(@GitHubPayload("issues.opened") GHEventPayloadIssueExtended payload)
        throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var issue = issueRepository.findById(payload.getIssue().getId()).orElseThrow();
        // Test payload shows 0 dependencies
        assertThat(issue.getBlockedByCount()).isEqualTo(0);
        assertThat(issue.getBlockingCount()).isEqualTo(0);
    }
}
