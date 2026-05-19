package de.tum.in.www1.hephaestus.workspace.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.GitLabWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("GitLabWebhookPurgeAdapter")
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
        // Given
        Long workspaceId = 42L;

        // When
        adapter.deleteWorkspaceData(workspaceId);

        // Then
        verify(webhookService).deregisterWebhookByWorkspaceId(workspaceId);
    }

    @Test
    void deleteWorkspaceData_doesNotPropagateExceptions() {
        // Given
        Long workspaceId = 99L;
        doThrow(new RuntimeException("GitLab API unreachable"))
            .when(webhookService)
            .deregisterWebhookByWorkspaceId(workspaceId);

        // When — should not throw
        adapter.deleteWorkspaceData(workspaceId);

        // Then
        verify(webhookService).deregisterWebhookByWorkspaceId(workspaceId);
    }

    @Test
    void getOrder_returnsExpectedPurgeOrder() {
        // Webhook deregistration must run before repo monitors and git clones
        assertThat(adapter.getOrder()).isEqualTo(50);
    }
}
