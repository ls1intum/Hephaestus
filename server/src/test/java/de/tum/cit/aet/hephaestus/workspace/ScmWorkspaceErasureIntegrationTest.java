package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJob;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobStatus;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobTrigger;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.github.connect.GithubConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.connect.GitlabConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThread;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.adapter.ScmWorkspacePurgeAdapter;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Real-Postgres proof of the F2 SCM erase-on-disconnect/purge semantics, and above all of its
 * <b>cross-tenant safety</b>.
 *
 * <p>The SCM mirror is instance-global: {@code repository}, {@code issue}, {@code pull_request} and
 * their children carry no {@code workspace_id} and are genuinely shared between workspaces that
 * monitor the same source repository. An erase that simply deleted "the workspace's repositories"
 * would destroy another tenant's data. These tests fix that boundary: a repository two workspaces
 * monitor survives one of them disconnecting, together with its issues, while a repository only the
 * erased workspace monitored is cascade-deleted with everything hanging off it.
 *
 * <p>Also pins idempotency (the erase is re-runnable), and the retention decisions: another
 * integration's rows and the {@code sync_job} history are NOT erased.
 */
class ScmWorkspaceErasureIntegrationTest extends BaseIntegrationTest {

    private static final String SHARED_REPO = "acme/shared";
    private static final String EXCLUSIVE_REPO = "acme/exclusive";

    @Autowired
    private ScmWorkspaceContentEraser eraser;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private IdentityProviderRepository gitProviderRepository;

    @Autowired
    private SlackThreadRepository slackThreadRepository;

    @Autowired
    private SyncJobRepository syncJobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScmWorkspacePurgeAdapter scmWorkspacePurgeAdapter;

    @Autowired
    private GithubConnectionStrategy githubConnectionStrategy;

    @Autowired
    private GitlabConnectionStrategy gitlabConnectionStrategy;

    private IdentityProvider gitProvider;
    private Workspace tenantA;
    private Workspace tenantB;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        gitProvider = gitProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseGet(() ->
                gitProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
            );

        tenantA = WorkspaceTestFixtures.persistInstallationWorkspace(
            workspaceRepository,
            connectionRepository,
            WorkspaceTestFixtures.installationWorkspace(5001L, "acme").withSlug("tenant-a"),
            5001L
        );
        tenantB = WorkspaceTestFixtures.persistInstallationWorkspace(
            workspaceRepository,
            connectionRepository,
            WorkspaceTestFixtures.installationWorkspace(5002L, "acme").withSlug("tenant-b"),
            5002L
        );

        Repository shared = persistRepository(9001L, SHARED_REPO);
        Repository exclusive = persistRepository(9002L, EXCLUSIVE_REPO);
        persistIssue(shared, 1, "shared issue");
        persistIssue(exclusive, 1, "exclusive issue");

