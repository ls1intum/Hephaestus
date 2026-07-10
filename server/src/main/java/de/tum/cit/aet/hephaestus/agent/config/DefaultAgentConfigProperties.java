package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Seeds one workspace-scoped {@link AgentConfig} at startup so a fresh local boot has a working model
 * without the admin UI. Off by default — enable with {@code hephaestus.agent.default-config.enabled=true}
 * and an {@code api-key}. PROXY mode: the key is stored encrypted and injected by the proxy, never
 * reaching the sandbox (ADR 0006).
 */
@ConfigurationProperties(prefix = "hephaestus.agent.default-config")
public record DefaultAgentConfigProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("Default model") String name,
    @DefaultValue("ANTHROPIC") LlmProvider provider,
    @Nullable String modelName,
    @Nullable String apiKey,
    /** Optional OpenAI-compatible endpoint (self-hosted / proxy models); null uses the provider's default. */
    @Nullable String baseUrl
) {}
