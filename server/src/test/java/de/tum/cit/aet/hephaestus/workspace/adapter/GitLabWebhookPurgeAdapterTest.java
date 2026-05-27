package de.tum.cit.aet.hephaestus.workspace.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.GitLabWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class GitLabWebhookPurgeAdapterTest extends BaseUnitTest {

    @Mock
    private GitLabWebhookService webhookService;

    private GitLabWebhookPurgeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new GitLabWebhookPurgeAdapter(webhookService);
    }

    @Test
    void deleteWorkspaceData_callsDeregisterWebhookByWorkspaceId() {
        Long workspaceId = 42L;

        adapter.deleteWorkspaceData(workspaceId);

        verify(webhookService).deregisterWebhookByWorkspaceId(workspaceId);
    }

    @Test
    void deleteWorkspaceData_doesNotPropagateExceptions() {
        Long workspaceId = 99L;
        doThrow(new RuntimeException("GitLab API unreachable"))
            .when(webhookService)
            .deregisterWebhookByWorkspaceId(workspaceId);

        // When — should not throw
        adapter.deleteWorkspaceData(workspaceId);

        verify(webhookService).deregisterWebhookByWorkspaceId(workspaceId);
    }

    @Test
    void getOrder_returnsExpectedPurgeOrder() {
        // Webhook deregistration must run before repo monitors and git clones
        assertThat(adapter.getOrder()).isEqualTo(50);
    }
}
