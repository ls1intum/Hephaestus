package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class LlmModelResolver {

    private static final String AZURE_API_VERSION = "2025-04-01-preview";
    private static final String AZURE_PROTOCOL = "azure-openai-responses";
    private static final String ANTHROPIC_PROTOCOL = "anthropic-messages";

    private final LlmConnectionRepository llmConnectionRepository;
    private final WorkspaceLlmConnectionRepository workspaceLlmConnectionRepository;
    private final AgentConfigRepository agentConfigRepository;

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

    /**
     * Identifies WHICH connection row funds a config's binding, without exposing any credential
     * material (#1368 slice 5). Frozen into {@link de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot}
     * so the proxy can re-resolve the live credential for an in-flight job without re-reading the
     * config's (possibly since-changed) current binding. Both components {@code null} means "legacy
     * config, no catalog binding" — {@link #resolveProxyCredential} then falls back to
     * {@code AgentConfig.llmApiKey}.
     */
    public record ConnectionRef(@Nullable FundingSource scope, @Nullable Long connectionId) {}

    /**
     * The live routing + credential material the proxy injects — resolved TOGETHER from the live
     * connection row (#1368 security fix wave). {@code baseUrl} deliberately does NOT come from the
     * job's frozen {@code ConfigSnapshot}: repointing a connection's host must not be split-brained
     * against its rotated key (an old-host + new-key mismatch would otherwise leak the new credential to
     * whatever now answers at the stale host). {@code apiKey} is {@code null} for a deliberately keyless
     * connection (self-hosted vLLM/Ollama gateways) — the caller forwards without an auth header rather
     * than refusing.
     */
    public record ProxyCredential(
        String baseUrl,
        String authHeaderName,
        String authValuePrefix,
        @Nullable String azureApiVersion,
        @Nullable String apiKey
    ) {}

    /** Which connection (if any) funds this config's current binding. See {@link ConnectionRef}. */
    @Transactional(readOnly = true)
    public ConnectionRef connectionRef(AgentConfig config) {
        LlmModel instance = config.getInstanceModel();
        if (instance != null) {
            return new ConnectionRef(FundingSource.INSTANCE, instance.getConnection().getId());
        }
        WorkspaceLlmModel byo = config.getWorkspaceModel();
        if (byo != null) {
            return new ConnectionRef(FundingSource.WORKSPACE, byo.getConnection().getId());
        }
        return new ConnectionRef(null, null);
    }

    /**
     * Re-resolves the LIVE base URL + credential + auth header material for a frozen
     * {@link ConnectionRef} — used by the LLM proxy at call time, NEVER at job-dispatch time. Picks up
     * key rotation/revocation, host repointing, and connection enable/disable all TOGETHER and
     * immediately, unlike everything else in a job's {@code ConfigSnapshot} which is deliberately frozen
     * at dispatch. The base URL is deliberately re-read here rather than taken from the snapshot: if it
     * came from the snapshot while the credential is re-resolved live, a connection repointed to a new
     * host after dispatch would send the NEW (rotated) key to the OLD (stale, frozen) host — routing and
     * credential must come from the same live row. Returns empty when the connection (or its legacy
     * config) no longer exists or has been disabled.
     *
     * @param legacyConfigId used only when {@code ref} carries no scope/connection (pre-catalog config)
     * @param legacyApiProtocol the snapshot's frozen api protocol, used to derive the legacy auth-header
     *     shape and default host (mirrors {@link #legacy(AgentConfig)} — a pre-catalog config has no
     *     connection row to read header conventions from)
     */
    @Transactional(readOnly = true)
    public @Nullable ProxyCredential resolveProxyCredential(
        ConnectionRef ref,
        @Nullable Long legacyConfigId,
        @Nullable String legacyApiProtocol
    ) {
        if (ref.scope() == FundingSource.INSTANCE && ref.connectionId() != null) {
            return llmConnectionRepository
                .findById(ref.connectionId())
                .filter(LlmConnection::isEnabled)
                .map(c ->
                    new ProxyCredential(
                        c.getBaseUrl(),
                        c.getAuthHeaderName(),
                        c.getAuthValuePrefix(),
                        c.getAzureApiVersion(),
                        blankToNull(c.getApiKey())
                    )
                )
                .orElse(null);
        }
        if (ref.scope() == FundingSource.WORKSPACE && ref.connectionId() != null) {
            return workspaceLlmConnectionRepository
                .findById(ref.connectionId())
                .filter(WorkspaceLlmConnection::isEnabled)
                .map(c ->
                    new ProxyCredential(
                        c.getBaseUrl(),
                        c.getAuthHeaderName(),
                        c.getAuthValuePrefix(),
                        c.getAzureApiVersion(),
                        blankToNull(c.getApiKey())
                    )
                )
                .orElse(null);
        }
        if (legacyConfigId == null) {
            return null;
        }
        return agentConfigRepository
            .findById(legacyConfigId)
            .filter(c -> c.getLlmApiKey() != null && !c.getLlmApiKey().isBlank())
            .map(c -> {
                var defaults = ApiProtocolDefaults.forProtocol(legacyApiProtocol != null ? legacyApiProtocol : "");
                String azureVersion = AZURE_PROTOCOL.equals(legacyApiProtocol) ? AZURE_API_VERSION : null;
                String liveBaseUrl =
                    c.getLlmBaseUrl() != null && !c.getLlmBaseUrl().isBlank()
                        ? c.getLlmBaseUrl()
                        : legacyDefaultBaseUrl(legacyApiProtocol);
                return new ProxyCredential(
                    liveBaseUrl,
                    defaults.headerName(),
                    defaults.valuePrefix(),
                    azureVersion,
                    c.getLlmApiKey()
                );
            })
            .orElse(null);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value != null && !value.isBlank() ? value : null;
    }

    /**
     * Same provider/protocol → default-host mapping {@link #legacy(AgentConfig)} and
     * {@link de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot#fromLegacyJson} use, keyed here by
     * the snapshot's frozen {@code apiProtocol} string (all that {@link #resolveProxyCredential} has for
     * a pre-catalog config — it deliberately re-reads the config's LIVE {@code llmBaseUrl}, falling back
     * to this default only when that live column is itself blank).
     */
    private static String legacyDefaultBaseUrl(@Nullable String apiProtocol) {
        if (ANTHROPIC_PROTOCOL.equals(apiProtocol)) {
            return "https://api.anthropic.com";
        }
        if (AZURE_PROTOCOL.equals(apiProtocol)) {
            return "";
        }
        return "https://api.openai.com";
    }

    private static String effectiveProtocol(@Nullable String override, String connectionProtocol) {
        return override != null && !override.isBlank() ? override : connectionProtocol;
    }

    /**
     * Map a pre-catalog {@link AgentConfig} onto a {@link ResolvedLlmModel} using the same
     * provider→(protocol, auth header, upstream URL) rules a one-time migration used to backfill early
     * catalog rows (since removed — the catalog is now created directly by instance/workspace admins).
     * This runtime fallback is what actually keeps a legacy config working: any config not yet rebound
     * to an instance or workspace model resolves through here instead.
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
