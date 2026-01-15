package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import static org.assertj.core.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO.GitHubCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubIssueCommentProcessor.
 * <p>
 * Tests the processor independently from the webhook handler to verify:
 * - Comment upsert logic (create vs update)
 * - Domain event publishing (CommentCreated, CommentUpdated, CommentDeleted)
 * - Context handling and workspace association
 */
@DisplayName("GitHub Issue Comment Processor")
@Transactional
class GitHubIssueCommentProcessorIntegrationTest extends BaseIntegrationTest {

    private static final Long TEST_ORG_ID = 215361191L;
    private static final Long TEST_REPO_ID = 998279771L;
    private static final Long TEST_ISSUE_ID = 123456789L;
    private static final Long TEST_COMMENT_ID = 987654321L;
    private static final String TEST_ORG_LOGIN = "HephaestusTest";
    private static final String TEST_REPO_FULL_NAME = "HephaestusTest/TestRepository";

    @Autowired
    private GitHubIssueCommentProcessor processor;

    @Autowired
    private IssueCommentRepository commentRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private TestCommentEventListener eventListener;

    private Repository testRepository;
    private Workspace testWorkspace;
    private Issue testIssue;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    private void setupTestData() {
        // Create organization
        Organization org = new Organization();
        org.setId(TEST_ORG_ID);
        org.setGithubId(TEST_ORG_ID);
        org.setLogin(TEST_ORG_LOGIN);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/" + TEST_ORG_ID);
        org = organizationRepository.save(org);

        // Create repository
        testRepository = new Repository();
        testRepository.setId(TEST_REPO_ID);
        testRepository.setName("TestRepository");
        testRepository.setNameWithOwner(TEST_REPO_FULL_NAME);
        testRepository.setHtmlUrl("https://github.com/" + TEST_REPO_FULL_NAME);
        testRepository.setVisibility(Repository.Visibility.PUBLIC);
        testRepository.setDefaultBranch("main");
        testRepository.setCreatedAt(Instant.now());
        testRepository.setUpdatedAt(Instant.now());
        testRepository.setPushedAt(Instant.now());
        testRepository.setOrganization(org);
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

        // Create issue
        testIssue = new Issue();
        testIssue.setId(TEST_ISSUE_ID);
        testIssue.setNumber(42);
        testIssue.setTitle("Test Issue");
        testIssue.setState(Issue.State.OPEN);
        testIssue.setHtmlUrl("https://github.com/" + TEST_REPO_FULL_NAME + "/issues/42");
        testIssue.setCreatedAt(Instant.now());
        testIssue.setUpdatedAt(Instant.now());
        testIssue.setRepository(testRepository);
        testIssue = issueRepository.save(testIssue);
    }

    private ProcessingContext createContext() {
        return ProcessingContext.forSync(testWorkspace.getId(), testRepository);
    }

    private GitHubCommentDTO createCommentDTO(Long id, String body) {
        return new GitHubCommentDTO(
            id,
            "node_id_" + id,
            "https://github.com/" + TEST_REPO_FULL_NAME + "/issues/42#issuecomment-" + id,
            body,
            null,
            "OWNER",
            Instant.now(),
            Instant.now()
        );
    }

    @Nested
    @DisplayName("Process Method - Create")
    class ProcessMethodCreate {

        @Test
        @DisplayName("should create comment and publish CommentCreated event")
        void shouldCreateCommentAndPublishEvent() {
            GitHubCommentDTO dto = createCommentDTO(TEST_COMMENT_ID, "This is a test comment.");
            ProcessingContext context = createContext();

            IssueComment result = processor.process(dto, testIssue.getId(), context);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TEST_COMMENT_ID);
            assertThat(result.getBody()).isEqualTo("This is a test comment.");
            assertThat(result.getIssue().getId()).isEqualTo(testIssue.getId());

