package de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.tum.cit.aet.hephaestus.integration.core.events.ConnectionLifecycleEvent;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.core.task.AsyncTaskExecutor;

class GitLabConnectionStateListenerTest extends BaseUnitTest {

    @Mock
    private GitLabWorkspaceInitializationService initService;

    @Mock
    private GitLabWebhookService webhookService;

    @Mock
    private AsyncTaskExecutor monitoringExecutor;

    private GitLabConnectionStateListener listener;

    @BeforeEach
    void setUp() {
        listener = new GitLabConnectionStateListener(initService, webhookService, monitoringExecutor);
    }

    /** Makes the mocked executor run submitted work inline so blocking teardown is observable. */
    private void runExecutorSynchronously() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        })
            .when(monitoringExecutor)
            .execute(any(Runnable.class));
    }

    @Test
    void gitlabActivation_dispatchesInitialInitializationOnce() {
        listener.onActivated(new ConnectionLifecycleEvent.Activated(42L, 5L, IntegrationKind.GITLAB));

        verify(initService).initializeAsync(5L);
        verifyNoInteractions(webhookService);
    }

    @Test
    void nonGitlabActivation_isIgnored() {
        listener.onActivated(new ConnectionLifecycleEvent.Activated(42L, 5L, IntegrationKind.GITHUB));

        verifyNoInteractions(initService, webhookService, monitoringExecutor);
    }

    @Test
    void gitlabDeactivation_deregistersWebhookByConnectionId() {
        runExecutorSynchronously();

        listener.onDeactivated(new ConnectionLifecycleEvent.Deactivated(42L, 5L, IntegrationKind.GITLAB));

        verify(webhookService).deregisterWebhookForConnection(5L, 42L);
        verifyNoInteractions(initService);
    }

    @Test
    void nonGitlabDeactivation_isIgnored() {
        listener.onDeactivated(new ConnectionLifecycleEvent.Deactivated(42L, 5L, IntegrationKind.SLACK));

        verifyNoInteractions(initService, webhookService, monitoringExecutor);
    }

    @Test
    void activationFailure_neverPropagatesOffTheEventThread() {
        doThrow(new IllegalStateException("gitlab is down")).when(initService).initializeAsync(5L);

        assertThatCode(() ->
            listener.onActivated(new ConnectionLifecycleEvent.Activated(42L, 5L, IntegrationKind.GITLAB))
        ).doesNotThrowAnyException();
    }
}
