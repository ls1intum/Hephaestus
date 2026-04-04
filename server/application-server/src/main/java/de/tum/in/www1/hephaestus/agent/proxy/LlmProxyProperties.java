package de.tum.in.www1.hephaestus.agent.proxy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the LLM proxy controller.
 *
 * <p>Upstream URLs can be overridden for testing (e.g., WireMock) or to point at
 * alternative API-compatible endpoints (e.g., Azure OpenAI).
 *
 * <p>For Azure OpenAI via the dedicated {@code azure_openai} proxy route, set:
 * <pre>
 * hephaestus.llm-proxy.azure-openai-upstream-url=https://YOUR_RESOURCE.openai.azure.com
 * </pre>
 *
 * <p>The Azure route uses {@code api-key} header without Bearer prefix by default.
 */
@ConfigurationProperties(prefix = "hephaestus.llm-proxy")
public record LlmProxyProperties(
    @DefaultValue("https://api.anthropic.com") String anthropicUpstreamUrl,
    @DefaultValue("https://api.openai.com") String openaiUpstreamUrl,
    @DefaultValue("Authorization") String openaiAuthHeader,
    @DefaultValue("true") boolean openaiUseBearerPrefix,
    @DefaultValue("") String azureOpenaiUpstreamUrl,
    @DefaultValue("api-key") String azureOpenaiAuthHeader,
    @DefaultValue("false") boolean azureOpenaiUseBearerPrefix
) {}