            // Verify CommentCreated event was published
            assertThat(eventListener.getCreatedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.comment().id()).isEqualTo(TEST_COMMENT_ID);
                    assertThat(event.issueId()).isEqualTo(testIssue.getId());
                    assertThat(event.context().scopeId()).isEqualTo(testWorkspace.getId());
                });
        }

        @Test
        @DisplayName("should handle null DTO gracefully")
        void shouldHandleNullDTO() {
            IssueComment result = processor.process(null, testIssue.getId(), createContext());

            assertThat(result).isNull();
            assertThat(eventListener.getCreatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("should handle missing issue gracefully")
        void shouldHandleMissingIssue() {
            GitHubCommentDTO dto = createCommentDTO(TEST_COMMENT_ID, "Test");

            IssueComment result = processor.process(dto, 999999L, createContext());

            assertThat(result).isNull();
            assertThat(eventListener.getCreatedEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Process Method - Update")
    class ProcessMethodUpdate {

        @Test
        @DisplayName("should update comment and publish CommentUpdated event when body changes")
        void shouldUpdateCommentAndPublishEvent() {
            // Create initial comment
            IssueComment existing = new IssueComment();
            existing.setId(TEST_COMMENT_ID);
            existing.setBody("Original body");
            existing.setHtmlUrl("https://github.com/test");
            existing.setCreatedAt(Instant.now());
            existing.setIssue(testIssue);
            commentRepository.save(existing);

            // Update with new body
            GitHubCommentDTO dto = createCommentDTO(TEST_COMMENT_ID, "Updated body");
            ProcessingContext context = createContext();

            IssueComment result = processor.process(dto, testIssue.getId(), context);

            assertThat(result).isNotNull();
            assertThat(result.getBody()).isEqualTo("Updated body");

            // Verify CommentUpdated event was published
            assertThat(eventListener.getUpdatedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.comment().id()).isEqualTo(TEST_COMMENT_ID);
                    assertThat(event.changedFields()).contains("body");
                    assertThat(event.issueId()).isEqualTo(testIssue.getId());
                });
        }

        @Test
        @DisplayName("should not publish event when nothing changes")
        void shouldNotPublishEventWhenNothingChanges() {
            // Create initial comment with matching data
            IssueComment existing = new IssueComment();
            existing.setId(TEST_COMMENT_ID);
            existing.setBody("Same body");
            existing.setHtmlUrl(
                "https://github.com/" + TEST_REPO_FULL_NAME + "/issues/42#issuecomment-" + TEST_COMMENT_ID
            );
            existing.setAuthorAssociation(de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation.OWNER);
            existing.setCreatedAt(Instant.now());
            existing.setIssue(testIssue);
            commentRepository.save(existing);

            // Process with same data
            GitHubCommentDTO dto = createCommentDTO(TEST_COMMENT_ID, "Same body");

            processor.process(dto, testIssue.getId(), createContext());

            // No events should be published for unchanged data
            assertThat(eventListener.getCreatedEvents()).isEmpty();
            assertThat(eventListener.getUpdatedEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Delete Method")
    class DeleteMethod {

        @Test
        @DisplayName("should delete comment and publish CommentDeleted event")
        void shouldDeleteCommentAndPublishEvent() {
            // Create comment to delete
            IssueComment existing = new IssueComment();
            existing.setId(TEST_COMMENT_ID);
            existing.setBody("To be deleted");
            existing.setHtmlUrl("https://github.com/test");
            existing.setCreatedAt(Instant.now());
            existing.setIssue(testIssue);
            commentRepository.save(existing);

            processor.delete(TEST_COMMENT_ID, createContext());

            // Verify comment is deleted
            assertThat(commentRepository.existsById(TEST_COMMENT_ID)).isFalse();

            // Verify CommentDeleted event was published
            assertThat(eventListener.getDeletedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.commentId()).isEqualTo(TEST_COMMENT_ID);
                    assertThat(event.issueId()).isEqualTo(testIssue.getId());
                });
        }

        @Test
        @DisplayName("should handle null commentId gracefully")
        void shouldHandleNullCommentId() {
            processor.delete(null, createContext());

            assertThat(eventListener.getDeletedEvents()).isEmpty();
        }

        @Test
        @DisplayName("should handle non-existent comment gracefully")
        void shouldHandleNonExistentComment() {
            processor.delete(999999L, createContext());

            // No event should be published for non-existent comment
            assertThat(eventListener.getDeletedEvents()).isEmpty();
        }
    }

    /**
     * Test event listener that captures comment events.
     */
    @Component
    static class TestCommentEventListener {

        private final List<DomainEvent.CommentCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.CommentUpdated> updatedEvents = new ArrayList<>();
        private final List<DomainEvent.CommentDeleted> deletedEvents = new ArrayList<>();

        @EventListener
        public void onCommentCreated(DomainEvent.CommentCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onCommentUpdated(DomainEvent.CommentUpdated event) {
            updatedEvents.add(event);
        }

        @EventListener
        public void onCommentDeleted(DomainEvent.CommentDeleted event) {
            deletedEvents.add(event);
        }

        public List<DomainEvent.CommentCreated> getCreatedEvents() {
            return createdEvents;
        }

        public List<DomainEvent.CommentUpdated> getUpdatedEvents() {
            return updatedEvents;
        }

        public List<DomainEvent.CommentDeleted> getDeletedEvents() {
            return deletedEvents;
        }

        public void clear() {
            createdEvents.clear();
            updatedEvents.clear();
            deletedEvents.clear();
        }
    }
}
