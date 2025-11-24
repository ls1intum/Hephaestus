package de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionComment;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionCommentRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import jakarta.transaction.Transactional;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepositoryDiscussionComment;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Discussion Comment Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
@Transactional
class GitHubDiscussionCommentMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubDiscussionCommentMessageHandler handler;

    @Autowired
    private DiscussionRepository discussionRepository;

    @Autowired
    private DiscussionCommentRepository commentRepository;

    @BeforeEach
    void cleanDatabase() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    void shouldReturnCorrectEventType() {
        assertThat(handler.getHandlerEvent()).isEqualTo(GHEvent.DISCUSSION_COMMENT);
    }

    @Test
    void shouldPersistCommentOnCreated(
        @GitHubPayload("discussion_comment.created") GHEventPayload.DiscussionComment payload
    ) throws Exception {
        handler.handleEvent(payload);

        var stored = getComment(payload.getComment());
        assertThat(stored.getBody()).isEqualTo(payload.getComment().getBody());
        assertThat(stored.getAuthor()).isNotNull();
        assertThat(stored.getAuthor().getId()).isEqualTo(payload.getComment().getUser().getId());
        assertThat(stored.getDiscussion()).isNotNull();
        assertThat(stored.getDiscussion().getId()).isEqualTo(payload.getDiscussion().getId());
    }

    @Test
    void shouldUpdateBodyOnEdited(
        @GitHubPayload("discussion_comment.created") GHEventPayload.DiscussionComment created,
        @GitHubPayload("discussion_comment.edited") GHEventPayload.DiscussionComment edited
    ) throws Exception {
        handler.handleEvent(created);
        handler.handleEvent(edited);

        var stored = getComment(edited.getComment());
        assertThat(stored.getBody()).isEqualTo(edited.getComment().getBody());
    }

    @Test
    void shouldDeleteOnDeleted(@GitHubPayload("discussion_comment.deleted") GHEventPayload.DiscussionComment deleted)
        throws Exception {
        persistPlaceholderComment(deleted);

        handler.handleEvent(deleted);

        assertThat(commentRepository.findById(deleted.getComment().getId())).isEmpty();
        var discussion = discussionRepository.findById(deleted.getDiscussion().getId()).orElseThrow();
        assertThat(discussion.getAnswerComment()).isNull();
        assertThat(discussion.getAnswerChosenAt()).isNull();
        assertThat(discussion.getAnswerChosenBy()).isNull();
    }

    private DiscussionComment getComment(GHRepositoryDiscussionComment ghComment) {
        return commentRepository
            .findById(ghComment.getId())
            .orElseThrow(() -> new AssertionError("Discussion comment " + ghComment.getId() + " not persisted"));
    }

    private void persistPlaceholderComment(GHEventPayload.DiscussionComment payload) {
        Discussion parent = new Discussion();
        parent.setId(payload.getDiscussion().getId());
        parent.setNumber(payload.getDiscussion().getNumber());
        parent.setTitle(payload.getDiscussion().getTitle());
        parent.setState(Discussion.State.OPEN);
        parent.setCreatedAt(Instant.now());
        parent.setUpdatedAt(Instant.now());
        discussionRepository.save(parent);
        DiscussionComment comment = new DiscussionComment();
        comment.setId(payload.getComment().getId());
        comment.setBody(payload.getComment().getBody() != null ? payload.getComment().getBody() : "placeholder");
        comment.setAuthorAssociation(AuthorAssociation.NONE);
        comment.setDiscussion(parent);
        comment.setCreatedAt(Instant.now());
        comment.setUpdatedAt(Instant.now());
        commentRepository.save(comment);

        parent.setAnswerComment(comment);
        parent.setAnswerChosenAt(Instant.now());
        discussionRepository.save(parent);
    }
}
