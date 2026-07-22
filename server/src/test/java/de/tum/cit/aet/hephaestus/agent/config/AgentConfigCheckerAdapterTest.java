package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.ResolvedLlmModel;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class AgentConfigCheckerAdapterTest extends BaseUnitTest {

    @Mock
    private AgentConfigRepository repository;

    @Mock
    private LlmModelResolver resolver;

    private AgentConfigCheckerAdapter checker;

    @BeforeEach
    void setUp() {
        checker = new AgentConfigCheckerAdapter(repository, resolver);
    }

    @Test
    void boundConfigIsNotRunnableAfterItsModelIsRevoked() {
        AgentConfig config = enabledConfig(7L);
        when(repository.findByIdAndWorkspaceId(7L, 1L)).thenReturn(Optional.of(config));
        when(resolver.resolve(config)).thenThrow(new IllegalStateException("unavailable"));

        assertThat(checker.hasRunnablePracticeConfig(1L, 7L)).isFalse();
    }

    @Test
    void fanOutRemainsRunnableWhenAtLeastOneConfigHasAnAvailableModel() {
        AgentConfig revoked = enabledConfig(7L);
        AgentConfig available = enabledConfig(8L);
        when(repository.findByWorkspaceId(1L)).thenReturn(List.of(revoked, available));
        when(resolver.resolve(revoked)).thenThrow(new IllegalStateException("unavailable"));
        when(resolver.resolve(available)).thenReturn(org.mockito.Mockito.mock(ResolvedLlmModel.class));

        assertThat(checker.hasRunnablePracticeConfig(1L, null)).isTrue();
    }

    private static AgentConfig enabledConfig(Long id) {
        AgentConfig config = new AgentConfig();
        config.setId(id);
        config.setEnabled(true);
        return config;
    }
}
