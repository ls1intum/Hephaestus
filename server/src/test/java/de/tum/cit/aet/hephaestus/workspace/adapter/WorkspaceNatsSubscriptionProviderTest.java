package de.tum.cit.aet.hephaestus.workspace.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.NatsSubscriptionProvider.NatsSubscriptionInfo;
import de.tum.cit.aet.hephaestus.integration.core.spi.NatsSubscriptionProvider.StreamSubscription;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceScopeFilter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * The provider maps a workspace onto one {@link StreamSubscription} per bound stream. An SCM-connected
 * workspace gets its {@code github}/{@code gitlab} stream; an Outline connection with a registered
 * subscription adds the {@code outline} stream; a workspace with only Outline gets ONLY the outline
 * stream (never the {@code github} fallthrough it used to be mislabeled with).
 */
class WorkspaceNatsSubscriptionProviderTest extends BaseUnitTest {

    private static final long WS = 7L;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceScopeFilter workspaceScopeFilter;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private Workspace workspace;

    private WorkspaceNatsSubscriptionProvider provider;

    @BeforeEach
    void setUp() {
        provider = new WorkspaceNatsSubscriptionProvider(workspaceRepository, workspaceScopeFilter, connectionService);
        when(workspaceRepository.findById(WS)).thenReturn(Optional.of(workspace));
        when(workspace.getId()).thenReturn(WS);
        lenientReposAndOrg();
    }

    private void lenientReposAndOrg() {
        org.mockito.Mockito.lenient().when(workspace.getAccountLogin()).thenReturn("acme");
        org.mockito.Mockito.lenient().when(workspace.getRepositoriesToMonitor()).thenReturn(Set.of());
    }

    private StreamSubscription streamNamed(NatsSubscriptionInfo info, String stream) {
        return info
            .streamSubscriptions()
            .stream()
            .filter(s -> s.streamName().equals(stream))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no subscription for stream " + stream));
    }

    @Test
    void scmConnectedWorkspaceEmitsTheScmStreamWithRepoAndOrgSubjects() {
        RepositoryToMonitor repo = org.mockito.Mockito.mock(RepositoryToMonitor.class);
        when(repo.getNameWithOwner()).thenReturn("acme/app");
        when(workspace.getRepositoriesToMonitor()).thenReturn(Set.of(repo));
        when(workspaceScopeFilter.isRepositoryAllowed("acme/app")).thenReturn(true);
        when(connectionService.findActiveProviderKind(WS)).thenReturn(Optional.of(IntegrationKind.GITHUB));
        when(connectionService.findActiveOutlineConfig(WS)).thenReturn(Optional.empty());

        NatsSubscriptionInfo info = provider.getSubscriptionInfo(WS).orElseThrow();

        assertThat(info.streamSubscriptions()).hasSize(1);
        assertThat(streamNamed(info, "github").subjects()).contains("github.acme.app.>", "github.acme.?.>");
    }

    @Test
    void outlineOnlyWorkspaceEmitsOnlyTheOutlineStream() {
        when(connectionService.findActiveProviderKind(WS)).thenReturn(Optional.empty());
        when(connectionService.findActiveOutlineConfig(WS)).thenReturn(
            Optional.of(new ConnectionConfig.OutlineConfig("https://o.test", "sub-1", "secret", Set.of()))
        );

        NatsSubscriptionInfo info = provider.getSubscriptionInfo(WS).orElseThrow();

        assertThat(info.streamSubscriptions().stream().map(StreamSubscription::streamName)).containsExactly("outline");
        assertThat(streamNamed(info, "outline").subjects()).containsExactly("outline.sub-1.>");
    }

    @Test
    void dualConnectionWorkspaceEmitsBothStreams() {
        when(connectionService.findActiveProviderKind(WS)).thenReturn(Optional.of(IntegrationKind.GITLAB));
        when(connectionService.findActiveOutlineConfig(WS)).thenReturn(
            Optional.of(new ConnectionConfig.OutlineConfig("https://o.test", "sub-9", "secret", Set.of()))
        );

        NatsSubscriptionInfo info = provider.getSubscriptionInfo(WS).orElseThrow();

        assertThat(info.streamSubscriptions().stream().map(StreamSubscription::streamName)).containsExactlyInAnyOrder(
            "gitlab",
            "outline"
        );
    }

    @Test
    void outlineConnectionWithoutRegisteredSubscriptionIsSkipped() {
        when(connectionService.findActiveProviderKind(WS)).thenReturn(Optional.empty());
        when(connectionService.findActiveOutlineConfig(WS)).thenReturn(
            Optional.of(new ConnectionConfig.OutlineConfig("https://o.test", null, null, Set.of()))
        );

        NatsSubscriptionInfo info = provider.getSubscriptionInfo(WS).orElseThrow();

        assertThat(info.hasSubscriptions()).isFalse();
    }
}
