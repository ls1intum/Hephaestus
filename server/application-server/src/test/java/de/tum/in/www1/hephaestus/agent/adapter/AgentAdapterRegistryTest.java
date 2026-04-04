package de.tum.in.www1.hephaestus.agent.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapter;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AgentAdapterRegistry")
class AgentAdapterRegistryTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentAdapter claudeAdapter = new ClaudeCodeAgentAdapter(objectMapper);
    private final AgentAdapter openCodeAdapter = new OpenCodeAgentAdapter(objectMapper);
    private final AgentAdapter piAdapter = new PiAgentAdapter(objectMapper);

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should index adapters by agent type")
        void shouldIndexAdaptersByAgentType() {
            var registry = new AgentAdapterRegistry(List.of(claudeAdapter, openCodeAdapter, piAdapter));
            assertThat(registry.getAdapter(AgentType.CLAUDE_CODE)).isSameAs(claudeAdapter);
            assertThat(registry.getAdapter(AgentType.OPENCODE)).isSameAs(openCodeAdapter);
            assertThat(registry.getAdapter(AgentType.PI)).isSameAs(piAdapter);
        }

        @Test
        @DisplayName("should throw on duplicate adapter for same type")
        void shouldThrowOnDuplicateAdapter() {
            assertThatThrownBy(() ->
                new AgentAdapterRegistry(List.of(claudeAdapter, openCodeAdapter, piAdapter, claudeAdapter))
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
        }

        @Test
        @DisplayName("should throw when agent type has no adapter")
        void shouldThrowOnMissingAdapter() {
            assertThatThrownBy(() -> new AgentAdapterRegistry(List.of(claudeAdapter, openCodeAdapter)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PI");
        }
    }

    @Nested
    @DisplayName("getAdapter")
    class GetAdapter {

        @Test
        @DisplayName("should return correct adapter for each type")
        void shouldReturnCorrectAdapterForEachType() {
            var registry = new AgentAdapterRegistry(List.of(claudeAdapter, openCodeAdapter, piAdapter));

            for (AgentType type : AgentType.values()) {
                AgentAdapter adapter = registry.getAdapter(type);
                assertThat(adapter).isNotNull();
                assertThat(adapter.agentType()).isEqualTo(type);
            }
        }

        @Test
        @DisplayName("should reject null agent type")
        void shouldRejectNullAgentType() {
            var registry = new AgentAdapterRegistry(List.of(claudeAdapter, openCodeAdapter, piAdapter));
            assertThatThrownBy(() -> registry.getAdapter(null)).isInstanceOf(NullPointerException.class);
        }
    }
}
