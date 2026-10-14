package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.integration.scm.spi.ScmTokenSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.ItemFixtures;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class ReviewRepositoryPreparerIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private PullRequestRepository pullRequests;

    @Autowired
    private RepositoryRepository repositories;

    @Autowired
    private IdentityProviderRepository providers;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private RepositoryToMonitorRepository monitors;

    @Autowired
    private ConnectionRepository connectionRows;

    @Autowired
    private ConnectionService connections;

    private static final String HEAD = "a".repeat(40);
    private PullRequest pullRequest;
    private ScmCaptureContext context;
    private ReviewRepositoryPreparer preparer;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        var provider = providers.save(new IdentityProvider(IdentityProviderType.GITLAB, "https://scm.example"));
        var repository = new Repository();
        repository.setNativeId(200L);
        repository.setProvider(provider);
        repository.setName("repo");
        repository.setNameWithOwner("owner/repo");
        repository.setHtmlUrl("https://scm.example/owner/repo");
        repository.setVisibility(Repository.Visibility.PUBLIC);
        repository.setCreatedAt(Instant.now());
        repository.setUpdatedAt(Instant.now());
        repository = repositories.save(repository);
        var pr = new PullRequest();
        pr.setNativeId(300L);
        pr.setProvider(provider);
        pr.setRepository(repository);
        pr.setNumber(42);
        pr.setTitle("Reviewed change");
        pr.setHtmlUrl("https://scm.example/owner/repo/-/merge_requests/42");
        pr.setState(PullRequest.State.OPEN);
        pr.setCreatedAt(Instant.now());
        pr.setUpdatedAt(Instant.parse("2026-10-01T12:00:00.123Z"));
        pr.setHeadRefOid(HEAD);
        pullRequest = pullRequests.save(pr);
        var workspace = WorkspaceTestFixtures.persistGitLabWorkspace(
                workspaces, connectionRows, WorkspaceTestFixtures.gitLabPatWorkspace("owner"), "https://scm.example");
        var monitor = WorkspaceTestFixtures.repositoryMonitor(workspace, "owner/repo");
        monitor.setNativeId(repository.getNativeId());
        monitors.save(monitor);
        long workspaceId = workspace.getId();
        context = ScmCaptureFixtures.context(
                JsonMapper.builder()
                        .build()
                        .createObjectNode()
                        .put("pull_request_id", pullRequest.getId())
                        .put("repository_id", repository.getId())
                        .put("repository_full_name", "owner/repo")
                        .put("commit_sha", HEAD)
                        .put("base_ref_oid", "b".repeat(40)),
                workspaceId,
                false);
        var previous = context.pinned();
        context = new ScmCaptureContext(
                ItemFixtures.snapshot(previous.item(), previous.state())
                        .version(previous.version())
                        .integration(new IntegrationRef(IntegrationKind.GITLAB, workspaceId, null))
                        .roles(previous.roles())
                        .label(previous.label())
                        .build(),
                context.request(),
                context.work(),
                context.preparation(),
                context.capturedAt());
        var tokens = mock(ScmTokenSource.class);
        when(tokens.kind()).thenReturn(IntegrationKind.GITLAB);
        when(tokens.serverUrl(workspaceId)).thenReturn(Optional.of("https://scm.example"));
        when(tokens.recordsReviewDiffBase()).thenReturn(true);
        when(tokens.accessToken(workspaceId)).thenReturn(Optional.of("private-token"));
        var git = mock(GitRepositoryManager.class);
        when(git.commitExists(any(), anyString())).thenReturn(true);
        preparer = new ReviewRepositoryPreparer(git, pullRequests, connections, List.of(tokens), repositories);
    }

    @Test
    void shouldPrepareTheExactPinnedRangeFromTheRealMirror() {
        var prepared = preparer.prepare(context);
        assertThat(prepared.head()).isEqualTo(HEAD);
        assertThat(prepared.target()).isEqualTo("b".repeat(40));
    }
}
