package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.item.ItemRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ProvisioningListener;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace.GitLabRepositoryMonitors;
import de.tum.cit.aet.hephaestus.integration.scm.workspace.WorkspaceRepositoryMonitorService;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class ScmRepositoryItemLifecycleIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private SyncTargetProvider syncTargets;

    @Autowired
    private WorkspaceRepositoryMonitorService monitorService;

    @Autowired
    private GitLabRepositoryMonitors gitLabMonitors;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private ConnectionRepository connections;

    @Autowired
    private IdentityProviderRepository providers;

    @Autowired
    private RepositoryRepository repositories;

    @Autowired
    private RepositoryToMonitorRepository monitors;

    @Autowired
    private ItemRepository items;

    @Autowired
    private PullRequestRepository pulls;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(
            value = IdentityProviderType.class,
            names = {"GITHUB", "GITLAB"})
    void shouldRegisterAnEmptyRepositoryAfterSyncAndReuseItsItemForLaterPullRequests(IdentityProviderType type) {
        var fixture = fixture(type);
        var monitor = monitor(fixture.workspace(), fixture.repository());
        assertThat(repositoryItemIds(fixture)).isEmpty();

        reconcile(monitor, fixture.repository());
        long itemId = repositoryItemId(fixture);
        reconcile(monitor, fixture.repository());
        assertThat(repositoryItemIds(fixture)).containsExactly(itemId);
        assertActive(fixture.workspace(), itemId, true);

        var pull = new PullRequest();
        pull.setNativeId(randomId());
        pull.setProvider(fixture.repository().getProvider());
        pull.setRepository(fixture.repository());
        pull.setNumber(1);
        pull.setTitle("First pull request");
        pull.setState(Issue.State.OPEN);
        pull.setHtmlUrl(fixture.repository().getHtmlUrl() + "/pull/1");
        pull.setCreatedAt(Instant.now());
        pull.setUpdatedAt(Instant.now());
        pull.setHeadRefOid("a".repeat(40));
        pull.setBaseRefOid("b".repeat(40));
        pull.setHeadRefName("topic");
        pull.setBaseRefName("main");
        long pullId = pulls.saveAndFlush(pull).getId();
        transactions.executeWithoutResult(status -> publisher.publishEvent(new ScmDomainEvent.PullRequestCreated(
                ScmEventPayload.PullRequestData.from(pulls.findById(pullId).orElseThrow()),
                EventContext.forSync(fixture.workspace().getId(), null, type, Instant.now()))));

        assertThat(repositoryItemIds(fixture)).containsExactly(itemId);
        assertThat(items.findSource(fixture.workspace().getId(), ScmItemEvents.PULL_REQUEST.value(), pullId))
                .hasValueSatisfying(item -> assertThat(item.getParentId()).isEqualTo(itemId));
    }

    @ParameterizedTest
    @EnumSource(
            value = IdentityProviderType.class,
            names = {"GITHUB", "GITLAB"})
    void shouldRemoveOnlyTheWorkspaceItemAndRestoreItsIdentityWhenMonitoringResumes(IdentityProviderType type) {
        var fixture = fixture(type);
        var monitor = monitor(fixture.workspace(), fixture.repository());
        reconcile(monitor, fixture.repository());
        long itemId = repositoryItemId(fixture);
        var other = connectedWorkspace(type, fixture.repository().getProvider().getServerUrl());
        reconcile(monitor(other, fixture.repository()), fixture.repository());
        long otherItemId = items.findSource(
                        other.getId(),
                        ScmItemEvents.REPOSITORY.value(),
                        fixture.repository().getId())
                .orElseThrow()
                .getId();

        syncTargets.removeSyncTarget(monitor.getId());

        assertActive(fixture.workspace(), itemId, false);
        assertActive(other, otherItemId, true);
        assertThat(repositories.findById(fixture.repository().getId())).isPresent();
        reconcile(monitor(fixture.workspace(), fixture.repository()), fixture.repository());
        assertThat(repositoryItemIds(fixture)).containsExactly(itemId);
        assertActive(fixture.workspace(), itemId, true);
    }

    @ParameterizedTest
    @EnumSource(
            value = IdentityProviderType.class,
            names = {"GITHUB", "GITLAB"})
    void shouldRemoveTheItemThroughTheWorkspaceMonitorService(IdentityProviderType type) {
        var fixture = fixture(type);
        reconcile(monitor(fixture.workspace(), fixture.repository()), fixture.repository());
        long itemId = repositoryItemId(fixture);
        var other = connectedWorkspace(type, fixture.repository().getProvider().getServerUrl());
        reconcile(monitor(other, fixture.repository()), fixture.repository());

        if (type == IdentityProviderType.GITHUB) {
            monitorService.removeRepositoryMonitorForInstallation(
                    installationId(fixture.workspace()),
                    fixture.repository().getNameWithOwner(),
                    fixture.repository().getNativeId());
        } else {
            monitorService.removeRepositoryFromMonitor(
                    fixture.workspace().getWorkspaceSlug(), fixture.repository().getNameWithOwner());
        }

        assertActive(fixture.workspace(), itemId, false);
        assertThat(repositories.findById(fixture.repository().getId())).isPresent();
    }

    @ParameterizedTest
    @EnumSource(
            value = IdentityProviderType.class,
            names = {"GITHUB", "GITLAB"})
    void shouldRemoveTheItemBeforeDeletingTheLastMonitoredRepository(IdentityProviderType type) {
        var fixture = fixture(type);
        reconcile(monitor(fixture.workspace(), fixture.repository()), fixture.repository());
        long itemId = repositoryItemId(fixture);

        if (type == IdentityProviderType.GITHUB) {
            monitorService.removeRepositoryMonitorForInstallation(
                    installationId(fixture.workspace()),
                    fixture.repository().getNameWithOwner(),
                    fixture.repository().getNativeId());
        } else {
            monitorService.removeRepositoryFromMonitor(
                    fixture.workspace().getWorkspaceSlug(), fixture.repository().getNameWithOwner());
        }

        assertActive(fixture.workspace(), itemId, false);
        assertThat(repositories.findById(fixture.repository().getId())).isEmpty();
    }

    @Test
    void shouldRollBackTheRepositoryItemWithTheMonitorTransaction() {
        var fixture = fixture(IdentityProviderType.GITHUB);
        var monitor = monitor(fixture.workspace(), fixture.repository());

        transactions.executeWithoutResult(status -> {
            reconcile(monitor, fixture.repository());
            assertThat(repositoryItemIds(fixture)).hasSize(1);
            status.setRollbackOnly();
        });

        assertThat(repositoryItemIds(fixture)).isEmpty();
        assertThat(monitors.findById(monitor.getId()))
                .hasValueSatisfying(row -> assertThat(row.getNativeId()).isNull());
    }

    @Test
    void shouldRegisterAndRestoreAnEmptyGitHubRepositoryWhenItsInstallationBindsIt() {
        var fixture = fixture(IdentityProviderType.GITHUB);
        var repository = fixture.repository();
        var snapshot = new ProvisioningListener.RepositorySnapshot(
                repository.getNativeId(), repository.getNameWithOwner(), repository.getName(), false);

        monitorService.ensureRepositoryAndMonitorFromSnapshot(installationId(fixture.workspace()), snapshot);
        long itemId = repositoryItemId(fixture);
        assertActive(fixture.workspace(), itemId, true);
        monitorService.ensureRepositoryAndMonitorFromSnapshot(installationId(fixture.workspace()), snapshot);
        assertThat(repositoryItemIds(fixture)).containsExactly(itemId);
        monitorService.removeAllRepositoriesFromMonitor(installationId(fixture.workspace()), false);
        assertActive(fixture.workspace(), itemId, false);
        monitorService.ensureRepositoryAndMonitorFromSnapshot(installationId(fixture.workspace()), snapshot);
        assertThat(repositoryItemIds(fixture)).containsExactly(itemId);
        assertActive(fixture.workspace(), itemId, true);
    }

    @Test
    void shouldRegisterAndRestoreAnEmptyGitLabRepositoryWhenDiscoveryBindsIt() {
        var fixture = fixture(IdentityProviderType.GITLAB);

        assertThat(gitLabMonitors.monitorAll(fixture.workspace(), List.of(fixture.repository())))
                .isEqualTo(1);
        long itemId = repositoryItemId(fixture);
        assertActive(fixture.workspace(), itemId, true);
        var monitor = monitors.findByWorkspaceIdAndNameWithOwner(
                        fixture.workspace().getId(), fixture.repository().getNameWithOwner())
                .orElseThrow();
        syncTargets.removeSyncTarget(monitor.getId());
        assertActive(fixture.workspace(), itemId, false);
        assertThat(gitLabMonitors.monitorAll(fixture.workspace(), List.of(fixture.repository())))
                .isEqualTo(1);
        assertThat(repositoryItemIds(fixture)).containsExactly(itemId);
        assertActive(fixture.workspace(), itemId, true);
    }

    @Test
    void shouldIgnoreARepositoryFromAnotherGitLabInstanceWhenThePathMatches() {
        var fixture = fixture(IdentityProviderType.GITLAB);
        var foreignProvider = providers.saveAndFlush(
                new IdentityProvider(IdentityProviderType.GITLAB, "https://" + UUID.randomUUID() + ".test"));
        var foreign = repository(foreignProvider, fixture.repository().getNameWithOwner());
        var monitor = monitor(fixture.workspace(), fixture.repository());

        reconcile(monitor, fixture.repository());

        assertThat(repositoryItemIds(fixture)).hasSize(1);
        assertThat(items.findSource(fixture.workspace().getId(), ScmItemEvents.REPOSITORY.value(), foreign.getId()))
                .isEmpty();
        monitor.setNativeId(foreign.getNativeId());
        monitors.saveAndFlush(monitor);
        reconcile(monitor, foreign);
        assertThat(items.findSource(fixture.workspace().getId(), ScmItemEvents.REPOSITORY.value(), foreign.getId()))
                .isEmpty();
    }

    private Fixture fixture(IdentityProviderType type) {
        String url = type == IdentityProviderType.GITHUB ? "https://github.com" : "https://gitlab.example.test";
        var provider = providers
                .findByTypeAndServerUrl(type, url)
                .orElseGet(() -> providers.saveAndFlush(new IdentityProvider(type, url)));
        var workspace = connectedWorkspace(type, url);
        return new Fixture(workspace, repository(provider, workspace.getAccountLogin() + "/empty"));
    }

    private Workspace connectedWorkspace(IdentityProviderType type, String url) {
        String slug = "repo-item-" + UUID.randomUUID();
        if (type == IdentityProviderType.GITHUB) {
            long id = randomId();
            return WorkspaceTestFixtures.persistInstallationWorkspace(
                    workspaces,
                    connections,
                    WorkspaceTestFixtures.installationWorkspace(id, slug).withSlug(slug),
                    id);
        }
        return WorkspaceTestFixtures.persistGitLabWorkspace(
                workspaces,
                connections,
                WorkspaceTestFixtures.gitLabPatWorkspace(slug).withSlug(slug),
                url);
    }

    private Repository repository(IdentityProvider provider, String nameWithOwner) {
        var repository = new Repository();
        repository.setNativeId(randomId());
        repository.setProvider(provider);
        repository.setName("empty");
        repository.setNameWithOwner(nameWithOwner);
        repository.setHtmlUrl(provider.getServerUrl() + "/" + nameWithOwner);
        repository.setDefaultBranch("main");
        repository.setVisibility(Repository.Visibility.PUBLIC);
        repository.setCreatedAt(Instant.now());
        repository.setUpdatedAt(Instant.now());
        return repositories.saveAndFlush(repository);
    }

    private RepositoryToMonitor monitor(Workspace workspace, Repository repository) {
        var monitor = WorkspaceTestFixtures.repositoryMonitor(workspace, repository.getNameWithOwner());
        return monitors.saveAndFlush(monitor);
    }

    private void reconcile(RepositoryToMonitor monitor, Repository repository) {
        syncTargets.reconcileSyncTargetIdentity(
                monitor.getId(), repository.getNativeId(), repository.getNameWithOwner());
    }

    private List<Long> repositoryItemIds(Fixture fixture) {
        return jdbc
                .queryForList(
                        "SELECT id FROM item WHERE workspace_id=? AND kind='scm.repository' AND source_id=?",
                        Long.class,
                        fixture.workspace().getId(),
                        fixture.repository().getId())
                .stream()
                .map(Objects::requireNonNull)
                .toList();
    }

    private long repositoryItemId(Fixture fixture) {
        assertThat(repositoryItemIds(fixture)).hasSize(1);
        return repositoryItemIds(fixture).getFirst();
    }

    private void assertActive(Workspace workspace, long itemId, boolean active) {
        assertThat(jdbc.queryForObject(
                        "SELECT removed_at IS NULL FROM item WHERE workspace_id=? AND id=?",
                        Boolean.class,
                        workspace.getId(),
                        itemId))
                .isEqualTo(active);
    }

    private long installationId(Workspace workspace) {
        var config = connections
                .findActive(workspace.getId(), IntegrationKind.GITHUB)
                .orElseThrow()
                .getConfig();
        if (config instanceof ConnectionConfig.GitHubAppConfig app) return Objects.requireNonNull(app.installationId());
        throw new IllegalStateException("Test workspace has no GitHub App connection");
    }

    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    private record Fixture(Workspace workspace, Repository repository) {}
}
