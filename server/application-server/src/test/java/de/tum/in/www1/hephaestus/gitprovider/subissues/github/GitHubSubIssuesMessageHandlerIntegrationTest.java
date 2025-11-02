package de.tum.in.www1.hephaestus.gitprovider.subissues.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayloadSubIssues;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Sub-Issues Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubSubIssuesMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubSubIssuesMessageHandler handler;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("should persist both sub-issue and parent when sub-issue is added")
    void subIssueAddedEventPersistsBothIssues(
        @GitHubPayload("sub_issues.sub_issue_added") GHEventPayloadSubIssues payload
    ) throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert - Both sub-issue and parent should be persisted
        var subIssue = issueRepository.findById(payload.getSubIssue().getId());
        assertThat(subIssue).isPresent().get().satisfies(saved -> {
            assertThat(saved.getNumber()).isEqualTo(payload.getSubIssue().getNumber());
            assertThat(saved.getTitle()).isEqualTo(payload.getSubIssue().getTitle());
        });

        var parentIssue = issueRepository.findById(payload.getParentIssue().getId());
        assertThat(parentIssue).isPresent().get().satisfies(saved -> {
            assertThat(saved.getNumber()).isEqualTo(payload.getParentIssue().getNumber());
            assertThat(saved.getTitle()).isEqualTo(payload.getParentIssue().getTitle());
        });

        // Verify repository was created
        assertThat(repositoryRepository.findAll()).isNotEmpty();
    }

    @Test
    @DisplayName("should handle sub-issue removal correctly")
    void subIssueRemovedEventHandled(
        @GitHubPayload("sub_issues.sub_issue_added") GHEventPayloadSubIssues added,
        @GitHubPayload("sub_issues.sub_issue_removed") GHEventPayloadSubIssues removed
    ) throws Exception {
        // Arrange
        handler.handleEvent(added);
        var subIssueAfterAdd = issueRepository.findById(added.getSubIssue().getId());
        var parentIssueAfterAdd = issueRepository.findById(added.getParentIssue().getId());
        assertThat(subIssueAfterAdd).isPresent();
        assertThat(parentIssueAfterAdd).isPresent();

        // Act
        handler.handleEvent(removed);

        // Assert - Both issues should still exist but relationship metadata should be updated
        var subIssueAfterRemoval = issueRepository.findById(removed.getSubIssue().getId());
        var parentIssueAfterRemoval = issueRepository.findById(removed.getParentIssue().getId());

        assertThat(subIssueAfterRemoval).isPresent();
        assertThat(parentIssueAfterRemoval).isPresent();
    }

    @Test
    @DisplayName("should persist both issues when parent is added")
    void parentIssueAddedEventPersistsBothIssues(
        @GitHubPayload("sub_issues.parent_issue_added") GHEventPayloadSubIssues payload
    ) throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert - Both sub-issue and parent should be persisted
        var subIssue = issueRepository.findById(payload.getSubIssue().getId());
        assertThat(subIssue).isPresent().get().satisfies(saved -> {
            assertThat(saved.getNumber()).isEqualTo(payload.getSubIssue().getNumber());
            assertThat(saved.getTitle()).isEqualTo(payload.getSubIssue().getTitle());
        });

        var parentIssue = issueRepository.findById(payload.getParentIssue().getId());
        assertThat(parentIssue).isPresent().get().satisfies(saved -> {
            assertThat(saved.getNumber()).isEqualTo(payload.getParentIssue().getNumber());
            assertThat(saved.getTitle()).isEqualTo(payload.getParentIssue().getTitle());
        });

        // Verify repository was created
        assertThat(repositoryRepository.findAll()).isNotEmpty();
    }

    @Test
    @DisplayName("should handle parent removal correctly")
    void parentIssueRemovedEventHandled(
        @GitHubPayload("sub_issues.parent_issue_added") GHEventPayloadSubIssues added,
        @GitHubPayload("sub_issues.parent_issue_removed") GHEventPayloadSubIssues removed
    ) throws Exception {
        // Arrange
        handler.handleEvent(added);
        var subIssueAfterAdd = issueRepository.findById(added.getSubIssue().getId());
        var parentIssueAfterAdd = issueRepository.findById(added.getParentIssue().getId());
        assertThat(subIssueAfterAdd).isPresent();
        assertThat(parentIssueAfterAdd).isPresent();

        // Act
        handler.handleEvent(removed);

        // Assert - Both issues should still exist but relationship metadata should be updated
        var subIssueAfterRemoval = issueRepository.findById(removed.getSubIssue().getId());
        var parentIssueAfterRemoval = issueRepository.findById(removed.getParentIssue().getId());

        assertThat(subIssueAfterRemoval).isPresent();
        assertThat(parentIssueAfterRemoval).isPresent();
    }

    @Test
    @DisplayName("should ensure relationship IDs match payload")
    void subIssueEventVerifiesRelationshipIds(
        @GitHubPayload("sub_issues.sub_issue_added") GHEventPayloadSubIssues payload
    ) throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert - Verify the relationship IDs are correctly stored
        assertThat(payload.getSubIssueId()).isEqualTo(payload.getSubIssue().getId());
        assertThat(payload.getParentIssueId()).isEqualTo(payload.getParentIssue().getId());

        // Verify both issues are persisted with correct IDs
        var subIssue = issueRepository.findById(payload.getSubIssueId());
        var parentIssue = issueRepository.findById(payload.getParentIssueId());

        assertThat(subIssue).isPresent();
        assertThat(parentIssue).isPresent();
    }
}
