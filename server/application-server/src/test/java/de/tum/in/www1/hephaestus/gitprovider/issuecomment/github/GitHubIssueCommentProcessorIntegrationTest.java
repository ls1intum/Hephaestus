package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import static org.assertj.core.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO.GitHubCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
    private PullRequestRepository pullRequestRepository;

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
        org.setProviderId(TEST_ORG_ID);
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

            IssueComment result = processor.process(dto, testIssue.getNumber(), context);

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
            IssueComment result = processor.process(null, testIssue.getNumber(), createContext());

            assertThat(result).isNull();
            assertThat(eventListener.getCreatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("should handle missing issue gracefully")
        void shouldHandleMissingIssue() {
            GitHubCommentDTO dto = createCommentDTO(TEST_COMMENT_ID, "Test");

            IssueComment result = processor.process(dto, 999999, createContext());

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

            IssueComment result = processor.process(dto, testIssue.getNumber(), context);

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

            processor.process(dto, testIssue.getNumber(), createContext());

            // No events should be published for unchanged data
            assertThat(eventListener.getCreatedEvents()).isEmpty();
            assertThat(eventListener.getUpdatedEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Process With Parent Creation - Solves Message Ordering")
    class ProcessWithParentCreation {

        private static final Long NEW_ISSUE_ID = 555555555L;
        private static final Long NEW_PR_ID = 666666666L;

        private GitHubIssueDTO createIssueDTOForNewIssue(Long issueId, boolean isPullRequest) {
            return new GitHubIssueDTO(
                issueId,
                issueId,
                "node_id_" + issueId,
                100,
                "New Issue from Comment Webhook",
                "Issue body",
                "open",
                null,
                "https://github.com/" + TEST_REPO_FULL_NAME + "/issues/100",
                0,
                Instant.now(),
                Instant.now(),
                null,
                false,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                null,
                null,
                isPullRequest
                    ? new GitHubIssueDTO.PullRequestRef(
                          "https://api.github.com/repos/test/pulls/100",
                          "https://github.com/test/pull/100"
                      )
                    : null
            );
        }

        @Test
        @DisplayName("should create Issue stub when parent does not exist")
        void shouldCreateIssueStubWhenParentDoesNotExist() {
            GitHubCommentDTO commentDto = createCommentDTO(TEST_COMMENT_ID, "Comment on new issue");
            GitHubIssueDTO issueDto = createIssueDTOForNewIssue(NEW_ISSUE_ID, false);
            ProcessingContext context = createContext();

            // Verify issue does not exist
            assertThat(issueRepository.existsById(NEW_ISSUE_ID)).isFalse();

            IssueComment result = processor.processWithParentCreation(commentDto, issueDto, context);

            // Verify comment was created
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TEST_COMMENT_ID);
            assertThat(result.getBody()).isEqualTo("Comment on new issue");

            // Verify Issue stub was created
            assertThat(issueRepository.existsById(NEW_ISSUE_ID)).isTrue();
            Issue createdIssue = issueRepository.findById(NEW_ISSUE_ID).orElseThrow();
            assertThat(createdIssue.getNumber()).isEqualTo(100);
            assertThat(createdIssue.getTitle()).isEqualTo("New Issue from Comment Webhook");
            assertThat(createdIssue.isPullRequest()).isFalse();
            assertThat(createdIssue.getRepository().getId()).isEqualTo(TEST_REPO_ID);

            // Verify comment is linked to the new issue
            assertThat(result.getIssue().getId()).isEqualTo(NEW_ISSUE_ID);
        }

        @Test
        @DisplayName("should create PullRequest stub when parent is PR and does not exist")
        void shouldCreatePullRequestStubWhenParentIsPRAndDoesNotExist() {
            GitHubCommentDTO commentDto = createCommentDTO(TEST_COMMENT_ID, "Comment on new PR");
            GitHubIssueDTO issueDto = createIssueDTOForNewIssue(NEW_PR_ID, true);
            ProcessingContext context = createContext();

            // Verify PR does not exist
            assertThat(pullRequestRepository.existsById(NEW_PR_ID)).isFalse();

            IssueComment result = processor.processWithParentCreation(commentDto, issueDto, context);

            // Verify comment was created
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TEST_COMMENT_ID);

            // Verify PullRequest stub was created (not Issue!)
            assertThat(pullRequestRepository.existsById(NEW_PR_ID)).isTrue();
            PullRequest createdPR = pullRequestRepository.findById(NEW_PR_ID).orElseThrow();
            assertThat(createdPR.getNumber()).isEqualTo(100);
            assertThat(createdPR.getTitle()).isEqualTo("New Issue from Comment Webhook");
            assertThat(createdPR.isPullRequest()).isTrue();

            // Verify comment is linked to the new PR (via Issue base class)
            assertThat(result.getIssue().getId()).isEqualTo(NEW_PR_ID);
        }

        @Test
        @DisplayName("should use existing parent when it already exists")
        void shouldUseExistingParentWhenItAlreadyExists() {
            GitHubCommentDTO commentDto = createCommentDTO(TEST_COMMENT_ID, "Comment on existing issue");
            GitHubIssueDTO issueDto = createIssueDTOForNewIssue(testIssue.getId(), false);
            ProcessingContext context = createContext();

            // Get initial issue count
            long initialCount = issueRepository.count();

            IssueComment result = processor.processWithParentCreation(commentDto, issueDto, context);

            // Verify comment was created
            assertThat(result).isNotNull();
            assertThat(result.getIssue().getId()).isEqualTo(testIssue.getId());

            // Verify no new issue was created
            assertThat(issueRepository.count()).isEqualTo(initialCount);
        }

        @Test
        @DisplayName("should handle missing issue ID gracefully")
        void shouldHandleMissingIssueIdGracefully() {
            GitHubCommentDTO commentDto = createCommentDTO(TEST_COMMENT_ID, "Comment");
            GitHubIssueDTO issueDto = new GitHubIssueDTO(
                null,
                null,
                null,
                100,
                "Title",
                null,
                "open",
                null,
                null,
                0,
                Instant.now(),
                Instant.now(),
                null,
                false,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                null,
                null,
                null
            );
            ProcessingContext context = createContext();

            IssueComment result = processor.processWithParentCreation(commentDto, issueDto, context);

            assertThat(result).isNull();
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
        @Transactional
        @DisplayName("should sync bidirectional relationship with parent issue when deleting")
        void shouldSyncBidirectionalRelationshipWhenDeleting() {
            // Create comment with bidirectional relationship set up
            IssueComment existing = new IssueComment();
            existing.setId(TEST_COMMENT_ID);
            existing.setBody("Comment to test bidirectional sync");
            existing.setHtmlUrl("https://github.com/test");
            existing.setCreatedAt(Instant.now());
            existing.setIssue(testIssue);
            commentRepository.save(existing);

            // Load the issue and initialize its comments collection (simulates
            // real-world scenario where parent is in persistence context)
            Issue loadedIssue = issueRepository.findById(testIssue.getId()).orElseThrow();
            loadedIssue.getComments().size(); // Force lazy initialization

            // Delete should work without TransientObjectException
            // because the processor syncs bidirectional relationship
            assertThatCode(() -> processor.delete(TEST_COMMENT_ID, createContext())).doesNotThrowAnyException();

            // Verify comment is deleted
            assertThat(commentRepository.existsById(TEST_COMMENT_ID)).isFalse();
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
