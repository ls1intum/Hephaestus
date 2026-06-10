package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Seeds one workspace-scoped {@link AgentConfig} at startup so a fresh local boot has a working
 * model without clicking through the admin UI. Off by default — enable for development with
 * {@code hephaestus.agent.default-config.enabled=true} and an {@code api-key}.
 *
 * <p>The seeded config uses PROXY credential mode (the only workspace-facing mode): the key is
 * stored encrypted and injected by the LLM proxy, never reaching the sandbox. A single enabled
 * config is picked up automatically by practice detection, so no explicit binding is needed.
 */
@ConfigurationProperties(prefix = "hephaestus.agent.default-config")
public record DefaultAgentConfigProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("Default model") String name,
    @DefaultValue("ANTHROPIC") LlmProvider provider,
    @Nullable String modelName,
    @Nullable String apiKey
) {}
