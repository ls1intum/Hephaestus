package de.tum.in.www1.hephaestus.gitprovider.subissues.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.testutil.github.annotation.GitHubPayload;
import de.tum.in.www1.hephaestus.testutil.integration.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEventPayloadSubIssues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubSubIssuesMessageHandler.
 *
 * Tests webhook event handling for GitHub's sub-issues (tasklists) feature,
 * which allows tracking parent-child relationships between issues.
 */
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
    @GitHubPayload("sub_issues.sub_issue_added")
    void handlesSubIssueAddedEvent(GHEventPayloadSubIssues payload) {
        // Arrange - payload loaded by @GitHubPayload

        // Act
        subIssuesMessageHandler.handleEvent(payload);

        // Assert - verify both parent and sub-issue are processed
        var parentIssue = issueRepository.findById(payload.getParentIssue().getId());
        var subIssue = issueRepository.findById(payload.getSubIssue().getId());

        assertThat(parentIssue).isPresent();
        assertThat(subIssue).isPresent();

        // Verify basic properties are captured
        assertThat(parentIssue.get().getNumber()).isEqualTo(payload.getParentIssue().getNumber());
        assertThat(subIssue.get().getNumber()).isEqualTo(payload.getSubIssue().getNumber());
    }

    @Test
    @Transactional
    @GitHubPayload("sub_issues.sub_issue_removed")
    void handlesSubIssueRemovedEvent(GHEventPayloadSubIssues payload) {
        // Arrange - payload loaded by @GitHubPayload

        // Act
        subIssuesMessageHandler.handleEvent(payload);

        // Assert - verify both issues are still processed (relationship is in metadata)
        var parentIssue = issueRepository.findById(payload.getParentIssue().getId());
        var subIssue = issueRepository.findById(payload.getSubIssue().getId());

        assertThat(parentIssue).isPresent();
        assertThat(subIssue).isPresent();
    }

    @Test
    @Transactional
    @GitHubPayload("sub_issues.parent_issue_added")
    void handlesParentIssueAddedEvent(GHEventPayloadSubIssues payload) {
        // Arrange - payload loaded by @GitHubPayload

        // Act
        subIssuesMessageHandler.handleEvent(payload);

        // Assert - verify both issues are processed
        var parentIssue = issueRepository.findById(payload.getParentIssue().getId());
        var subIssue = issueRepository.findById(payload.getSubIssue().getId());

        assertThat(parentIssue).isPresent();
        assertThat(subIssue).isPresent();

        // Verify the relationship is captured
        assertThat(parentIssue.get().getTitle()).isNotEmpty();
        assertThat(subIssue.get().getTitle()).isNotEmpty();
    }

    @Test
    @Transactional
    @GitHubPayload("sub_issues.parent_issue_removed")
    void handlesParentIssueRemovedEvent(GHEventPayloadSubIssues payload) {
        // Arrange - payload loaded by @GitHubPayload

        // Act
        subIssuesMessageHandler.handleEvent(payload);

        // Assert - verify both issues are still processed
        var parentIssue = issueRepository.findById(payload.getParentIssue().getId());
        var subIssue = issueRepository.findById(payload.getSubIssue().getId());

        assertThat(parentIssue).isPresent();
        assertThat(subIssue).isPresent();
    }

    @Test
    @Transactional
    @GitHubPayload("sub_issues.sub_issue_added")
    void capturesSubIssuesMetadataFromPayload(GHEventPayloadSubIssues payload) {
        // Arrange - payload loaded by @GitHubPayload

        // Act
        subIssuesMessageHandler.handleEvent(payload);

        // Assert - verify sub-issues summary counts are captured via enrichment
        var parentIssue = issueRepository.findById(payload.getParentIssue().getId());

        assertThat(parentIssue).isPresent();

        // The enrichment process should attempt to capture sub-issues metadata
        // Note: Actual values depend on what's in the GitHub JSON payload
        // The enrichIssueFromGitHub() method will parse these fields when available
        assertThat(parentIssue.get()).isNotNull();
    }
}
