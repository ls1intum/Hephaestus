package de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github;

import static org.assertj.core.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionComment;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto.GitHubDiscussionCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubDiscussionCommentProcessor.
 * <p>
 * Tests the processor independently from the webhook handler to verify:
 * - Comment upsert logic (create vs update)
 * - Domain event publishing (DiscussionCommentCreated, DiscussionCommentEdited, DiscussionCommentDeleted)
 * - Context handling and workspace association
 * - Reply threading via parent comment resolution
 */
@DisplayName("GitHub Discussion Comment Processor")
@Import(GitHubDiscussionCommentProcessorIntegrationTest.TestCommentEventListener.class)
class GitHubDiscussionCommentProcessorIntegrationTest extends BaseIntegrationTest {

    private static final Long TEST_ORG_ID = 215361191L;
    private static final Long TEST_REPO_ID = 998279771L;
    private static final Long TEST_DISCUSSION_ID = 9096662L;
    private static final Long TEST_COMMENT_ID = 14848457L;
    private static final String TEST_ORG_LOGIN = "HephaestusTest";
    private static final String TEST_REPO_FULL_NAME = "HephaestusTest/TestRepository";

    @Autowired
    private GitHubDiscussionCommentProcessor processor;

    @Autowired
    private DiscussionCommentRepository commentRepository;

    @Autowired
    private DiscussionRepository discussionRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestCommentEventListener eventListener;

