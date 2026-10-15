package de.tum.cit.aet.hephaestus.integration.scm.gitlab.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.RepositoryScopeFilter;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace.GitLabRouteAdmission;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GitLabSharedWebhookProviderIdentityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitLabWebhookContextResolver resolver;

    @Autowired
    private GitLabProperties properties;

    @Autowired
    private IdentityProviderRepository providers;

    @Autowired
    private RepositoryRepository repositories;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private ConnectionRepository connections;

    @Autowired
    private RepositoryToMonitorRepository monitors;

    @Autowired
    private RepositoryScopeFilter scopeFilter;

    @Test
    void shouldRefuseAForeignGitHubRepositoryAtTheAllowedGitLabPath() {
        Fixture fixture = fixture();
        IdentityProvider github = provider(IdentityProviderType.GITHUB, "https://github.com");
        Repository foreign = repository(github, fixture.nativeId(), fixture.path());

        Optional<ResolvedIdentity> selected = Optional.ofNullable(resolver.resolve(fixture.path(), "open", "issue"))
                .map(GitLabSharedWebhookProviderIdentityIntegrationTest::identity);

        assertThat(repositories
                        .findById(foreign.getId())
                        .orElseThrow()
                        .getProvider()
                        .getId())
                .isEqualTo(github.getId());
        assertThat(selected)
                .as("A shared GitLab delivery must not select the persisted GitHub repository")
                .isEmpty();
    }

    @Test
    void shouldResolveTheConfiguredGitLabRepositoryAndItsWorkspace() {
        Fixture fixture = fixture();
        IdentityProvider gitlab = provider(IdentityProviderType.GITLAB, properties.defaultServerUrl());
        Repository correct = repository(gitlab, fixture.nativeId(), fixture.path());

        ProcessingContext selected = Objects.requireNonNull(resolver.resolve(fixture.path(), "open", "issue"));

        assertThat(identity(selected)).isEqualTo(expected(fixture, correct, gitlab));
        assertThat(repositories.findById(correct.getId()).orElseThrow().getNativeId())
                .isEqualTo(fixture.nativeId());
        assertThat(resolver.mayStillWrite(selected)).isTrue();
    }

    @Test
    void shouldResolveOnlyTheConfiguredGitLabRepositoryWhenProviderPathsCollide() {
        Fixture fixture = fixture();
        IdentityProvider github = provider(IdentityProviderType.GITHUB, "https://github.com");
        IdentityProvider gitlab = provider(IdentityProviderType.GITLAB, properties.defaultServerUrl());
        Repository foreign = repository(github, fixture.nativeId(), fixture.path());
        Repository correct = repository(gitlab, fixture.nativeId(), fixture.path());
        assertThat(correct.getId()).isNotEqualTo(foreign.getId());
        assertThat(repositories.findById(foreign.getId()).orElseThrow().getNativeId())
                .isEqualTo(correct.getNativeId());
        var selected = new ArrayList<ResolvedIdentity>();

        var failure = catchThrowable(() -> {
            ProcessingContext context = resolver.resolve(fixture.path(), "open", "issue");
            if (context != null) {
                selected.add(identity(context));
            }
        });

        assertThat(failure)
                .as("Same-path rows from different providers must resolve without ambiguity")
                .isNull();
        assertThat(selected).containsExactly(expected(fixture, correct, gitlab));
    }

    private Fixture fixture() {
        String group = "g-" + UUID.randomUUID();
        String path = group + "/project";
        Workspace workspace = WorkspaceTestFixtures.persistGitLabWorkspace(
                workspaces,
                connections,
                WorkspaceTestFixtures.gitLabPatWorkspace(group),
                properties.defaultServerUrl());
        monitors.saveAndFlush(WorkspaceTestFixtures.repositoryMonitor(workspace, path));
        assertThat(GitLabRouteAdmission.current()).isEmpty();
        assertThat(scopeFilter.isRepositoryAllowed(path)).isTrue();
        long nativeId = 100_000_000L + Integer.toUnsignedLong(UUID.randomUUID().hashCode()) % 900_000_000L;
        return new Fixture(path, Objects.requireNonNull(workspace.getId()), nativeId);
    }

    private IdentityProvider provider(IdentityProviderType type, String serverUrl) {
        return providers
                .findByTypeAndServerUrl(type, serverUrl)
                .orElseGet(() -> providers.saveAndFlush(new IdentityProvider(type, serverUrl)));
    }

    private Repository repository(IdentityProvider provider, long nativeId, String path) {
        Repository repository = new Repository();
        assertThat(repository.getId()).isNull();
        repository.setProvider(provider);
        repository.setNativeId(nativeId);
        repository.setName("project");
        repository.setNameWithOwner(path);
        repository.setHtmlUrl(provider.getServerUrl() + "/" + path);
        repository.setDefaultBranch("main");
        repository.setVisibility(Repository.Visibility.PUBLIC);
        repository.setPushedAt(Instant.parse("2026-10-01T09:00:00Z"));
        Repository saved = repositories.saveAndFlush(repository);
        assertThat(saved.getId()).isPositive();
        return saved;
    }

    private static ResolvedIdentity identity(ProcessingContext context) {
        Repository repository = Objects.requireNonNull(context.repository());
        IdentityProvider provider = Objects.requireNonNull(context.provider());
        return new ResolvedIdentity(
                Objects.requireNonNull(repository.getId()),
                repository.getNativeId(),
                Objects.requireNonNull(provider.getId()),
                provider.getType(),
                provider.getServerUrl(),
                repository.getNameWithOwner(),
                Objects.requireNonNull(context.scopeId()));
    }

    private static ResolvedIdentity expected(Fixture fixture, Repository repository, IdentityProvider provider) {
        return new ResolvedIdentity(
                Objects.requireNonNull(repository.getId()),
                fixture.nativeId(),
                Objects.requireNonNull(provider.getId()),
                provider.getType(),
                provider.getServerUrl(),
                fixture.path(),
                fixture.workspaceId());
    }

    private record Fixture(String path, long workspaceId, long nativeId) {}

    private record ResolvedIdentity(
            long repositoryId,
            long nativeId,
            long providerId,
            IdentityProviderType providerType,
            String serverUrl,
            String path,
            long workspaceId) {}
}
