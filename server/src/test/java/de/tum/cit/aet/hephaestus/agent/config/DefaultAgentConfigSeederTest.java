package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

class DefaultAgentConfigSeederTest extends BaseUnitTest {

    @Mock
    private AgentConfigService agentConfigService;

    @Mock
    private AgentConfigRepository agentConfigRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private DefaultAgentConfigSeeder seeder(boolean enabled, String apiKey) {
        var props = new DefaultAgentConfigProperties(enabled, "Default model", LlmProvider.ANTHROPIC, null, apiKey);
        return new DefaultAgentConfigSeeder(props, agentConfigService, agentConfigRepository, workspaceRepository);
    }

    @Test
    void disabled_doesNothing() {
        seeder(false, "sk-test").seed();
        verifyNoInteractions(workspaceRepository, agentConfigRepository, agentConfigService);
    }

    @Test
    void enabledWithoutKey_skipsBeforeTouchingTheDb() {
        seeder(true, "  ").seed();
        verifyNoInteractions(workspaceRepository, agentConfigRepository, agentConfigService);
    }

    @Test
    void noWorkspace_skips() {
        when(workspaceRepository.findAll()).thenReturn(List.of());
        seeder(true, "sk-test").seed();
        verify(agentConfigService, never()).createConfig(any(), any());
    }

    @Test
    void alreadyExists_skips() {
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(agentConfigRepository.existsByWorkspaceIdAndName(any(), any())).thenReturn(true);
        seeder(true, "sk-test").seed();
        verify(agentConfigService, never()).createConfig(any(), any());
    }

    @Test
    void happyPath_createsOneEnabledProxyConfig() {
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(agentConfigRepository.existsByWorkspaceIdAndName(any(), any())).thenReturn(false);
        when(agentConfigService.createConfig(any(), any())).thenReturn(new AgentConfig());

        seeder(true, "sk-test").seed();

        var captor = ArgumentCaptor.forClass(CreateAgentConfigRequestDTO.class);
        verify(agentConfigService).createConfig(any(), captor.capture());
        assertThat(captor.getValue().credentialMode()).isEqualTo(CredentialMode.PROXY);
        assertThat(captor.getValue().enabled()).isTrue();
        assertThat(captor.getValue().name()).isEqualTo("Default model");
    }

    @Test
    void createConfigFailure_isIsolatedAndDoesNotThrow() {
        // The publisher runs workspace activation right after this listener, so a throw must not escape.
        when(workspaceRepository.findAll()).thenReturn(List.of(new Workspace()));
        when(agentConfigRepository.existsByWorkspaceIdAndName(any(), any())).thenReturn(false);
        when(agentConfigService.createConfig(any(), any())).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> seeder(true, "sk-test").seed()).doesNotThrowAnyException();
    }
}
