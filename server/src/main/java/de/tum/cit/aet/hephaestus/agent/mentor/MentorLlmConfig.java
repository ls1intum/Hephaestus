package de.tum.cit.aet.hephaestus.agent.mentor;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelBindingSource;
import de.tum.cit.aet.hephaestus.agent.catalog.ResolvedLlmModel;
import de.tum.cit.aet.hephaestus.agent.usage.AdmittedLlmModel;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import org.jspecify.annotations.Nullable;

/**
 * Slim projection of the LLM fields that {@link MentorPiAdapter} needs to build a sandbox spec.
 * Decouples the mentor module from the JPA AgentConfig entity.
 *
 * <p>#1368 slice 5: routes through {@link LlmModelResolver} — the same resolved, non-secret
 * behaviour shape ({@code ResolvedLlmModel}) the practice-review path freezes into
 * {@code ConfigSnapshot} — instead of copying {@code AgentConfig.llmProvider}/{@code credentialMode}
 * verbatim. This closes the capability drift the mentor runner previously hardcoded (context window,
 * max output tokens) and gives the mentor the SAME connection-scoped, live
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
    @Nullable FundingSource connectionScope,
    @Nullable Long connectionId,
    @Nullable Long modelId,
    @Nullable Long workspaceId,
    @Nullable LlmPriceSnapshot priceSnapshot,
    boolean allowInternet,
    int timeoutSeconds
) {
    public MentorLlmConfig(
        Long configId,
        String apiProtocol,
        String baseUrl,
        String upstreamModelId,
        @Nullable Integer contextWindow,
        @Nullable Integer maxOutputTokens,
        boolean supportsReasoning,
        @Nullable FundingSource connectionScope,
        @Nullable Long connectionId,
        boolean allowInternet,
        int timeoutSeconds
    ) {
        this(
            configId,
            apiProtocol,
            baseUrl,
            upstreamModelId,
            contextWindow,
            maxOutputTokens,
            supportsReasoning,
            connectionScope,
            connectionId,
            null,
            null,
            null,
            allowInternet,
            timeoutSeconds
        );
    }

    public static MentorLlmConfig fromAgentConfig(ModelBindingSource config, LlmModelResolver resolver) {
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
            ref.scope(),
            ref.connectionId(),
            ref.modelId(),
            ref.workspaceId(),
            null,
            config.isAllowInternet(),
            config.getTimeoutSeconds()
        );
    }

    public static MentorLlmConfig fromAdmission(ModelBindingSource config, AdmittedLlmModel admitted) {
        ResolvedLlmModel resolved = admitted.resolved();
        LlmModelResolver.ConnectionRef ref = admitted.connection();
        return new MentorLlmConfig(
            config.getId(),
            resolved.apiProtocol(),
            resolved.baseUrl(),
            resolved.upstreamModelId(),
            resolved.contextWindow(),
            resolved.maxOutputTokens(),
            resolved.supportsReasoning(),
            ref.scope(),
            ref.connectionId(),
            ref.modelId(),
            ref.workspaceId(),
            admitted.price(),
            config.isAllowInternet(),
            config.getTimeoutSeconds()
        );
    }
}
