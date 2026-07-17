package de.tum.cit.aet.hephaestus.workspace.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.consumer.IntegrationNatsConsumer;
import de.tum.cit.aet.hephaestus.integration.core.consumer.NatsConnectionProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceScopeFilter;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit tests for {@link WorkspaceSyncTargetProvider#reconcileSyncTargetIdentity}, the correctness
 * backstop that re-keys a monitor's {@code nameWithOwner} by its stable native id after an upstream
 * rename/transfer and refreshes the workspace's NATS consumer so events under the new name are
 * delivered instead of silently ACK-dropped.
 */
class WorkspaceSyncTargetProviderReconcileTest extends BaseUnitTest {

    private static final long SYNC_TARGET_ID = 42L;
    private static final long WORKSPACE_ID = 1L;
    private static final long NATIVE_ID = 555L;
    private static final String OLD_NAME = "acme/old-widgets";
    private static final String NEW_NAME = "acme/new-widgets";

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private WorkspaceScopeFilter workspaceScopeFilter;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private ObjectProvider<IntegrationNatsConsumer> natsConsumerService;

    @Mock
    private IntegrationNatsConsumer natsConsumer;

    @Mock
    private Workspace workspace;

    private WorkspaceSyncTargetProvider provider;

    @BeforeEach
    void setUp() {
        provider = new WorkspaceSyncTargetProvider(
            workspaceRepository,
            repositoryToMonitorRepository,
            workspaceScopeFilter,
            connectionService,
            new NatsConnectionProperties(true, "nats://localhost:4222", null, 7, null),
            natsConsumerService
        );
    }

    private RepositoryToMonitor monitor(Long nativeId, String nameWithOwner) {
        RepositoryToMonitor rtm = new RepositoryToMonitor();
        rtm.setNativeId(nativeId);
        rtm.setNameWithOwner(nameWithOwner);
        rtm.setWorkspace(workspace);
        return rtm;
    }

    @Test
    void shouldRekeyNameAndRefreshConsumerWhenIdAgreesButNameChanged() {
        RepositoryToMonitor rtm = monitor(NATIVE_ID, OLD_NAME);
        when(repositoryToMonitorRepository.findById(SYNC_TARGET_ID)).thenReturn(Optional.of(rtm));
        when(workspace.getId()).thenReturn(WORKSPACE_ID);
        doAnswer(inv -> {
            Consumer<IntegrationNatsConsumer> c = inv.getArgument(0);
            c.accept(natsConsumer);
            return null;
        })
            .when(natsConsumerService)
            .ifAvailable(any());

        provider.reconcileSyncTargetIdentity(SYNC_TARGET_ID, NATIVE_ID, NEW_NAME);

        assertThat(rtm.getNameWithOwner()).isEqualTo(NEW_NAME);
        verify(repositoryToMonitorRepository).save(rtm);
        verify(natsConsumer).updateScopeConsumer(WORKSPACE_ID);
    }

    @Test
    void shouldCaptureNativeIdForLegacyRowWithoutRenamingWhenNameUnchanged() {
        RepositoryToMonitor rtm = monitor(null, OLD_NAME);
        when(repositoryToMonitorRepository.findById(SYNC_TARGET_ID)).thenReturn(Optional.of(rtm));

        provider.reconcileSyncTargetIdentity(SYNC_TARGET_ID, NATIVE_ID, OLD_NAME);

        assertThat(rtm.getNativeId()).isEqualTo(NATIVE_ID);
        assertThat(rtm.getNameWithOwner()).isEqualTo(OLD_NAME);
        verify(repositoryToMonitorRepository).save(rtm);
        // Name did not move, so no consumer rebuild.
        verify(natsConsumerService, never()).ifAvailable(any());
    }

    @Test
    void shouldNotRenameWhenStableIdDisagrees() {
        RepositoryToMonitor rtm = monitor(111L, OLD_NAME);
        when(repositoryToMonitorRepository.findById(SYNC_TARGET_ID)).thenReturn(Optional.of(rtm));

        provider.reconcileSyncTargetIdentity(SYNC_TARGET_ID, 222L, NEW_NAME);

        // A different id means a different repository — never rename by name alone.
        assertThat(rtm.getNameWithOwner()).isEqualTo(OLD_NAME);
        verify(repositoryToMonitorRepository, never()).save(any());
        verify(natsConsumerService, never()).ifAvailable(any());
    }

    @Test
    void shouldBeNoOpWhenSyncTargetMissing() {
        when(repositoryToMonitorRepository.findById(SYNC_TARGET_ID)).thenReturn(Optional.empty());

        provider.reconcileSyncTargetIdentity(SYNC_TARGET_ID, NATIVE_ID, NEW_NAME);

        verify(repositoryToMonitorRepository, never()).save(any());
        verify(natsConsumerService, never()).ifAvailable(any());
    }
}
