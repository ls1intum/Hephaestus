package de.tum.in.www1.hephaestus.agent.proxy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the LLM proxy controller.
 *
 * <p>Upstream URLs can be overridden for testing (e.g., WireMock) or to point at
 * alternative API-compatible endpoints (e.g., Azure OpenAI).
 *
 * <p>For Azure OpenAI, set:
 * <pre>
 * hephaestus.llm-proxy.openai-upstream-url=https://YOUR_RESOURCE.openai.azure.com
 * hephaestus.llm-proxy.openai-auth-header=api-key
 * hephaestus.llm-proxy.openai-use-bearer-prefix=false
 * </pre>
 */
@ConfigurationProperties(prefix = "hephaestus.llm-proxy")
public record LlmProxyProperties(
    @DefaultValue("https://api.anthropic.com") String anthropicUpstreamUrl,
    @DefaultValue("https://api.openai.com") String openaiUpstreamUrl,
    @DefaultValue("Authorization") String openaiAuthHeader,
    @DefaultValue("true") boolean openaiUseBearerPrefix
) {}
