package de.tum.in.www1.hephaestus.gitprovider.subissues.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayloadSubIssues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubSubIssuesMessageHandler.
 *
 * Tests webhook event handling for GitHub's sub-issues (tasklists) feature,
 * which allows tracking parent-child relationships between issues.
 */
@ExtendWith(GitHubPayloadExtension.class)
class GitHubSubIssuesMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubSubIssuesMessageHandler subIssuesMessageHandler;

    @Autowired
    private IssueRepository issueRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @Transactional
    void handlesSubIssueAddedEvent(@GitHubPayload("sub_issues.sub_issue_added") GHEventPayloadSubIssues payload) {
        // Arrange - payload loaded by @GitHubPayload

        // Act
        subIssuesMessageHandler.handleEvent(payload);

        // Assert - verify both parent and sub-issue are processed
        var parentIssue = issueRepository.findById(payload.getParentIssue().getId()).orElseThrow();
        var subIssue = issueRepository.findById(payload.getSubIssue().getId()).orElseThrow();

        // Verify basic properties are captured
        assertThat(parentIssue.getNumber()).isEqualTo(payload.getParentIssue().getNumber());
        assertThat(subIssue.getNumber()).isEqualTo(payload.getSubIssue().getNumber());

        // Relationship persisted
        assertThat(parentIssue.getSubIssues()).extracting(Issue::getId).contains(subIssue.getId());
        assertThat(subIssue.getParentIssues()).extracting(Issue::getId).contains(parentIssue.getId());
    }

    @Test
    @Transactional
    void handlesSubIssueRemovedEvent(
        @GitHubPayload("sub_issues.sub_issue_added") GHEventPayloadSubIssues added,
        @GitHubPayload("sub_issues.sub_issue_removed") GHEventPayloadSubIssues removed
    ) {
        // Arrange
        subIssuesMessageHandler.handleEvent(added);
        var parentBefore = issueRepository.findById(added.getParentIssue().getId()).orElseThrow();
        assertThat(parentBefore.getSubIssues()).isNotEmpty();

        // Act
        subIssuesMessageHandler.handleEvent(removed);

        // Assert
        var parentAfter = issueRepository.findById(removed.getParentIssue().getId()).orElseThrow();
        var childAfter = issueRepository.findById(removed.getSubIssue().getId()).orElseThrow();

        assertThat(parentAfter.getSubIssues()).extracting(Issue::getId).doesNotContain(childAfter.getId());
        assertThat(childAfter.getParentIssues()).extracting(Issue::getId).doesNotContain(parentAfter.getId());
    }

    @Test
    @Transactional
    void handlesParentIssueAddedEvent(@GitHubPayload("sub_issues.parent_issue_added") GHEventPayloadSubIssues payload) {
        // Arrange - payload loaded by @GitHubPayload

        // Act
        subIssuesMessageHandler.handleEvent(payload);

        // Assert - verify both issues are processed
        var parentIssue = issueRepository.findById(payload.getParentIssue().getId()).orElseThrow();
        var subIssue = issueRepository.findById(payload.getSubIssue().getId()).orElseThrow();

        // Verify the relationship is captured
        assertThat(parentIssue.getSubIssues()).extracting(Issue::getId).contains(subIssue.getId());
        assertThat(subIssue.getParentIssues()).extracting(Issue::getId).contains(parentIssue.getId());
    }

    @Test
    @Transactional
    void handlesParentIssueRemovedEvent(
        @GitHubPayload("sub_issues.parent_issue_added") GHEventPayloadSubIssues added,
        @GitHubPayload("sub_issues.parent_issue_removed") GHEventPayloadSubIssues removed
    ) {
        // Arrange
        subIssuesMessageHandler.handleEvent(added);
        var parentBefore = issueRepository.findById(added.getParentIssue().getId()).orElseThrow();
        assertThat(parentBefore.getSubIssues()).isNotEmpty();

        // Act
        subIssuesMessageHandler.handleEvent(removed);

        // Assert
        var parentAfter = issueRepository.findById(removed.getParentIssue().getId()).orElseThrow();
        var childAfter = issueRepository.findById(removed.getSubIssue().getId()).orElseThrow();

        assertThat(parentAfter.getSubIssues()).extracting(Issue::getId).doesNotContain(childAfter.getId());
        assertThat(childAfter.getParentIssues()).extracting(Issue::getId).doesNotContain(parentAfter.getId());
    }

    @Test
    @Transactional
    void capturesSubIssuesMetadataFromPayload(
        @GitHubPayload("sub_issues.sub_issue_added") GHEventPayloadSubIssues payload
    ) {
        // Arrange - payload loaded by @GitHubPayload

        // Act
        subIssuesMessageHandler.handleEvent(payload);

        // Assert - verify sub-issues summary counts are captured via enrichment
        var parentIssue = issueRepository.findById(payload.getParentIssue().getId()).orElseThrow();
        var summary = payload.getParentIssue().getSubIssuesSummary();
        var dependencies = payload.getParentIssue().getIssueDependenciesSummary();

        assertThat(parentIssue.getSubIssuesTotal()).isEqualTo(summary.getTotal());
        assertThat(parentIssue.getSubIssuesCompleted()).isEqualTo(summary.getCompleted());
        assertThat(parentIssue.getBlockedByCount()).isEqualTo(dependencies.getBlockedBy());
        assertThat(parentIssue.getBlockingCount()).isEqualTo(dependencies.getBlocking());
    }
}
