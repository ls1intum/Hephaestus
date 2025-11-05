package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Issue Comment Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubIssueCommentMessageHandlerIntegrationTest extends BaseIntegrationTest {

    private static final long ISSUE_COMMENT_ID = 3476883532L;

    @Autowired
    private GitHubIssueCommentMessageHandler handler;

    @Autowired
    private IssueCommentRepository issueCommentRepository;

    @Autowired
    private IssueRepository issueRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("should persist created issue comments with author and issue links")
    void createdEventPersistsComment(@GitHubPayload("issue_comment.created") GHEventPayload.IssueComment payload)
        throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var comment = issueCommentRepository.findById(ISSUE_COMMENT_ID).orElseThrow();
        assertThat(comment.getBody()).isEqualTo(payload.getComment().getBody());
        assertThat(comment.getHtmlUrl()).isEqualTo(payload.getComment().getHtmlUrl().toString());
        assertThat(comment.getAuthorAssociation()).isEqualTo(AuthorAssociation.MEMBER);
        assertThat(comment.getCreatedAt()).isEqualTo(Instant.parse("2025-11-01T21:44:00Z"));
        assertThat(comment.getUpdatedAt()).isEqualTo(Instant.parse("2025-11-01T21:44:00Z"));

        assertThat(comment.getAuthor()).isNotNull();
        assertThat(comment.getAuthor().getLogin()).isEqualTo(payload.getComment().getUser().getLogin());

        assertThat(comment.getIssue()).isNotNull();
        assertThat(comment.getIssue().getId()).isEqualTo(payload.getIssue().getId());
        assertThat(comment.getIssue().isHasPullRequest()).isFalse();

        assertThat(issueRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("should ignore duplicate create events")
    void createdEventIsIdempotent(@GitHubPayload("issue_comment.created") GHEventPayload.IssueComment payload)
        throws Exception {
        // Arrange
        handler.handleEvent(payload);
        var original = issueCommentRepository.findById(ISSUE_COMMENT_ID).orElseThrow();
        var originalUpdatedAt = original.getUpdatedAt();

        // Act
        handler.handleEvent(payload);

        // Assert
        assertThat(issueCommentRepository.count()).isEqualTo(1);
        assertThat(issueRepository.count()).isEqualTo(1);
        var replayed = issueCommentRepository.findById(ISSUE_COMMENT_ID).orElseThrow();
        assertThat(replayed.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    @DisplayName("should update comment body and timestamp on edit events")
    void editedEventUpdatesComment(
        @GitHubPayload("issue_comment.created") GHEventPayload.IssueComment created,
        @GitHubPayload("issue_comment.edited") GHEventPayload.IssueComment edited
    ) throws Exception {
        // Arrange
        handler.handleEvent(created);
        var originalUpdatedAt = issueCommentRepository.findById(ISSUE_COMMENT_ID).orElseThrow().getUpdatedAt();

        // Act
        handler.handleEvent(edited);

        // Assert
        var comment = issueCommentRepository.findById(ISSUE_COMMENT_ID).orElseThrow();
        assertThat(comment.getBody()).isEqualTo(edited.getComment().getBody());
        assertThat(comment.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("should delete comments without removing parent issue on delete events")
    void deletedEventRemovesComment(
        @GitHubPayload("issue_comment.created") GHEventPayload.IssueComment created,
        @GitHubPayload("issue_comment.deleted") GHEventPayload.IssueComment deleted
    ) throws Exception {
        // Arrange
        handler.handleEvent(created);
        assertThat(issueCommentRepository.findById(ISSUE_COMMENT_ID)).isPresent();

        // Act
        handler.handleEvent(deleted);

        // Assert
        assertThat(issueCommentRepository.findById(ISSUE_COMMENT_ID)).isEmpty();
        assertThat(issueRepository.findById(created.getIssue().getId())).isPresent();
    }

    @Test
    @DisplayName("should flag pull request backed issues when processing PR comments")
    void createdEventAgainstPullRequestSetsFlag(
        @GitHubPayload("issue_comment.created.pull-request") GHEventPayload.IssueComment payload
    ) throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var comment = issueCommentRepository.findById(payload.getComment().getId()).orElseThrow();
        assertThat(comment.getIssue()).isNotNull();
        assertThat(comment.getIssue().isHasPullRequest()).isTrue();
        assertThat(comment.getIssue().getNumber()).isEqualTo(payload.getIssue().getNumber());
        assertThat(comment.getAuthor().getLogin()).isEqualTo(payload.getComment().getUser().getLogin());
    }
}
