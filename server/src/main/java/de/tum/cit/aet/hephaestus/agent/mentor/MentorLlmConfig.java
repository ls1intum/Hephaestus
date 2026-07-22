package de.tum.cit.aet.hephaestus.agent.mentor;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.ResolvedLlmModel;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import org.jspecify.annotations.Nullable;

/**
 * Slim projection of the LLM fields that {@link MentorPiAdapter} needs to build a sandbox spec.
 * Decouples the mentor module from the JPA {@link AgentConfig} entity.
 *
 * <p>#1368 slice 5: routes through {@link LlmModelResolver} — the same resolved, non-secret
 * behaviour shape ({@code ResolvedLlmModel}) the practice-review path freezes into
 * {@code ConfigSnapshot} — instead of copying {@code AgentConfig.llmProvider}/{@code credentialMode}
 * verbatim. This closes the capability drift the mentor runner previously hardcoded (context window,
 * max output tokens, cache-control format) and gives the mentor the SAME connection-scoped, live
 * credential resolution the proxy performs for one-shot jobs.
 */
public record MentorLlmConfig(
    Long configId,
    String apiProtocol,
    String baseUrl,
    String upstreamModelId,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens,
    boolean supportsReasoning,
    @Nullable String cacheControlFormat,
    @Nullable FundingSource connectionScope,
    @Nullable Long connectionId,
    boolean allowInternet,
    int timeoutSeconds,
    /**
     * The pre-catalog config's OWN raw {@code llmBaseUrl} (only ever non-null when
     * {@code connectionScope} is null) — kept separate from the resolver's already-defaulted
     * {@code baseUrl} so {@code MentorPiAdapter} can reproduce the pre-#1368 precedence: an explicit
     * per-config override wins over the instance-wide {@code MentorAgentProperties.baseUrl()}, which
     * in turn wins over the resolver's hardcoded provider default.
     */
    @Nullable String rawLegacyBaseUrl
) {
    public static MentorLlmConfig fromAgentConfig(AgentConfig config, LlmModelResolver resolver) {
        ResolvedLlmModel resolved = resolver.resolve(config);
        LlmModelResolver.ConnectionRef ref = resolver.connectionRef(config);
        return new MentorLlmConfig(
            config.getId(),
            resolved.apiProtocol(),
            resolved.baseUrl(),
            resolved.upstreamModelId(),
            resolved.contextWindow(),
            resolved.maxOutputTokens(),
            resolved.supportsReasoning(),
            resolved.cacheControlFormat(),
            ref.scope(),
            ref.connectionId(),
            config.isAllowInternet(),
            config.getTimeoutSeconds(),
            ref.scope() == null ? config.getLlmBaseUrl() : null
        );
    }
}
