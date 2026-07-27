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
 * Decouples the mentor module from the workspace's agent binding.
 *
 * <p>Routes through {@link LlmModelResolver} — the same resolved, non-secret behaviour shape
 * ({@code ResolvedLlmModel}) the practice-review path freezes into {@code ConfigSnapshot}. It carries
 * the resolved capabilities (context window, max output tokens) rather than having the mentor runner
 * hardcode them, and gives the mentor the SAME connection-scoped, live credential resolution the
 * proxy performs for one-shot jobs.
 */
public record MentorLlmConfig(
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
    public static MentorLlmConfig fromAdmission(ModelBindingSource config, AdmittedLlmModel admitted) {
        ResolvedLlmModel resolved = admitted.resolved();
        LlmModelResolver.ConnectionRef ref = admitted.connection();
        return new MentorLlmConfig(
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
