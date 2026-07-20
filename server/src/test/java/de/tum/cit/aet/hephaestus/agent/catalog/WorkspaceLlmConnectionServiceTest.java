package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditAction;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class WorkspaceLlmConnectionServiceTest extends BaseUnitTest {

    @Mock
    private WorkspaceLlmConnectionRepository connectionRepository;

    @Mock
    private WorkspaceLlmModelRepository modelRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private EgressPolicy egressPolicy;

    @Mock
    private InstanceLlmSettingsService instanceLlmSettingsService;

    @Mock
    private LlmConnectionProbeService probeService;

    @Mock
    private ConfigAuditPort configAudit;

    @InjectMocks
    private WorkspaceLlmConnectionService connectionService;

    private WorkspaceContext workspaceContext;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        workspace.setId(1L);
        workspace.setWorkspaceSlug("test-workspace");
        workspaceContext = new WorkspaceContext(
            1L,
            "test-workspace",
            "Test Workspace",
            AccountType.ORG,
            null,
            false,
            false,
            Set.of()
        );
    }

    private void byoEnabled(boolean enabled) {
        InstanceLlmSettings settings = new InstanceLlmSettings();
        settings.setAllowWorkspaceConnections(enabled);
        when(instanceLlmSettingsService.get()).thenReturn(settings);
    }

    private CreateWorkspaceLlmConnectionRequest createRequest() {
        return new CreateWorkspaceLlmConnectionRequest(
            "openai-prod",
            "OpenAI",
            "https://api.openai.com",
            "openai-completions",
            null,
            null,
            "sk-abc",
            null,
            null
        );
    }

    @Nested
    class ByoGate {

        @Test
        void createIsRejectedWhenWorkspaceConnectionsAreDisabled() {
            byoEnabled(false);

            assertThatThrownBy(() -> connectionService.create(workspaceContext, createRequest())).isInstanceOf(
                AccessForbiddenException.class
            );
            verify(connectionRepository, never()).save(any());
        }

        @Test
        void deleteIsRejectedWhenWorkspaceConnectionsAreDisabled() {
            byoEnabled(false);

            assertThatThrownBy(() -> connectionService.delete(workspaceContext, 5L)).isInstanceOf(
                AccessForbiddenException.class
            );
            verify(connectionRepository, never()).delete(any());
        }

        @Test
        void probeIsRejectedWhenWorkspaceConnectionsAreDisabled() {
            byoEnabled(false);

            assertThatThrownBy(() -> connectionService.probe(workspaceContext, 5L)).isInstanceOf(
                AccessForbiddenException.class
            );
        }

        @Test
        void listRemainsAvailableWhenWorkspaceConnectionsAreDisabled() {
            // Viewing must survive an instance admin later disabling the feature, or existing connections
            // become inexplicably invisible to the workspace that owns them.
            when(connectionRepository.findByWorkspaceId(1L)).thenReturn(List.of());

            List<WorkspaceLlmConnection> result = connectionService.list(workspaceContext);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class Create {

        @Test
        void rejectsDuplicateSlugInTheSameWorkspace() {
            byoEnabled(true);
            when(connectionRepository.findByWorkspaceIdAndSlug(1L, "openai-prod")).thenReturn(
                Optional.of(new WorkspaceLlmConnection())
            );

            assertThatThrownBy(() -> connectionService.create(workspaceContext, createRequest())).isInstanceOf(
                LlmConnectionSlugConflictException.class
            );
            verify(connectionRepository, never()).save(any());
        }

        @Test
        void validatesTheBaseUrlThroughEgressPolicy() {
            byoEnabled(true);
            when(connectionRepository.findByWorkspaceIdAndSlug(1L, "openai-prod")).thenReturn(Optional.empty());
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            connectionService.create(workspaceContext, createRequest());

            verify(egressPolicy).validate("https://api.openai.com");
        }

        @Test
        void createdConnectionIsScopedToTheCallingWorkspace() {
            byoEnabled(true);
            when(connectionRepository.findByWorkspaceIdAndSlug(1L, "openai-prod")).thenReturn(Optional.empty());
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkspaceLlmConnection result = connectionService.create(workspaceContext, createRequest());

            assertThat(result.getWorkspace()).isEqualTo(workspace);
            assertThat(result.getSlug()).isEqualTo("openai-prod");
        }

        @Test
        void createdConnectionIsRecordedOnTheWorkspaceConfigAuditTrailWithoutTheApiKey() {
            byoEnabled(true);
            when(connectionRepository.findByWorkspaceIdAndSlug(1L, "openai-prod")).thenReturn(Optional.empty());
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            connectionService.create(workspaceContext, createRequest());

            ArgumentCaptor<ConfigAuditEntry> entry = ArgumentCaptor.forClass(ConfigAuditEntry.class);
            verify(configAudit).record(entry.capture());
            assertThat(entry.getValue().entityType()).isEqualTo(ConfigAuditEntityType.WORKSPACE_LLM_CONNECTION);
            assertThat(entry.getValue().workspaceId()).isEqualTo(1L);
            assertThat(entry.getValue().action()).isEqualTo(ConfigAuditAction.CREATED);
            assertThat(entry.getValue().after()).asString().contains("openai-prod").doesNotContain("sk-abc");
        }
    }

    @Nested
    class Delete {

        @Test
        void rejectsDeleteWhileAWorkspaceModelStillReferencesTheConnection() {
            byoEnabled(true);
            WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
            connection.setId(5L);
            when(connectionRepository.findByIdAndWorkspaceId(5L, 1L)).thenReturn(Optional.of(connection));
            when(modelRepository.existsByConnectionIdAndWorkspaceId(5L, 1L)).thenReturn(true);

            assertThatThrownBy(() -> connectionService.delete(workspaceContext, 5L)).isInstanceOf(
                LlmConnectionInUseException.class
            );
            verify(connectionRepository, never()).delete(any());
        }

        @Test
        void deletesAnUnreferencedConnection() {
            byoEnabled(true);
            WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
            connection.setId(5L);
            connection.setSlug("openai-prod");
            when(connectionRepository.findByIdAndWorkspaceId(5L, 1L)).thenReturn(Optional.of(connection));
            when(modelRepository.existsByConnectionIdAndWorkspaceId(5L, 1L)).thenReturn(false);

            connectionService.delete(workspaceContext, 5L);

            verify(connectionRepository).delete(connection);
            ArgumentCaptor<ConfigAuditEntry> entry = ArgumentCaptor.forClass(ConfigAuditEntry.class);
            verify(configAudit).record(entry.capture());
            assertThat(entry.getValue().entityType()).isEqualTo(ConfigAuditEntityType.WORKSPACE_LLM_CONNECTION);
            assertThat(entry.getValue().action()).isEqualTo(ConfigAuditAction.DELETED);
        }
    }

    @Nested
    class Get {

        @Test
        void throwsNotFoundForAConnectionOwnedByAnotherWorkspace() {
            // Never trust a client-supplied id: the tenancy-scoped lookup must miss, not fall through to
            // a bare findById that could return a foreign workspace's connection.
            when(connectionRepository.findByIdAndWorkspaceId(5L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> connectionService.get(workspaceContext, 5L)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }

    @Nested
    class Probe {

        @Test
        void reachableProbeReportsOnlyTheModelCountNeverTheRawList() {
            byoEnabled(true);
            WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
            connection.setId(5L);
            connection.setBaseUrl("https://api.openai.com");
            connection.setAuthHeaderName("Authorization");
            connection.setAuthValuePrefix("Bearer ");
            connection.setApiKey("sk-abc");
            when(connectionRepository.findByIdAndWorkspaceId(5L, 1L)).thenReturn(Optional.of(connection));
            when(
                probeService.probeCredential("https://api.openai.com", "Authorization", "Bearer ", "sk-abc")
            ).thenReturn(LlmProbeResult.reachable(List.of("gpt-5", "gpt-5-mini"), 200));

            WorkspaceLlmProbeResult result = connectionService.probe(workspaceContext, 5L);

            assertThat(result.reachable()).isTrue();
            assertThat(result.modelCount()).isEqualTo(2);
        }

        @Test
        void unreachableProbeCarriesTheAdvisoryMessage() {
            byoEnabled(true);
            WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
            connection.setId(5L);
            connection.setBaseUrl("https://api.openai.com");
            when(connectionRepository.findByIdAndWorkspaceId(5L, 1L)).thenReturn(Optional.of(connection));
            when(probeService.probeCredential(any(), any(), any(), any())).thenReturn(
                LlmProbeResult.unreachable(503, "Provider returned HTTP 503")
            );

            WorkspaceLlmProbeResult result = connectionService.probe(workspaceContext, 5L);

            assertThat(result.reachable()).isFalse();
            assertThat(result.modelCount()).isEqualTo(0);
            assertThat(result.message()).isEqualTo("Provider returned HTTP 503");
        }
    }
}
