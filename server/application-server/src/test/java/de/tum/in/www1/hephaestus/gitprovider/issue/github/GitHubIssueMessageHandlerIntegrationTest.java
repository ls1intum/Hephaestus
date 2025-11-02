package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
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
    void openedEventPersistsIssue(@GitHubPayload("issues.opened") GHEventPayload.Issue payload) throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var issue = issueRepository.findById(payload.getIssue().getId());
        assertThat(issue).isPresent().get().satisfies(saved -> {
            assertThat(saved.getNumber()).isEqualTo(payload.getIssue().getNumber());
            assertThat(saved.getTitle()).isEqualTo(payload.getIssue().getTitle());
            assertThat(saved.getState()).isEqualTo(Issue.State.OPEN);
            assertThat(saved.getHtmlUrl()).isEqualTo(payload.getIssue().getHtmlUrl().toString());
            assertThat(saved.getBody()).isEqualTo(payload.getIssue().getBody());
            assertThat(saved.isLocked()).isFalse();
        });

        // Verify repository was created
        assertThat(repositoryRepository.findAll()).isNotEmpty();
        
        // Verify author was created
        assertThat(userRepository.findById(payload.getIssue().getUser().getId())).isPresent();
    }

    @Test
    @DisplayName("should update issue state when closed")
    void closedEventUpdatesIssueState(
        @GitHubPayload("issues.opened") GHEventPayload.Issue opened,
        @GitHubPayload("issues.closed") GHEventPayload.Issue closed
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
        @GitHubPayload("issues.closed") GHEventPayload.Issue closed,
        @GitHubPayload("issues.reopened") GHEventPayload.Issue reopened
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
        @GitHubPayload("issues.opened") GHEventPayload.Issue opened,
        @GitHubPayload("issues.deleted") GHEventPayload.Issue deleted
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
        @GitHubPayload("issues.opened") GHEventPayload.Issue opened,
        @GitHubPayload("issues.edited") GHEventPayload.Issue edited
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
    @DisplayName("should add label to issue when labeled")
    void labeledEventAddsLabel(
        @GitHubPayload("issues.opened") GHEventPayload.Issue opened,
        @GitHubPayload("issues.labeled") GHEventPayload.Issue labeled
    ) throws Exception {
        // Arrange
        handler.handleEvent(opened);
        var issueBefore = issueRepository.findById(opened.getIssue().getId()).orElseThrow();
        int initialLabelCount = issueBefore.getLabels().size();

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
    @DisplayName("should remove label from issue when unlabeled")
    void unlabeledEventRemovesLabel(
        @GitHubPayload("issues.labeled") GHEventPayload.Issue labeled,
        @GitHubPayload("issues.unlabeled") GHEventPayload.Issue unlabeled
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
    @DisplayName("should add assignee to issue when assigned")
    void assignedEventAddsAssignee(
        @GitHubPayload("issues.opened") GHEventPayload.Issue opened,
        @GitHubPayload("issues.assigned") GHEventPayload.Issue assigned
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
    @DisplayName("should remove assignee from issue when unassigned")
    void unassignedEventRemovesAssignee(
        @GitHubPayload("issues.assigned") GHEventPayload.Issue assigned,
        @GitHubPayload("issues.unassigned") GHEventPayload.Issue unassigned
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
        @GitHubPayload("issues.opened") GHEventPayload.Issue opened,
        @GitHubPayload("issues.milestoned") GHEventPayload.Issue milestoned
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
        @GitHubPayload("issues.milestoned") GHEventPayload.Issue milestoned,
        @GitHubPayload("issues.demilestoned") GHEventPayload.Issue demilestoned
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
        @GitHubPayload("issues.opened") GHEventPayload.Issue opened,
        @GitHubPayload("issues.locked") GHEventPayload.Issue locked
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
        @GitHubPayload("issues.locked") GHEventPayload.Issue locked,
        @GitHubPayload("issues.unlocked") GHEventPayload.Issue unlocked
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
    void pinnedEventHandled(@GitHubPayload("issues.pinned") GHEventPayload.Issue payload) throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert - Issue should be persisted
        var issue = issueRepository.findById(payload.getIssue().getId());
        assertThat(issue).isPresent().get().satisfies(saved -> {
            assertThat(saved.getNumber()).isEqualTo(payload.getIssue().getNumber());
            assertThat(saved.getTitle()).isEqualTo(payload.getIssue().getTitle());
        });
    }

    @Test
    @DisplayName("should handle issue unpinned event")
    void unpinnedEventHandled(
        @GitHubPayload("issues.pinned") GHEventPayload.Issue pinned,
        @GitHubPayload("issues.unpinned") GHEventPayload.Issue unpinned
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
    void transferredEventHandled(@GitHubPayload("issues.transferred") GHEventPayload.Issue payload) throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert - Issue should be persisted with new repository reference
        var issue = issueRepository.findById(payload.getIssue().getId());
        assertThat(issue).isPresent().get().satisfies(saved -> {
            assertThat(saved.getNumber()).isEqualTo(payload.getIssue().getNumber());
            assertThat(saved.getTitle()).isEqualTo(payload.getIssue().getTitle());
        });
        
        // Verify repository was created
        assertThat(repositoryRepository.findAll()).isNotEmpty();
    }

    @Test
    @DisplayName("should handle issue typed event")
    void typedEventHandled(
        @GitHubPayload("issues.opened") GHEventPayload.Issue opened,
        @GitHubPayload("issues.typed") GHEventPayload.Issue typed
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
        @GitHubPayload("issues.typed") GHEventPayload.Issue typed,
        @GitHubPayload("issues.untyped") GHEventPayload.Issue untyped
    ) throws Exception {
        // Arrange
        handler.handleEvent(typed);

        // Act
        handler.handleEvent(untyped);

        // Assert - Issue should still exist and be updated
        var issue = issueRepository.findById(untyped.getIssue().getId());
        assertThat(issue).isPresent();
    }
}
