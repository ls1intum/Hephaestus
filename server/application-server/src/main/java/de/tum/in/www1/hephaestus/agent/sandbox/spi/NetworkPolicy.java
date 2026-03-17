package de.tum.in.www1.hephaestus.agent.sandbox.spi;

/**
 * Network access policy for a sandboxed container.
 *
 * <p>When {@code internetAccess} is {@code false}, the container runs on a Docker {@code
 * --internal} network with zero external connectivity. The only reachable endpoint is the LLM proxy
 * at the given URL, accessed via app-server multi-homing on the job network.
 *
 * @param internetAccess        whether the container may reach the public internet
 * @param llmProxyUrl           full URL to the LLM proxy endpoint (injected as env var)
 * @param llmProxyToken         job-scoped authentication token for the proxy
 * @param llmProxyProviderPath  provider path segment appended to proxy base URL (e.g., "anthropic", "openai")
 */
public record NetworkPolicy(
    boolean internetAccess,
    String llmProxyUrl,
    String llmProxyToken,
    String llmProxyProviderPath
) {}
