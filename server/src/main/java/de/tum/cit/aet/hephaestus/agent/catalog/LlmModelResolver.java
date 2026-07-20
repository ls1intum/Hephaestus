package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves an {@link AgentConfig} binding to the effective {@link ResolvedLlmModel} the runtime needs,
 * collapsing the two catalog scopes (instance vs workspace BYO) behind one shape. The credential is
 * resolved separately and live (never frozen) via {@link #resolveCredential}.
 *
 * <p>Precedence: an instance-model binding, else a workspace-model binding, else the legacy
 * {@code AgentConfig.llm*} columns mapped through the old {@link LlmProvider} vocabulary — so configs
 * created before the catalog cut over keep working until they are rebound (deprecate-then-remove).
 */
@Service
public class LlmModelResolver {

    private static final String AZURE_API_VERSION = "2025-04-01-preview";

    /** The effective, non-secret runtime shape for a config's bound (or legacy) model. */
    @Transactional(readOnly = true)
    public ResolvedLlmModel resolve(AgentConfig config) {
        LlmModel instance = config.getInstanceModel();
        if (instance != null) {
            LlmConnection c = instance.getConnection();
            return new ResolvedLlmModel(
                c.getBaseUrl(),
                effectiveProtocol(instance.getApiProtocolOverride(), c.getApiProtocol()),
                c.getAuthHeaderName(),
                c.getAuthValuePrefix(),
                c.getAzureApiVersion(),
                instance.getUpstreamModelId(),
                instance.getContextWindow(),
                instance.getMaxOutputTokens(),
                instance.isSupportsReasoning(),
                instance.getCacheControlFormat(),
                FundingSource.INSTANCE
            );
        }
        WorkspaceLlmModel byo = config.getWorkspaceModel();
        if (byo != null) {
            WorkspaceLlmConnection c = byo.getConnection();
            return new ResolvedLlmModel(
                c.getBaseUrl(),
                effectiveProtocol(byo.getApiProtocolOverride(), c.getApiProtocol()),
                c.getAuthHeaderName(),
                c.getAuthValuePrefix(),
                c.getAzureApiVersion(),
                byo.getUpstreamModelId(),
                byo.getContextWindow(),
                byo.getMaxOutputTokens(),
                byo.isSupportsReasoning(),
                byo.getCacheControlFormat(),
                FundingSource.WORKSPACE
            );
        }
        return legacy(config);
    }

    /** The live API key for a config's bound (or legacy) connection. Never frozen into a snapshot. */
    @Transactional(readOnly = true)
    public @Nullable String resolveCredential(AgentConfig config) {
        LlmModel instance = config.getInstanceModel();
        if (instance != null) {
            return instance.getConnection().getApiKey();
        }
        WorkspaceLlmModel byo = config.getWorkspaceModel();
        if (byo != null) {
            return byo.getConnection().getApiKey();
        }
        return config.getLlmApiKey();
    }

    private static String effectiveProtocol(@Nullable String override, String connectionProtocol) {
        return override != null && !override.isBlank() ? override : connectionProtocol;
    }

    /**
     * Map a pre-catalog {@link AgentConfig} onto a {@link ResolvedLlmModel} using the same
     * provider→(protocol, auth header, upstream URL) rules the migration backfill used, so behaviour
     * is unchanged for configs not yet rebound to the catalog.
     */
    private ResolvedLlmModel legacy(AgentConfig config) {
        LlmProvider provider = config.getLlmProvider();
        String apiProtocol;
        String authHeaderName;
        String authValuePrefix;
        String azureApiVersion = null;
        String defaultBaseUrl;
        switch (provider) {
            case ANTHROPIC -> {
                apiProtocol = "anthropic-messages";
                authHeaderName = "x-api-key";
                authValuePrefix = "";
                defaultBaseUrl = "https://api.anthropic.com";
            }
            case AZURE_OPENAI -> {
                apiProtocol = "azure-openai-responses";
                authHeaderName = "api-key";
                authValuePrefix = "";
                azureApiVersion = AZURE_API_VERSION;
                defaultBaseUrl = "";
            }
            default -> {
                apiProtocol = "openai-completions";
                authHeaderName = "Authorization";
                authValuePrefix = "Bearer ";
                defaultBaseUrl = "https://api.openai.com";
            }
        }
        String baseUrl =
            config.getLlmBaseUrl() != null && !config.getLlmBaseUrl().isBlank()
                ? config.getLlmBaseUrl()
                : defaultBaseUrl;
        return new ResolvedLlmModel(
            baseUrl,
            apiProtocol,
            authHeaderName,
            authValuePrefix,
            azureApiVersion,
            config.getModelName() != null ? config.getModelName() : "",
            null,
            null,
            false,
            null,
            FundingSource.INSTANCE
        );
    }
}
