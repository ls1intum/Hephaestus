package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemIndexService;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemSourceAccess;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.workspace.ScmWorkspaceContentEraser;
import de.tum.cit.aet.hephaestus.integration.scm.workspace.WorkspaceRepositoryMonitorService;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.adapter.WorkspaceSyncTargetProvider;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class RepositoryProviderScopeIntegrationTest extends AbstractWorkspaceIntegrationTest {
    private static final String NAME = "shared-org/shared-repo";
    private static final long NATIVE_ID = 7001L;

    @Autowired
    private RepositoryRepository repositories;

    @Autowired
    private RepositoryToMonitorRepository monitors;

    @Autowired
    private ConnectionRepository connections;

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private WorkspaceSyncTargetProvider syncTargets;

    @Autowired
    private ItemIndexService itemWriter;

    @Autowired
    private ScmItemSourceAccess sourceAccess;

    @Autowired
    private IssueRepository issues;

    @Autowired
    private ScmWorkspaceContentEraser eraser;

    @Autowired
    private WorkspaceRepositoryMonitorService monitorService;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @WithMentorUser
    void shouldExposeOnlyTheConnectedInstanceWhenNamesAndNativeIdsCollide(boolean knownId) {
        User caller = persistUser("mentor");
        Workspace own = workspace("source-a", caller);
        Workspace other = workspace("source-b", caller);
        IdentityProvider first = provider("HTTPS://A.example.test:443/");
        IdentityProvider second = provider("https://b.example.test");
        connect(own, first);
        connect(other, second);
        Repository mine = repository(first, NATIVE_ID, NAME);
        Repository theirs = repository(second, NATIVE_ID, NAME);
        monitor(own, knownId ? NATIVE_ID : null);
        monitor(other, knownId ? NATIVE_ID : null);

        assertThat(repositories.findAllByWorkspaceMonitors(own.getId()))
                .extracting(Repository::getId)
                .containsExactly(mine.getId());
        assertThat(repositories.findAllByWorkspaceMonitors(other.getId()))
                .extracting(Repository::getId)
                .containsExactly(theirs.getId());
        Issue ownWork = issue(mine);
        Issue foreignWork = issue(theirs);
        assertWorkFacts(own, ownWork);
        assertWorkFacts(other, foreignWork);
        assertNoWorkFacts(own, foreignWork);
        assertNoWorkFacts(other, ownWork);
    }

    @Test
    @WithMentorUser
    void shouldExposeNoRepositoryWhenTheWorkspaceHasNoSourceConnection() {
        Workspace workspace = workspace("unconnected", persistUser("mentor"));
        Repository repository = repository(provider("https://unconnected.example.test"), NATIVE_ID, NAME);
        monitor(workspace, null);
        assertThat(repositories.findAllByWorkspaceMonitors(workspace.getId())).isEmpty();
        assertNoWorkFacts(workspace, issue(repository));
    }

    @Test
    @WithMentorUser
    void shouldRetainMonitoredWorkAndRefuseDeliveryWhilePausedThenRestoreAccessOnResume() {
        Workspace workspace = workspace("default-source", persistUser("mentor"));
        Repository mine = repository(ensureGitHubProvider(), NATIVE_ID, NAME);
        IdentityProvider enterprise = gitProviderRepository.saveAndFlush(
                new IdentityProvider(IdentityProviderType.GITHUB, "https://enterprise.example.test"));
        repository(enterprise, NATIVE_ID, NAME);
        Connection pending = connections.saveAndFlush(new Connection(
                workspace,
                IntegrationKind.GITHUB,
                "pat",
                new ConnectionConfig.GitHubPatConfig("shared-org", null, Set.of())));
        Connection active = connectionService.transition(
                pending,
                new ConnectionService.TransitionRequest(
                        IntegrationState.ACTIVE,
                        "TEST_CONNECTED",
                        "SYSTEM",
                        "provider-scope-test",
                        UUID.randomUUID().toString(),
                        "Source connected"));
        monitor(workspace, null);
        Issue work = issue(mine);
        assertWorkFacts(workspace, work);

        Connection suspended = connectionService.transition(
                active,
                new ConnectionService.TransitionRequest(
                        IntegrationState.SUSPENDED,
                        "TEST_PAUSED",
                        "SYSTEM",
                        "provider-scope-test",
                        UUID.randomUUID().toString(),
                        "Source paused"));
        assertThat(repositories.findAllByWorkspaceMonitors(workspace.getId()))
                .extracting(Repository::getId)
                .containsExactly(mine.getId());
        assertNoWorkFacts(workspace, work);
        connectionService.transition(
                suspended,
                new ConnectionService.TransitionRequest(
                        IntegrationState.ACTIVE,
                        "TEST_RESUMED",
                        "SYSTEM",
                        "provider-scope-test",
                        UUID.randomUUID().toString(),
                        "Source resumed"));
        assertWorkFacts(workspace, work);
    }

    @Test
    void shouldHealOnlyMonitorsOnTheRenamedRepositoriesProvider() {
        User owner = persistUser("scope-owner");
        Workspace own = workspace("rename-a", owner);
        Workspace sameSource = workspace("rename-shared", owner);
        Workspace other = workspace("rename-b", owner);
        IdentityProvider first = provider("https://rename-a.example.test");
        IdentityProvider second = provider("https://rename-b.example.test");
        connect(own, first);
        connect(sameSource, first);
        connect(other, second);
        repository(first, NATIVE_ID, NAME);
        repository(second, NATIVE_ID, NAME);
        RepositoryToMonitor mine = monitor(own, NATIVE_ID);
        RepositoryToMonitor shared = monitor(sameSource, NATIVE_ID);
        RepositoryToMonitor theirs = monitor(other, NATIVE_ID);

        syncTargets.reconcileSyncTargetsForRepository(
                Objects.requireNonNull(first.getId()), NATIVE_ID, "shared-org/renamed");

        assertThat(monitors.findById(mine.getId()).orElseThrow().getNameWithOwner())
                .isEqualTo("shared-org/renamed");
        assertThat(monitors.findById(shared.getId()).orElseThrow().getNameWithOwner())
                .isEqualTo("shared-org/renamed");
        assertThat(monitors.findById(theirs.getId()).orElseThrow().getNameWithOwner())
                .isEqualTo(NAME);
    }

    @Test
    void shouldRepairAProvenForeignInstanceIdButPreserveAnOwnInstanceIdUnderAnotherName() {
        Workspace workspace = workspace("repair", persistUser("repair-owner"));
        IdentityProvider own = provider("https://repair-a.example.test");
        IdentityProvider foreign = provider("https://repair-b.example.test");
        connect(workspace, own);
        repository(own, 9001L, NAME);
        Repository foreignOld = repository(foreign, 701L, NAME);
        RepositoryToMonitor monitor = monitor(workspace, 701L);
        syncTargets.reconcileSyncTargetIdentity(monitor.getId(), 9001L, NAME);
        assertThat(monitors.findById(monitor.getId()).orElseThrow().getNativeId())
                .isEqualTo(9001L);

        repository(own, 702L, "shared-org/reassigned");
        foreignOld.setNameWithOwner("shared-org/previous");
        repositories.saveAndFlush(foreignOld);
        repository(foreign, 702L, NAME);
        monitor.setNativeId(702L);
        monitors.saveAndFlush(monitor);
        syncTargets.reconcileSyncTargetIdentity(monitor.getId(), 9001L, NAME);
        assertThat(monitors.findById(monitor.getId()).orElseThrow().getNativeId())
                .isEqualTo(702L);
    }

    @Test
    void shouldNotInferAReplacementFromForeignRowsWithoutAConnectedSource() {
        Workspace workspace = workspace("unknown-repair", persistUser("unknown-owner"));
        repository(provider("https://unknown.example.test"), 701L, NAME);
        RepositoryToMonitor monitor = monitor(workspace, 701L);
        syncTargets.reconcileSyncTargetIdentity(monitor.getId(), 9001L, NAME);
        assertThat(monitors.findById(monitor.getId()).orElseThrow().getNativeId())
                .isEqualTo(701L);
    }

    @Test
    @WithMentorUser
    void shouldDenyPendingAndRetiredSourcesAfterAWorkspaceReconnects() {
        Workspace workspace = workspace("reconnected", persistUser("mentor"));
        IdentityProvider old = provider("https://retired.example.test");
        IdentityProvider current = provider("https://current.example.test");
        Workspace sharedOld = workspace("retained-old-source", persistUser("retained-owner"));
        connect(sharedOld, old);
        monitor(sharedOld, NATIVE_ID);
        Repository oldRepository = repository(old, NATIVE_ID, NAME);
        Repository currentRepository = repository(current, NATIVE_ID, NAME);
        Connection pending = connections.saveAndFlush(new Connection(
                workspace,
                IntegrationKind.GITLAB,
                old.getServerUrl(),
                new ConnectionConfig.GitLabConfig(
                        old.getServerUrl(),
                        null,
                        null,
                        ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                        Set.of(),
                        null)));
        monitor(workspace, NATIVE_ID);
        assertThat(repositories.findAllByWorkspaceMonitors(workspace.getId())).isEmpty();
        assertNoWorkFacts(workspace, issue(oldRepository));

        Connection active = connectionService.transition(
                pending,
                new ConnectionService.TransitionRequest(
                        IntegrationState.ACTIVE,
                        "TEST_CONNECTED",
                        "SYSTEM",
                        "source-lifecycle-test",
                        UUID.randomUUID().toString(),
                        "Source connected"));
        eraser.eraseWorkspaceScmMirror(workspace.getId());
        connectionService.transition(
                active,
                new ConnectionService.TransitionRequest(
                        IntegrationState.UNINSTALLED,
                        "TEST_UNINSTALLED",
                        "SYSTEM",
                        "source-lifecycle-test",
                        UUID.randomUUID().toString(),
                        "Source disconnected"));
        connect(workspace, current);
        monitor(workspace, NATIVE_ID);
        assertThat(repositories.findAllByWorkspaceMonitors(workspace.getId()))
                .extracting(Repository::getId)
                .containsExactly(currentRepository.getId());
        assertWorkFacts(workspace, issue(currentRepository));
    }

    @Test
    void shouldRetainAReplacementMonitorWhenAnInstallationRemovalNamesTheOldNativeId() {
        Workspace workspace = workspace("installation-identity", persistUser("installation-owner"));
        Repository replacement = repository(ensureGitHubProvider(), NATIVE_ID + 1, NAME);
        long installationId = 881234L;
        Connection pending = connections.saveAndFlush(new Connection(
                workspace,
                IntegrationKind.GITHUB,
                Long.toString(installationId),
                new ConnectionConfig.GitHubAppConfig(installationId, "shared-org", null, Set.of())));
        connectionService.transition(
                pending,
                new ConnectionService.TransitionRequest(
                        IntegrationState.ACTIVE,
                        "TEST_CONNECTED",
                        "SYSTEM",
                        "installation-identity-test",
                        UUID.randomUUID().toString(),
                        "Source connected"));
        RepositoryToMonitor monitor = monitor(workspace, NATIVE_ID + 1);
        monitorService.removeRepositoryMonitorForInstallation(installationId, NAME, NATIVE_ID);
        assertThat(monitors.findById(monitor.getId())).isPresent();
        assertThat(repositories.findById(replacement.getId())).isPresent();
        monitor.setNativeId(null);
        monitors.saveAndFlush(monitor);
        monitorService.removeRepositoryMonitorForInstallation(installationId, NAME, NATIVE_ID);
        assertThat(monitors.findById(monitor.getId())).isPresent();
        monitorService.removeRepositoryMonitorForInstallation(installationId, NAME, NATIVE_ID + 1);
        assertThat(monitors.findById(monitor.getId())).isEmpty();
        assertThat(repositories.findById(replacement.getId())).isEmpty();
    }

    @Test
    void shouldEraseOnlyTheExactOrphanAndKeepSharedAndForeignRepositories() {
        User owner = persistUser("erase-owner");
        Workspace first = workspace("erase-a", owner);
        Workspace shared = workspace("erase-shared", owner);
        Workspace foreign = workspace("erase-b", owner);
        IdentityProvider source = provider("https://erase-a.example.test");
        IdentityProvider other = provider("https://erase-b.example.test");
        connect(first, source);
        connect(shared, source);
        connect(foreign, other);
        Repository ownRepository = repository(source, NATIVE_ID, NAME);
        Repository foreignRepository = repository(other, NATIVE_ID, NAME);
        monitor(first, NATIVE_ID);
        monitor(shared, NATIVE_ID);
        RepositoryToMonitor foreignMonitor = monitor(foreign, NATIVE_ID);

        eraser.eraseWorkspaceScmMirror(first.getId());
        assertThat(repositories.findById(ownRepository.getId())).isPresent();
        assertThat(repositories.findById(foreignRepository.getId())).isPresent();

        eraser.eraseWorkspaceScmMirror(shared.getId());
        assertThat(repositories.findById(ownRepository.getId())).isEmpty();
        assertThat(repositories.findById(foreignRepository.getId())).isPresent();
        assertThat(monitors.findById(foreignMonitor.getId()).orElseThrow().getNameWithOwner())
                .isEqualTo(NAME);
    }

    @Test
    void shouldNotDeleteAnySameNamedMirrorWhenTheErasedMonitorHasNoSourceAuthority() {
        Workspace unknown = workspace("unknown-erase", persistUser("unknown-erase-owner"));
        Repository first = repository(provider("https://unknown-erase-a.example.test"), NATIVE_ID, NAME);
        Repository second = repository(provider("https://unknown-erase-b.example.test"), NATIVE_ID, NAME);
        RepositoryToMonitor monitor = monitor(unknown, null);
        eraser.eraseWorkspaceScmMirror(unknown.getId());
        assertThat(monitors.findById(monitor.getId())).isEmpty();
        assertThat(repositories.findById(first.getId())).isPresent();
        assertThat(repositories.findById(second.getId())).isPresent();
    }

    private Workspace workspace(String slug, User owner) {
        return createWorkspace(slug, slug, "shared-org", AccountType.ORG, owner);
    }

    private IdentityProvider provider(String url) {
        return gitProviderRepository.saveAndFlush(new IdentityProvider(IdentityProviderType.GITLAB, url));
    }

    private Connection connect(Workspace workspace, IdentityProvider provider) {
        Connection connection = connections.saveAndFlush(new Connection(
                workspace,
                IntegrationKind.GITLAB,
                provider.getServerUrl(),
                new ConnectionConfig.GitLabConfig(
                        provider.getServerUrl(),
                        null,
                        null,
                        ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                        Set.of(),
                        null)));
        return connectionService.transition(
                connection,
                new ConnectionService.TransitionRequest(
                        IntegrationState.ACTIVE,
                        "TEST_CONNECTED",
                        "SYSTEM",
                        "provider-scope-test",
                        UUID.randomUUID().toString(),
                        "Source connected"));
    }

    private Repository repository(IdentityProvider provider, long nativeId, String name) {
        Repository repository = new Repository();
        repository.setProvider(provider);
        repository.setNativeId(nativeId);
        repository.setNameWithOwner(name);
        repository.setName(name.substring(name.lastIndexOf('/') + 1));
        repository.setHtmlUrl(provider.getServerUrl() + "/" + name);
        repository.setDefaultBranch("main");
        return repositories.saveAndFlush(repository);
    }

    private RepositoryToMonitor monitor(Workspace workspace, @Nullable Long nativeId) {
        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setWorkspace(workspace);
        monitor.setNameWithOwner(NAME);
        monitor.setNativeId(nativeId);
        return monitors.saveAndFlush(monitor);
    }

    private Issue issue(Repository repository) {
        Issue issue = new Issue();
        issue.setProvider(repository.getProvider());
        issue.setRepository(repository);
        issue.setNativeId(7L);
        issue.setNumber(7);
        issue.setTitle("Captured work");
        issue.setState(Issue.State.OPEN);
        issue.setHtmlUrl(repository.getHtmlUrl() + "/issues/7");
        return issues.saveAndFlush(issue);
    }

    private void assertWorkFacts(Workspace workspace, Issue issue) {
        assertThat(sourceAccess.permits(item(workspace, issue), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .isTrue();
    }

    private void assertNoWorkFacts(Workspace workspace, Issue issue) {
        assertThat(sourceAccess.permits(item(workspace, issue), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .isFalse();
    }

    private ItemRef item(Workspace workspace, Issue issue) {
        var sourceRepository = Objects.requireNonNull(issue.getRepository());
        var repository = itemWriter.upsert(new ItemRegistration(
                workspace.getId(), new ItemKind("scm.repository"), sourceRepository.getId(), null, null));
        return itemWriter.upsert(new ItemRegistration(
                workspace.getId(), new ItemKind("scm.issue"), issue.getId(), repository.itemId(), null));
    }
}
