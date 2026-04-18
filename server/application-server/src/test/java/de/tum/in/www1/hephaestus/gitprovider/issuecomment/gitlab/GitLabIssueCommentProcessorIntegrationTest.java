package de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for {@link GitLabIssueCommentProcessor#processFromSync} covering Gap 8
 * (GitLab sync audit): {@code issue_comment.updated_at} must persist verbatim and never
 * fall back to {@code created_at}. When a subsequent sync returns a distinct
 * {@code updatedAt}, the processor must fire a {@link DomainEvent.CommentUpdated}
 * carrying {@code "updatedAt"} in {@code changedFields} — downstream profile views rely on
 * this divergence to detect edits.
 */
@DisplayName("GitLab Issue Comment Processor — sync path")
@TestPropertySource(
    properties = {
        "hephaestus.gitlab.enabled=true",
        "hephaestus.gitlab.default-server-url=https://gitlab.lrz.de",
        "hephaestus.gitlab.connect-timeout=30s",
        "hephaestus.gitlab.read-timeout=60s",
        "hephaestus.gitlab.rate-limit-delay=200ms",
        "hephaestus.gitlab.sync-page-delay=5m",
    }
)
@Import(GitLabIssueCommentProcessorIntegrationTest.TestCommentEventListener.class)
class GitLabIssueCommentProcessorIntegrationTest extends BaseIntegrationTest {

    private static final String FIXTURE_ORG_LOGIN = "hephaestustest";
    private static final String FIXTURE_REPO_FULL_NAME = "hephaestustest/demo-repository";

    private static final long NATIVE_NOTE_ID = 4_406_174L;
    private static final String CREATED_AT = "2026-01-31T18:27:14Z";
    private static final String UPDATED_AT_SAME = CREATED_AT;
    private static final String UPDATED_AT_LATER = "2026-02-01T09:10:11Z";
    private static final String UPDATED_AT_LATER_AGAIN = "2026-02-15T12:00:00Z";

    @Autowired
    private GitLabIssueCommentProcessor processor;

    @Autowired
    private IssueCommentRepository commentRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private TestCommentEventListener eventListener;

    private GitProvider gitlabProvider;
    private Repository testRepository;
    private Issue testIssue;
    private Workspace testWorkspace;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    @Test
    @DisplayName("shouldPersistUpdatedAtVerbatimWhenCreatingNewComment")
    void shouldPersistUpdatedAtVerbatimWhenCreatingNewComment() {
        GitLabIssueCommentProcessor.SyncNoteData data = buildData("First comment", CREATED_AT, UPDATED_AT_LATER);

        IssueComment saved = processor.processFromSync(data, testIssue, gitlabProvider.getId(), testWorkspace.getId());

        assertThat(saved).isNotNull();
        Instant expectedCreated = OffsetDateTime.parse(CREATED_AT).toInstant();
        Instant expectedUpdated = OffsetDateTime.parse(UPDATED_AT_LATER).toInstant();
        assertThat(saved.getCreatedAt()).isEqualTo(expectedCreated);
        assertThat(saved.getUpdatedAt()).isEqualTo(expectedUpdated);
        assertThat(saved.getUpdatedAt()).isNotEqualTo(saved.getCreatedAt());
    }

    @Test
    @DisplayName("shouldAddUpdatedAtToChangedFieldsWhenUpdatedAtDivergesOnUpsert")
    void shouldAddUpdatedAtToChangedFieldsWhenUpdatedAtDivergesOnUpsert() {
        // Insert a comment whose updatedAt initially equals createdAt.
        processor.processFromSync(
            buildData("Original body", CREATED_AT, UPDATED_AT_SAME),
            testIssue,
            gitlabProvider.getId(),
            testWorkspace.getId()
        );
        eventListener.clear();

        // Re-sync with a later updatedAt (body unchanged — tests the divergence path in isolation).
        IssueComment saved = processor.processFromSync(
            buildData("Original body", CREATED_AT, UPDATED_AT_LATER),
            testIssue,
            gitlabProvider.getId(),
            testWorkspace.getId()
        );

        assertThat(saved).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(OffsetDateTime.parse(UPDATED_AT_LATER).toInstant());
        assertThat(saved.getCreatedAt()).isEqualTo(OffsetDateTime.parse(CREATED_AT).toInstant());
        assertThat(saved.getUpdatedAt()).isNotEqualTo(saved.getCreatedAt());

        List<DomainEvent.CommentUpdated> updates = eventListener.getUpdatedEvents();
        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).changedFields()).contains("updatedAt");
    }

    @Test
    @DisplayName("shouldNotEmitUpdateEventWhenUpdatedAtUnchangedOnResync")
    void shouldNotEmitUpdateEventWhenUpdatedAtUnchangedOnResync() {
        GitLabIssueCommentProcessor.SyncNoteData first = buildData("Body", CREATED_AT, UPDATED_AT_LATER);
        processor.processFromSync(first, testIssue, gitlabProvider.getId(), testWorkspace.getId());
        eventListener.clear();

        // Re-sync with identical body and identical updatedAt — no-op.
        IssueComment saved = processor.processFromSync(
            buildData("Body", CREATED_AT, UPDATED_AT_LATER),
            testIssue,
            gitlabProvider.getId(),
            testWorkspace.getId()
        );

        assertThat(saved).isNotNull();
        assertThat(eventListener.getUpdatedEvents()).isEmpty();
        assertThat(eventListener.getCreatedEvents()).isEmpty();
    }

    @Test
    @DisplayName("shouldNotFallBackToCreatedAtWhenUpdatedAtIsNull")
    void shouldNotFallBackToCreatedAtWhenUpdatedAtIsNull() {
        // Seed with a real divergent updatedAt so we can prove the null branch does
        // not overwrite the stored value with createdAt.
        processor.processFromSync(
            buildData("Body", CREATED_AT, UPDATED_AT_LATER),
            testIssue,
            gitlabProvider.getId(),
            testWorkspace.getId()
        );
        eventListener.clear();

        IssueComment saved = processor.processFromSync(
            buildData("Body", CREATED_AT, null),
            testIssue,
            gitlabProvider.getId(),
            testWorkspace.getId()
        );

        assertThat(saved).isNotNull();
        // updatedAt must remain the previously persisted value — not silently
        // collapsed to createdAt when the feed omits the field.
        assertThat(saved.getUpdatedAt()).isEqualTo(OffsetDateTime.parse(UPDATED_AT_LATER).toInstant());
        assertThat(saved.getUpdatedAt()).isNotEqualTo(saved.getCreatedAt());
    }

    @Test
    @DisplayName("shouldEmitCommentCreatedWithDistinctUpdatedAtWhenFirstSyncSeesEditedNote")
    void shouldEmitCommentCreatedWithDistinctUpdatedAtWhenFirstSyncSeesEditedNote() {
        // Fresh scheduler run, comment already edited on GitLab. The created event must
        // not lie about updatedAt — the first insert path is responsible for the initial value.
        IssueComment saved = processor.processFromSync(
            buildData("Edited before we saw it", CREATED_AT, UPDATED_AT_LATER_AGAIN),
            testIssue,
            gitlabProvider.getId(),
            testWorkspace.getId()
        );

        assertThat(saved).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(OffsetDateTime.parse(UPDATED_AT_LATER_AGAIN).toInstant());
        assertThat(saved.getCreatedAt()).isEqualTo(OffsetDateTime.parse(CREATED_AT).toInstant());
        assertThat(eventListener.getCreatedEvents()).hasSize(1);
        // First-seen comment must not produce a spurious CommentUpdated event.
        assertThat(eventListener.getUpdatedEvents()).isEmpty();
    }

    // ==================== Helpers ====================

    private GitLabIssueCommentProcessor.SyncNoteData buildData(String body, String createdAt, String updatedAt) {
        return new GitLabIssueCommentProcessor.SyncNoteData(
            NATIVE_NOTE_ID,
            body,
            "https://gitlab.lrz.de/hephaestustest/demo-repository/-/issues/5#note_" + NATIVE_NOTE_ID,
            "gid://gitlab/User/18024",
            "ga84xah",
            "Felix Dietrich",
            "https://gitlab.lrz.de/uploads/-/system/user/avatar/18024/avatar.png",
            "https://gitlab.lrz.de/ga84xah",
            createdAt,
            updatedAt
        );
    }

    private void setupTestData() {
        gitlabProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.lrz.de")
            .orElseGet(() ->
                gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, "https://gitlab.lrz.de"))
            );

        Organization org = new Organization();
        org.setNativeId(1L);
        org.setLogin(FIXTURE_ORG_LOGIN);
        org.setCreatedAt(Instant.parse(CREATED_AT));
        org.setUpdatedAt(Instant.parse(CREATED_AT));
        org.setName("HephaestusTest");
        org.setAvatarUrl("");
        org.setHtmlUrl("https://gitlab.lrz.de/hephaestustest");
        org.setProvider(gitlabProvider);
        org = organizationRepository.save(org);

        Repository repo = new Repository();
        repo.setNativeId(246_765L);
        repo.setName("demo-repository");
        repo.setNameWithOwner(FIXTURE_REPO_FULL_NAME);
        repo.setHtmlUrl("https://gitlab.lrz.de/hephaestustest/demo-repository");
        repo.setVisibility(Repository.Visibility.PRIVATE);
        repo.setDefaultBranch("main");
        repo.setCreatedAt(Instant.parse(CREATED_AT));
        repo.setUpdatedAt(Instant.parse(CREATED_AT));
        repo.setPushedAt(Instant.parse(CREATED_AT));
        repo.setOrganization(org);
        repo.setProvider(gitlabProvider);
        testRepository = repositoryRepository.save(repo);

        Issue issue = new Issue();
        issue.setNativeId(422_296L);
        issue.setProvider(gitlabProvider);
        issue.setNumber(5);
        issue.setTitle("Feature: Add user authentication");
        issue.setBody("Implement OAuth2 authentication flow");
        issue.setState(Issue.State.OPEN);
        issue.setHtmlUrl("https://gitlab.lrz.de/hephaestustest/demo-repository/-/issues/5");
        issue.setCreatedAt(Instant.parse(CREATED_AT));
        issue.setUpdatedAt(Instant.parse(CREATED_AT));
        issue.setRepository(testRepository);
        testIssue = issueRepository.save(issue);

        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug("hephaestus-test-gitlab");
        workspace.setDisplayName("HephaestusTest GitLab");
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        workspace.setIsPubliclyViewable(true);
        workspace.setOrganization(org);
        workspace.setAccountLogin(FIXTURE_ORG_LOGIN);
        workspace.setAccountType(AccountType.ORG);
        testWorkspace = workspaceRepository.save(workspace);
    }

    // ==================== Test Event Listener ====================

    @Component
    static class TestCommentEventListener {

        private final List<DomainEvent.CommentCreated> createdEvents = new CopyOnWriteArrayList<>();
        private final List<DomainEvent.CommentUpdated> updatedEvents = new CopyOnWriteArrayList<>();

        @EventListener
        public void onCreated(DomainEvent.CommentCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onUpdated(DomainEvent.CommentUpdated event) {
            updatedEvents.add(event);
        }

        public List<DomainEvent.CommentCreated> getCreatedEvents() {
            return new ArrayList<>(createdEvents);
        }

        public List<DomainEvent.CommentUpdated> getUpdatedEvents() {
            return new ArrayList<>(updatedEvents);
        }

        public void clear() {
            createdEvents.clear();
            updatedEvents.clear();
        }
    }
}