        // Both tenants monitor the shared repo; only tenant A monitors the exclusive one.
        persistMonitor(tenantA, SHARED_REPO);
        persistMonitor(tenantB, SHARED_REPO);
        persistMonitor(tenantA, EXCLUSIVE_REPO);
    }

    @Test
    @DisplayName("erasing one tenant leaves a co-monitored repository and its issues fully intact")
    void erase_isCrossTenantSafe() {
        eraser.eraseWorkspaceScmMirror(tenantA.getId());

        // The other tenant's lawful basis persists: the shared repository and its mirrored content survive.
        assertThat(repositoryRepository.findByNameWithOwner(SHARED_REPO)).isPresent();
        assertThat(issueRepository.findAll()).singleElement().extracting(Issue::getTitle).isEqualTo("shared issue");
        assertThat(repositoryToMonitorRepository.findByWorkspaceId(tenantB.getId()))
            .singleElement()
            .extracting(RepositoryToMonitor::getNameWithOwner)
            .isEqualTo(SHARED_REPO);

        // The erased tenant keeps no access path, and the repository only it monitored is gone with its cascade.
        assertThat(repositoryToMonitorRepository.findByWorkspaceId(tenantA.getId())).isEmpty();
        assertThat(repositoryRepository.findByNameWithOwner(EXCLUSIVE_REPO)).isEmpty();
    }

    @Test
    @DisplayName("erasing the last tenant drops the previously shared repository and its cascade")
    void erase_ofLastMonitoringTenant_dropsTheSharedRepositoryToo() {
        eraser.eraseWorkspaceScmMirror(tenantA.getId());
        eraser.eraseWorkspaceScmMirror(tenantB.getId());

        assertThat(repositoryRepository.count()).isZero();
        assertThat(issueRepository.count()).isZero();
        assertThat(repositoryToMonitorRepository.count()).isZero();
    }

    @Test
    @DisplayName("erasure is idempotent: a second run deletes nothing and throws nothing")
    void erase_isIdempotent() {
        eraser.eraseWorkspaceScmMirror(tenantA.getId());
        long repositoriesAfterFirst = repositoryRepository.count();

        eraser.eraseWorkspaceScmMirror(tenantA.getId());

        assertThat(repositoryRepository.count()).isEqualTo(repositoriesAfterFirst);
        assertThat(repositoryToMonitorRepository.findByWorkspaceId(tenantA.getId())).isEmpty();
    }

    @Test
    @DisplayName("erasure is surgical: another integration's rows and the retained sync history both survive")
    void erase_leavesOtherIntegrationsAndRetainedHistoryAlone() {
        SlackThread slackThread = new SlackThread();
        slackThread.setWorkspaceId(tenantA.getId());
        slackThread.setSlackChannelId("C123");
        slackThread.setSlackThreadTs("1700000000.000100");
        slackThread.setCreatedAt(Instant.now());
        slackThreadRepository.save(slackThread);

        SyncJob syncJob = new SyncJob();
        syncJob.setWorkspace(tenantA);
        syncJob.setConnection(connectionRepository.findByWorkspaceId(tenantA.getId()).getFirst());
        syncJob.setKind(IntegrationKind.GITHUB);
        syncJob.setType(SyncJobType.INITIAL);
        syncJob.setTrigger(SyncJobTrigger.MANUAL);
        syncJob.setStatus(SyncJobStatus.SUCCEEDED);
        syncJob.setCreatedAt(Instant.now());
        syncJobRepository.save(syncJob);

        eraser.eraseWorkspaceScmMirror(tenantA.getId());

        // Slack is erased by its own eraser on its own disconnect — an SCM disconnect must not reach it.
        // Counted through JDBC: slack_thread is workspace-scoped, so an unscoped JPA count() would be
        // rejected by the tenancy statement inspector.
        assertThat(countRows("slack_thread")).isEqualTo(1);
        // Deliberate retention: operational audit carries no mirrored third-party content.
        assertThat(countRows("sync_job")).isEqualTo(1);
    }

    @Test
    @DisplayName("both standard triggers reach the same choke point: purge adapter and GitHub/GitLab revoke")
    void bothTriggers_eraseTheSameRowSet() {
        // Trigger B — workspace purge, via the adapter that replaced GitWorkspacePurgeAdapter.
        assertThat(scmWorkspacePurgeAdapter.getOrder()).isEqualTo(-200);
        scmWorkspacePurgeAdapter.deleteWorkspaceData(tenantA.getId());

        assertThat(repositoryToMonitorRepository.findByWorkspaceId(tenantA.getId())).isEmpty();
        assertThat(repositoryRepository.findByNameWithOwner(EXCLUSIVE_REPO)).isEmpty();
        assertThat(repositoryRepository.findByNameWithOwner(SHARED_REPO)).isPresent();

        // Trigger A — admin disconnect. GitHub's revoke was a no-op before F2; GitLab's was
        // webhook-teardown only. Both must now erase, and the end state must match the purge above.
        githubConnectionStrategy.revoke(new IntegrationRef(IntegrationKind.GITHUB, tenantB.getId(), "5002"));

        assertThat(repositoryToMonitorRepository.count()).isZero();
        assertThat(repositoryRepository.count()).isZero();
        assertThat(issueRepository.count()).isZero();
    }

    @Test
    @DisplayName("GitLab disconnect erases the mirror too — its only erase trigger")
    void gitlabRevoke_erasesTheMirror() {
        gitlabConnectionStrategy.revoke(new IntegrationRef(IntegrationKind.GITLAB, tenantA.getId(), "group-1"));

        assertThat(repositoryToMonitorRepository.findByWorkspaceId(tenantA.getId())).isEmpty();
        assertThat(repositoryRepository.findByNameWithOwner(EXCLUSIVE_REPO)).isEmpty();
        // Still cross-tenant safe on the GitLab path.
        assertThat(repositoryRepository.findByNameWithOwner(SHARED_REPO)).isPresent();
    }

    private long countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private Repository persistRepository(long nativeId, String nameWithOwner) {
        Repository repo = new Repository();
        repo.setNativeId(nativeId);
        repo.setName(nameWithOwner.substring(nameWithOwner.indexOf('/') + 1));
        repo.setNameWithOwner(nameWithOwner);
        repo.setHtmlUrl("https://github.com/" + nameWithOwner);
        repo.setVisibility(Repository.Visibility.PRIVATE);
        repo.setDefaultBranch("main");
        repo.setPushedAt(Instant.now());
        repo.setCreatedAt(Instant.now());
        repo.setUpdatedAt(Instant.now());
        repo.setProvider(gitProvider);
        return repositoryRepository.save(repo);
    }

    private void persistIssue(Repository repo, int number, String title) {
        Issue issue = new Issue();
        issue.setNativeId(repo.getNativeId() * 100 + number);
        issue.setNumber(number);
        issue.setTitle(title);
        issue.setState(Issue.State.OPEN);
        issue.setHtmlUrl(repo.getHtmlUrl() + "/issues/" + number);
        issue.setCreatedAt(Instant.now());
        issue.setUpdatedAt(Instant.now());
        issue.setProvider(gitProvider);
        issue.setRepository(repo);
        issueRepository.save(issue);
    }

    private void persistMonitor(Workspace workspace, String nameWithOwner) {
        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setNameWithOwner(nameWithOwner);
        monitor.setWorkspace(workspace);
        repositoryToMonitorRepository.save(monitor);
    }
}
