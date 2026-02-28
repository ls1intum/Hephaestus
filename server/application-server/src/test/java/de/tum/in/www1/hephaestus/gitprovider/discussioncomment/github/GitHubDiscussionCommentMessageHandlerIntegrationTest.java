package de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto.GitHubDiscussionCommentEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for GitHubDiscussionCommentMessageHandler.
 * <p>
 * Tests the full webhook handling flow using JSON fixtures parsed directly
 * into DTOs for complete isolation. The comment handler processes the parent
 * discussion first (via GitHubDiscussionProcessor.process()) before processing
 * the comment itself, so no manual Discussion setup is needed.
 * <p>
 * Note: This test class does NOT use @Transactional because the processing
 * chain calls GitHubUserProcessor.findOrCreate() which uses REQUIRES_NEW propagation.
 * Having @Transactional here would cause connection pool deadlocks.
 */
@DisplayName("GitHub Discussion Comment Message Handler")
@Import(GitHubDiscussionCommentMessageHandlerIntegrationTest.TestEventListener.class)
class GitHubDiscussionCommentMessageHandlerIntegrationTest extends BaseIntegrationTest {

    private static final Long FIXTURE_ORG_ID = 215361191L;
    private static final Long FIXTURE_REPO_ID = 998279771L;
    private static final String FIXTURE_ORG_LOGIN = "HephaestusTest";
    private static final String FIXTURE_REPO_FULL_NAME = "HephaestusTest/TestRepository";

    // Comment fixture values from discussion_comment.created.json
    private static final Long FIXTURE_COMMENT_ID = 14848457L;
    private static final String FIXTURE_COMMENT_BODY = "Here is a helpful answer";
    private static final String FIXTURE_COMMENT_HTML_URL =
        "https://github.com/HephaestusTest/TestRepository/discussions/27#discussioncomment-14848457";
    private static final String FIXTURE_EDITED_COMMENT_BODY = "Edited answer content";

    // Discussion ID embedded in the comment fixtures
    private static final Long FIXTURE_DISCUSSION_ID = 9096662L;

    @Autowired
    private GitHubDiscussionCommentMessageHandler handler;

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
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private TestEventListener eventListener;

    private static final int FIXTURE_DISCUSSION_NUMBER = 27;

