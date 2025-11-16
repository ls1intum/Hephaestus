package de.tum.in.www1.hephaestus.gitprovider.discussion.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import jakarta.transaction.Transactional;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepositoryDiscussion;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Discussion Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
@Transactional
class GitHubDiscussionMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubDiscussionMessageHandler handler;

    @Autowired
    private DiscussionRepository discussionRepository;

    @BeforeEach
    void cleanDatabase() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    void shouldReturnCorrectEventType() {
        assertThat(handler.getHandlerEvent()).isEqualTo(GHEvent.DISCUSSION);
    }

    @Test
    void shouldPersistDiscussionOnCreated(@GitHubPayload("discussion.created") GHEventPayload.Discussion payload)
        throws Exception {
        handler.handleEvent(payload);

        var stored = getDiscussion(payload.getDiscussion());
        assertThat(stored.getTitle()).isEqualTo(payload.getDiscussion().getTitle());
        assertThat(stored.getState()).isEqualTo(Discussion.State.OPEN);
        assertThat(stored.getAuthor()).isNotNull();
        assertThat(stored.getAuthor().getId()).isEqualTo(payload.getDiscussion().getUser().getId());
        assertThat(stored.getRepository()).isNotNull();
        assertThat(stored.getRepository().getId()).isEqualTo(payload.getRepository().getId());
        assertThat(stored.getCategory()).isNotNull();
        assertThat(stored.getCategory().getSlug()).isEqualTo(payload.getDiscussion().getCategory().getSlug());
        assertThat(stored.getHtmlUrl()).isEqualTo(payload.getDiscussion().getHtmlUrl().toString());
        assertThat(stored.getCommentCount()).isEqualTo(payload.getDiscussion().getComments());
    }

    @Test
    void shouldUpdateAnswerMetadata(
        @GitHubPayload("discussion.created") GHEventPayload.Discussion created,
        @GitHubPayload("discussion.answered") GHEventPayload.Discussion answered
    ) throws Exception {
        handler.handleEvent(created);
        handler.handleEvent(answered);

        var stored = getDiscussion(answered.getDiscussion());
        assertThat(stored.getAnswerChosenAt()).isEqualTo(answered.getDiscussion().getAnswerChosenAt());
        assertThat(stored.getAnswerChosenBy()).isNotNull();
        assertThat(stored.getAnswerChosenBy().getId()).isEqualTo(answered.getDiscussion().getAnswerChosenBy().getId());
        assertThat(stored.getState()).isEqualTo(Discussion.State.ANSWERED);
    }

    @Test
    void shouldDeleteDiscussionOnDeleted(@GitHubPayload("discussion.deleted") GHEventPayload.Discussion deleted)
        throws Exception {
        persistPlaceholderDiscussion(deleted.getDiscussion());

        handler.handleEvent(deleted);

        assertThat(discussionRepository.findById(deleted.getDiscussion().getId())).isEmpty();
    }

    private Discussion getDiscussion(GHRepositoryDiscussion ghDiscussion) {
        return discussionRepository
            .findById(ghDiscussion.getId())
            .orElseThrow(() -> new AssertionError("Discussion " + ghDiscussion.getId() + " not persisted"));
    }

    private void persistPlaceholderDiscussion(GHRepositoryDiscussion ghDiscussion) {
        Discussion discussion = new Discussion();
        discussion.setId(ghDiscussion.getId());
        discussion.setNumber(ghDiscussion.getNumber());
        discussion.setTitle(ghDiscussion.getTitle());
        discussion.setState(Discussion.State.OPEN);
        discussion.setCreatedAt(Instant.now());
        discussion.setUpdatedAt(Instant.now());
        discussionRepository.save(discussion);
    }
}
