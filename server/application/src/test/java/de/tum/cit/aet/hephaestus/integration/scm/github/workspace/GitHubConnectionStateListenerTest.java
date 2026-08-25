package de.tum.cit.aet.hephaestus.integration.scm.github.workspace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.events.ConnectionLifecycleEvent;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.core.task.AsyncTaskExecutor;

class GitHubConnectionStateListenerTest extends BaseUnitTest {

    @Mock
    private ConnectionService connectionService;

    @Mock
    private GitHubWorkspaceDataSyncTrigger dataSyncTrigger;

    @Mock
    private AsyncTaskExecutor monitoringExecutor;

    private GitHubConnectionStateListener listener;

    @BeforeEach
    void setUp() {
        listener = new GitHubConnectionStateListener(connectionService, dataSyncTrigger, monitoringExecutor);
    }

    private void runExecutorSynchronously() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        })
            .when(monitoringExecutor)
            .execute(any(Runnable.class));
    }

    @Test
    void patActivation_recordsInitialSyncOnce() {
        runExecutorSynchronously();
        when(connectionService.findActiveGitHubPatConfig(5L)).thenReturn(
            Optional.of(new ConnectionConfig.GitHubPatConfig("acme", null, Set.of()))
        );

        listener.onActivated(new ConnectionLifecycleEvent.Activated(42L, 5L, IntegrationKind.GITHUB));

        verify(dataSyncTrigger).syncAllRepositories(5L);
    }

    @Test
    void appActivation_isNotSyncedHere() {
        // No PAT config → this is an App connection, whose initial sync is owned by the installation webhook.
        when(connectionService.findActiveGitHubPatConfig(5L)).thenReturn(Optional.empty());

        listener.onActivated(new ConnectionLifecycleEvent.Activated(42L, 5L, IntegrationKind.GITHUB));

        verify(dataSyncTrigger, never()).syncAllRepositories(anyLong());
        verifyNoInteractions(monitoringExecutor);
    }

    @Test
    void nonGithubActivation_isIgnored() {
        listener.onActivated(new ConnectionLifecycleEvent.Activated(42L, 5L, IntegrationKind.GITLAB));

        verifyNoInteractions(connectionService, dataSyncTrigger, monitoringExecutor);
    }
}