    private Repository testRepository;
    private Workspace testWorkspace;
    private Discussion testDiscussion;
    private GitProvider githubProvider;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    private void setupTestData() {
        // Create GitHub provider
        githubProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));

        // Create organization
        Organization org = new Organization();
        org.setNativeId(TEST_ORG_ID);
        org.setLogin(TEST_ORG_LOGIN);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/" + TEST_ORG_ID);
        org.setHtmlUrl("https://github.com/" + TEST_ORG_LOGIN);
        org.setProvider(githubProvider);
        org = organizationRepository.save(org);

        // Create repository
        testRepository = new Repository();
        testRepository.setNativeId(TEST_REPO_ID);
        testRepository.setName("TestRepository");
        testRepository.setNameWithOwner(TEST_REPO_FULL_NAME);
        testRepository.setHtmlUrl("https://github.com/" + TEST_REPO_FULL_NAME);
        testRepository.setVisibility(Repository.Visibility.PUBLIC);
        testRepository.setDefaultBranch("main");
        testRepository.setCreatedAt(Instant.now());
        testRepository.setUpdatedAt(Instant.now());
        testRepository.setPushedAt(Instant.now());
        testRepository.setOrganization(org);
        testRepository.setProvider(githubProvider);
        testRepository = repositoryRepository.save(testRepository);

        // Create workspace
        testWorkspace = new Workspace();
        testWorkspace.setWorkspaceSlug("test-workspace");
        testWorkspace.setDisplayName("Test Workspace");
        testWorkspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        testWorkspace.setIsPubliclyViewable(true);
        testWorkspace.setOrganization(org);
        testWorkspace.setAccountLogin(TEST_ORG_LOGIN);
        testWorkspace.setAccountType(AccountType.ORG);
        testWorkspace = workspaceRepository.save(testWorkspace);

        // Create discussion
        testDiscussion = new Discussion();
        testDiscussion.setNativeId(TEST_DISCUSSION_ID);
        testDiscussion.setNumber(27);
        testDiscussion.setTitle("Test Discussion");
        testDiscussion.setState(Discussion.State.OPEN);
        testDiscussion.setHtmlUrl("https://github.com/" + TEST_REPO_FULL_NAME + "/discussions/27");
        testDiscussion.setCreatedAt(Instant.now());
        testDiscussion.setUpdatedAt(Instant.now());
        testDiscussion.setRepository(testRepository);
        testDiscussion.setProvider(githubProvider);
        testDiscussion = discussionRepository.save(testDiscussion);
    }

    private ProcessingContext createContext() {
        return ProcessingContext.forSync(testWorkspace.getId(), testRepository);
    }

    private GitHubDiscussionCommentDTO createCommentDTO(Long id, String body) {
        return new GitHubDiscussionCommentDTO(
            id,
            id,
            "node_id_" + id,
            body,
            "https://github.com/" + TEST_REPO_FULL_NAME + "/discussions/27#discussioncomment-" + id,
            false,
            false,
            null,
            "OWNER",
            Instant.now(),
            Instant.now(),
            new GitHubUserDTO(
                100L,
                100L,
                "test-user",
                "https://avatars.githubusercontent.com/u/100",
                "https://github.com/test-user",
                "Test User",
                null
            ),
            null
        );
    }

    @Nested
    @DisplayName("Process Method - Create")
    class ProcessMethodCreate {

        @Test
        @DisplayName("should create comment and publish DiscussionCommentCreated event")
        void shouldCreateCommentAndPublishCreatedEvent() {
            GitHubDiscussionCommentDTO dto = createCommentDTO(TEST_COMMENT_ID, "This is a test comment.");
            ProcessingContext context = createContext();

            DiscussionComment result = processor.process(dto, testDiscussion, context);

            assertThat(result).isNotNull();
            assertThat(result.getNativeId()).isEqualTo(TEST_COMMENT_ID);
            assertThat(result.getBody()).isEqualTo("This is a test comment.");
            assertThat(result.getDiscussion().getId()).isEqualTo(testDiscussion.getId());

            // Verify DiscussionCommentCreated event was published
            assertThat(eventListener.getCreatedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.comment().id()).isEqualTo(result.getId());
                    assertThat(event.discussionId()).isEqualTo(testDiscussion.getId());
                    assertThat(event.context().scopeId()).isEqualTo(testWorkspace.getId());
                });
        }

        @Test
        @DisplayName("should handle null DTO databaseId gracefully")
        void shouldHandleNullDtoGracefully() {
            GitHubDiscussionCommentDTO dto = new GitHubDiscussionCommentDTO(
                null,
                null,
                "node_id",
                "body",
                "https://github.com/test",
                false,
                false,
                null,
                "NONE",
                Instant.now(),
                Instant.now(),
                null,
                null
            );

            DiscussionComment result = processor.process(dto, testDiscussion, createContext());

            assertThat(result).isNull();
            assertThat(eventListener.getCreatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("should handle null author")
        void shouldHandleNullAuthor() {
            GitHubDiscussionCommentDTO dto = new GitHubDiscussionCommentDTO(
                TEST_COMMENT_ID,
                TEST_COMMENT_ID,
                "node_id_" + TEST_COMMENT_ID,
                "Comment without author",
                "https://github.com/" + TEST_REPO_FULL_NAME + "/discussions/27#discussioncomment-" + TEST_COMMENT_ID,
                false,
                false,
                null,
                "NONE",
                Instant.now(),
                Instant.now(),
                null,
                null
            );
            ProcessingContext context = createContext();

            DiscussionComment result = processor.process(dto, testDiscussion, context);

            assertThat(result).isNotNull();
            assertThat(result.getNativeId()).isEqualTo(TEST_COMMENT_ID);
            assertThat(result.getAuthor()).isNull();
            assertThat(eventListener.getCreatedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("should set discussion relationship")
        void shouldSetDiscussionRelationship() {
            GitHubDiscussionCommentDTO dto = createCommentDTO(TEST_COMMENT_ID, "Test body");
            ProcessingContext context = createContext();

            DiscussionComment result = processor.process(dto, testDiscussion, context);

            assertThat(result).isNotNull();
            assertThat(result.getDiscussion()).isNotNull();
            assertThat(result.getDiscussion().getNativeId()).isEqualTo(TEST_DISCUSSION_ID);
            assertThat(result.getDiscussion().getTitle()).isEqualTo("Test Discussion");
        }

        @Test
        @DisplayName("should set author association")
        void shouldSetAuthorAssociation() {
            GitHubDiscussionCommentDTO dto = createCommentDTO(TEST_COMMENT_ID, "Test body");
            ProcessingContext context = createContext();

            DiscussionComment result = processor.process(dto, testDiscussion, context);

            assertThat(result).isNotNull();
            assertThat(result.getAuthorAssociation()).isEqualTo(AuthorAssociation.OWNER);
        }
    }

    @Nested
    @DisplayName("Process Method - Update")
    class ProcessMethodUpdate {

        @Test
        @DisplayName("should update comment and publish DiscussionCommentEdited event when body changes")
        void shouldUpdateCommentAndPublishEditedEventWhenBodyChanges() {
            // Create initial comment
            DiscussionComment existing = new DiscussionComment();
            existing.setNativeId(TEST_COMMENT_ID);
            existing.setProvider(githubProvider);
            existing.setBody("Original body");
            existing.setHtmlUrl(
                "https://github.com/" + TEST_REPO_FULL_NAME + "/discussions/27#discussioncomment-" + TEST_COMMENT_ID
            );
            existing.setCreatedAt(Instant.now());
            existing.setDiscussion(testDiscussion);
            commentRepository.save(existing);

            // Update with new body
            GitHubDiscussionCommentDTO dto = createCommentDTO(TEST_COMMENT_ID, "Updated body");
            ProcessingContext context = createContext();

            DiscussionComment result = processor.process(dto, testDiscussion, context);

            assertThat(result).isNotNull();
            assertThat(result.getBody()).isEqualTo("Updated body");

            // Verify DiscussionCommentEdited event was published
            assertThat(eventListener.getEditedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.comment().id()).isEqualTo(result.getId());
                    assertThat(event.changedFields()).contains("body");
                    assertThat(event.discussionId()).isEqualTo(testDiscussion.getId());
                });
        }

        @Test
        @DisplayName("should update answer status and publish DiscussionCommentEdited event")
        void shouldUpdateAnswerStatusAndPublishEditedEvent() {
            // Create initial comment that is not an answer
            DiscussionComment existing = new DiscussionComment();
            existing.setNativeId(TEST_COMMENT_ID);
            existing.setProvider(githubProvider);
            existing.setBody("Answer body");
            existing.setHtmlUrl(
                "https://github.com/" + TEST_REPO_FULL_NAME + "/discussions/27#discussioncomment-" + TEST_COMMENT_ID
            );
            existing.setAnswer(false);
            existing.setCreatedAt(Instant.now());
            existing.setDiscussion(testDiscussion);
            commentRepository.save(existing);

            // Update with isAnswer=true
            GitHubDiscussionCommentDTO dto = new GitHubDiscussionCommentDTO(
                TEST_COMMENT_ID,
                TEST_COMMENT_ID,
                "node_id_" + TEST_COMMENT_ID,
                "Answer body",
                "https://github.com/" + TEST_REPO_FULL_NAME + "/discussions/27#discussioncomment-" + TEST_COMMENT_ID,
                true,
                false,
                null,
                "OWNER",
                Instant.now(),
                Instant.now(),
                new GitHubUserDTO(
                    100L,
                    100L,
                    "test-user",
                    "https://avatars.githubusercontent.com/u/100",
                    "https://github.com/test-user",
                    "Test User",
                    null
                ),
                null
            );
            ProcessingContext context = createContext();

            DiscussionComment result = processor.process(dto, testDiscussion, context);

            assertThat(result).isNotNull();
            assertThat(result.isAnswer()).isTrue();

            // Verify DiscussionCommentEdited event was published with isAnswer change
            assertThat(eventListener.getEditedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.changedFields()).contains("isAnswer");
                    assertThat(event.discussionId()).isEqualTo(testDiscussion.getId());
                });
        }

        @Test
        @DisplayName("should not publish DiscussionCommentEdited event when nothing changes")
        void shouldNotPublishEditedEventWhenNothingChanges() {
            // Create initial comment with matching data
            GitHubDiscussionCommentDTO dto = createCommentDTO(TEST_COMMENT_ID, "Same body");
            ProcessingContext context = createContext();

            // First process creates the comment
            processor.process(dto, testDiscussion, context);
            eventListener.clear();

            // Process again with the same data
            processor.process(dto, testDiscussion, context);

            // No edited events should be published for unchanged data
            assertThat(eventListener.getCreatedEvents()).isEmpty();
            assertThat(eventListener.getEditedEvents()).isEmpty();
        }

        @Test
        @DisplayName("should be idempotent")
        void shouldBeIdempotent() {
            GitHubDiscussionCommentDTO dto = createCommentDTO(TEST_COMMENT_ID, "Idempotent body");
            ProcessingContext context = createContext();

            // Process multiple times
            DiscussionComment result1 = processor.process(dto, testDiscussion, context);
            DiscussionComment result2 = processor.process(dto, testDiscussion, context);
            DiscussionComment result3 = processor.process(dto, testDiscussion, context);

            // All results should have the same ID and body
            assertThat(result1.getNativeId()).isEqualTo(TEST_COMMENT_ID);
            assertThat(result2.getNativeId()).isEqualTo(TEST_COMMENT_ID);
            assertThat(result3.getNativeId()).isEqualTo(TEST_COMMENT_ID);
            assertThat(result1.getBody()).isEqualTo("Idempotent body");
            assertThat(result2.getBody()).isEqualTo("Idempotent body");
            assertThat(result3.getBody()).isEqualTo("Idempotent body");

            // Only one record should exist in the database
            assertThat(commentRepository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Delete Method")
    class DeleteMethod {

        @Test
        @DisplayName("should delete comment and publish DiscussionCommentDeleted event")
        void shouldDeleteCommentAndPublishDeletedEvent() {
            // Create comment to delete
            GitHubDiscussionCommentDTO dto = createCommentDTO(TEST_COMMENT_ID, "To be deleted");
            processor.process(dto, testDiscussion, createContext());
            eventListener.clear();

            // Delete the comment
            processor.processDeleted(dto, createContext());

            // Verify comment is deleted
            assertThat(commentRepository.findByNativeIdAndProviderId(TEST_COMMENT_ID, githubProvider.getId())).isEmpty();

            // Verify DiscussionCommentDeleted event was published
            assertThat(eventListener.getDeletedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.commentId()).isEqualTo(TEST_COMMENT_ID);
                    assertThat(event.discussionId()).isEqualTo(testDiscussion.getId());
                });
        }

        @Test
        @Transactional
        @DisplayName("should sync bidirectional relationship when deleting")
        void shouldSyncBidirectionalRelationshipWhenDeleting() {
            // Create comment with bidirectional relationship set up
            DiscussionComment existing = new DiscussionComment();
            existing.setNativeId(TEST_COMMENT_ID);
            existing.setProvider(githubProvider);
            existing.setBody("Comment to test bidirectional sync");
            existing.setHtmlUrl(
                "https://github.com/" + TEST_REPO_FULL_NAME + "/discussions/27#discussioncomment-" + TEST_COMMENT_ID
            );
            existing.setCreatedAt(Instant.now());
            existing.setDiscussion(testDiscussion);
            commentRepository.save(existing);

            // Load the discussion and initialize its comments collection (simulates
            // real-world scenario where parent is in persistence context)
            Discussion loadedDiscussion = discussionRepository.findById(testDiscussion.getId()).orElseThrow();
            loadedDiscussion.getComments().size(); // Force lazy initialization

            // Delete should work without TransientObjectException
            // because the processor syncs bidirectional relationship
            GitHubDiscussionCommentDTO dto = createCommentDTO(TEST_COMMENT_ID, "Comment to test bidirectional sync");
            assertThatCode(() -> processor.processDeleted(dto, createContext())).doesNotThrowAnyException();

            // Verify comment is deleted
            assertThat(commentRepository.findByNativeIdAndProviderId(TEST_COMMENT_ID, githubProvider.getId())).isEmpty();
        }

        @Test
        @DisplayName("should handle null comment ID gracefully")
        void shouldHandleNullCommentIdGracefully() {
            GitHubDiscussionCommentDTO dto = new GitHubDiscussionCommentDTO(
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null
            );

            processor.processDeleted(dto, createContext());

            assertThat(eventListener.getDeletedEvents()).isEmpty();
        }

        @Test
        @DisplayName("should handle non-existent comment gracefully")
        void shouldHandleNonExistentCommentGracefully() {
            GitHubDiscussionCommentDTO dto = createCommentDTO(999999L, "Non-existent");

            processor.processDeleted(dto, createContext());

            // No event should be published for non-existent comment
            assertThat(eventListener.getDeletedEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Reply Threading")
    class ReplyThreading {

        @Test
        @DisplayName("should resolve parent comment")
        void shouldResolveParentComment() {
            // Create parent comment
            Long parentId = TEST_COMMENT_ID;
            GitHubDiscussionCommentDTO parentDto = createCommentDTO(parentId, "Parent comment");
            DiscussionComment parent = processor.process(parentDto, testDiscussion, createContext());

            // Create reply comment
            Long replyId = TEST_COMMENT_ID + 1;
            GitHubDiscussionCommentDTO replyDto = new GitHubDiscussionCommentDTO(
                replyId,
                replyId,
                "node_id_" + replyId,
                "Reply comment",
                "https://github.com/" + TEST_REPO_FULL_NAME + "/discussions/27#discussioncomment-" + replyId,
                false,
                false,
                null,
                "NONE",
                Instant.now(),
                Instant.now(),
                new GitHubUserDTO(
                    101L,
                    101L,
                    "reply-user",
                    "https://avatars.githubusercontent.com/u/101",
                    "https://github.com/reply-user",
                    "Reply User",
                    null
                ),
                "node_id_" + parentId
            );
            DiscussionComment reply = processor.process(replyDto, testDiscussion, createContext());

            // Resolve parent
            processor.resolveParentComment(reply, parent);

            // Verify parent was set
            DiscussionComment savedReply = commentRepository.findByNativeIdAndProviderId(replyId, githubProvider.getId()).orElseThrow();
            assertThat(savedReply.getParentComment()).isNotNull();
            assertThat(savedReply.getParentComment().getNativeId()).isEqualTo(parentId);
        }
    }

    /**
     * Test event listener that captures discussion comment events.
     */
    @Component
    static class TestCommentEventListener {

        private final List<DomainEvent.DiscussionCommentCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.DiscussionCommentEdited> editedEvents = new ArrayList<>();
        private final List<DomainEvent.DiscussionCommentDeleted> deletedEvents = new ArrayList<>();

        @EventListener
        public void onCreated(DomainEvent.DiscussionCommentCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onEdited(DomainEvent.DiscussionCommentEdited event) {
            editedEvents.add(event);
        }

        @EventListener
        public void onDeleted(DomainEvent.DiscussionCommentDeleted event) {
            deletedEvents.add(event);
        }

        public List<DomainEvent.DiscussionCommentCreated> getCreatedEvents() {
            return createdEvents;
        }

        public List<DomainEvent.DiscussionCommentEdited> getEditedEvents() {
            return editedEvents;
        }

        public List<DomainEvent.DiscussionCommentDeleted> getDeletedEvents() {
            return deletedEvents;
        }

        public void clear() {
            createdEvents.clear();
            editedEvents.clear();
            deletedEvents.clear();
        }
    }
}
