package de.tum.in.www1.hephaestus.agent.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers all {@link AgentAdapter} beans and the {@link AgentAdapterRegistry}.
 *
 * <p>Adapters are always available — they are pure translation logic with no infrastructure
 * dependencies. Unlike the sandbox subsystem (conditional on {@code hephaestus.sandbox.enabled}),
 * adapter beans are unconditionally created.
 */
@Configuration
public class AgentAdapterConfiguration {

    @Bean
    public AgentAdapter claudeCodeAgentAdapter() {
        return new ClaudeCodeAgentAdapter();
    }

    @Bean
    public AgentAdapter openCodeAgentAdapter(ObjectMapper objectMapper) {
        return new OpenCodeAgentAdapter(objectMapper);
    }

    @Bean
    public AgentAdapterRegistry agentAdapterRegistry(List<AgentAdapter> adapters) {
        return new AgentAdapterRegistry(adapters);
    }
}