    private Repository testRepository;
    private GitProvider gitProvider;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    private void setupTestData() {
        // Create GitHub provider
        gitProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));

        // Create organization
        Organization org = new Organization();
        org.setNativeId(FIXTURE_ORG_ID);
        org.setLogin(FIXTURE_ORG_LOGIN);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/" + FIXTURE_ORG_ID);
        org.setHtmlUrl("https://github.com/" + FIXTURE_ORG_LOGIN);
        org.setProvider(gitProvider);
        org = organizationRepository.save(org);

        // Create repository
        Repository repo = new Repository();
        repo.setNativeId(FIXTURE_REPO_ID);
        repo.setName("TestRepository");
        repo.setNameWithOwner(FIXTURE_REPO_FULL_NAME);
        repo.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME);
        repo.setVisibility(Repository.Visibility.PUBLIC);
        repo.setDefaultBranch("main");
        repo.setCreatedAt(Instant.now());
        repo.setUpdatedAt(Instant.now());
        repo.setPushedAt(Instant.now());
        repo.setOrganization(org);
        repo.setProvider(gitProvider);
        testRepository = repositoryRepository.save(repo);

        // Create workspace
        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug("hephaestus-test");
        workspace.setDisplayName("Hephaestus Test");
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        workspace.setIsPubliclyViewable(true);
        workspace.setOrganization(org);
        workspace.setAccountLogin(FIXTURE_ORG_LOGIN);
        workspace.setAccountType(AccountType.ORG);
        workspaceRepository.save(workspace);
    }

    @Test
    @DisplayName("Should return correct event type")
    void shouldReturnCorrectEventType() {
        assertThat(handler.getEventType()).isEqualTo(GitHubEventType.DISCUSSION_COMMENT);
    }

    @Test
    @DisplayName("Should create comment on created event")
    void shouldCreateCommentOnCreatedEvent() throws Exception {
        // Given
        GitHubDiscussionCommentEventDTO event = loadPayload("discussion_comment.created");

        // Verify comment doesn't exist initially
        assertThat(commentRepository.findByNativeIdAndProviderId(FIXTURE_COMMENT_ID, gitProvider.getId())).isEmpty();

        // When
        handler.handleEvent(event);

        // Then - comment should be persisted with correct fields
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(commentRepository.findByNativeIdAndProviderId(FIXTURE_COMMENT_ID, gitProvider.getId()))
                .isPresent()
                .get()
                .satisfies(comment -> {
                    assertThat(comment.getNativeId()).isEqualTo(FIXTURE_COMMENT_ID);
                    assertThat(comment.getBody()).isEqualTo(FIXTURE_COMMENT_BODY);
                    assertThat(comment.getHtmlUrl()).isEqualTo(FIXTURE_COMMENT_HTML_URL);
                    assertThat(comment.getDiscussion()).isNotNull();
                    assertThat(comment.getDiscussion().getNativeId()).isEqualTo(FIXTURE_DISCUSSION_ID);
                    assertThat(comment.getAuthor()).isNotNull();
                });
        });

        // Parent discussion should also have been created by the handler
        assertThat(discussionRepository.findByRepositoryIdAndNumber(testRepository.getId(), FIXTURE_DISCUSSION_NUMBER)).isPresent();

        // Domain event published
        assertThat(eventListener.getCreatedEvents()).hasSize(1);
    }

    @Test
    @DisplayName("Should update comment on edited event")
    void shouldUpdateCommentOnEditedEvent() throws Exception {
        // Given - first create the comment
        GitHubDiscussionCommentEventDTO createEvent = loadPayload("discussion_comment.created");
        handler.handleEvent(createEvent);
        eventListener.clear();

        // Load edited event
        GitHubDiscussionCommentEventDTO editEvent = loadPayload("discussion_comment.edited");

        // When
        handler.handleEvent(editEvent);

        // Then
        assertThat(commentRepository.findByNativeIdAndProviderId(FIXTURE_COMMENT_ID, gitProvider.getId()))
            .isPresent()
            .get()
            .satisfies(comment -> {
                assertThat(comment.getBody()).isEqualTo(FIXTURE_EDITED_COMMENT_BODY);
            });
    }

    @Test
    @DisplayName("Should delete comment on deleted event")
    void shouldDeleteCommentOnDeletedEvent() throws Exception {
        // Given - first create the comment
        GitHubDiscussionCommentEventDTO createEvent = loadPayload("discussion_comment.created");
        handler.handleEvent(createEvent);

        // Verify it exists
        assertThat(commentRepository.findByNativeIdAndProviderId(FIXTURE_COMMENT_ID, gitProvider.getId())).isPresent();
        eventListener.clear();

        // Load deleted event
        GitHubDiscussionCommentEventDTO deleteEvent = loadPayload("discussion_comment.deleted");

        // When
        handler.handleEvent(deleteEvent);

        // Then
        assertThat(commentRepository.findByNativeIdAndProviderId(FIXTURE_COMMENT_ID, gitProvider.getId())).isEmpty();

        // Domain event published
        assertThat(eventListener.getDeletedEvents()).hasSize(1);
    }

    private GitHubDiscussionCommentEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubDiscussionCommentEventDTO.class);
    }

    // ==================== Test Event Listener ====================

    @Component
    static class TestEventListener {

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
            return new ArrayList<>(createdEvents);
        }

        public List<DomainEvent.DiscussionCommentEdited> getEditedEvents() {
            return new ArrayList<>(editedEvents);
        }

        public List<DomainEvent.DiscussionCommentDeleted> getDeletedEvents() {
            return new ArrayList<>(deletedEvents);
        }

        public void clear() {
            createdEvents.clear();
            editedEvents.clear();
            deletedEvents.clear();
        }
    }
}
