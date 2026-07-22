package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
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
 * <p>Only catalog bindings are executable. Legacy {@code AgentConfig.llm*} columns remain in the schema
 * during deprecation but are deliberately never read at runtime.
 */
@Service
@RequiredArgsConstructor
public class LlmModelResolver {

    private final LlmConnectionRepository llmConnectionRepository;
    private final WorkspaceLlmConnectionRepository workspaceLlmConnectionRepository;
    private final LlmModelRepository llmModelRepository;
    private final WorkspaceLlmModelRepository workspaceLlmModelRepository;
    private final LlmModelWorkspaceGrantRepository grantRepository;

    /** The effective, non-secret runtime shape for a config's bound model. */
    @Transactional(readOnly = true)
    public ResolvedLlmModel resolve(AgentConfig config) {
        LlmModel instance = config.getInstanceModel();
        if (instance != null) {
            LlmConnection c = instance.getConnection();
            requireUsableInstanceModel(instance, config.getWorkspace().getId());
            return new ResolvedLlmModel(
                c.getBaseUrl(),
                c.getApiProtocol(),
                instance.getUpstreamModelId(),
                instance.getContextWindow(),
                instance.getMaxOutputTokens(),
                instance.isSupportsReasoning(),
                FundingSource.INSTANCE
            );
        }
        WorkspaceLlmModel byo = config.getWorkspaceModel();
        if (byo != null) {
            WorkspaceLlmConnection c = byo.getConnection();
            if (
                !byo.isEnabled() ||
                !c.isEnabled() ||
                !isSupportedProtocol(c.getApiProtocol()) ||
                !byo.getWorkspace().getId().equals(config.getWorkspace().getId())
            ) {
                throw unavailable();
            }
            return new ResolvedLlmModel(
                c.getBaseUrl(),
                c.getApiProtocol(),
                byo.getUpstreamModelId(),
                byo.getContextWindow(),
                byo.getMaxOutputTokens(),
                byo.isSupportsReasoning(),
                FundingSource.WORKSPACE
            );
        }
        throw new IllegalStateException("The agent config must bind an available OpenAI-compatible model");
    }

    private void requireUsableInstanceModel(LlmModel model, Long workspaceId) {
        boolean visible =
            model.getVisibility() == ModelVisibility.PUBLIC ||
            grantRepository.existsByIdModelIdAndIdWorkspaceId(model.getId(), workspaceId);
        if (
            !model.isEnabled() ||
            !model.getConnection().isEnabled() ||
            !isSupportedProtocol(model.getConnection().getApiProtocol()) ||
            !visible
        ) {
            throw unavailable();
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("The configured OpenAI-compatible model is not available");
    }

    /** The live API key for a config's bound connection. Never frozen into a snapshot. */
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
        return null;
    }

    /**
     * Identifies WHICH connection row funds a config's binding, without exposing any credential
     * material (#1368 slice 5). Frozen into {@link de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot}
     * so the proxy can re-resolve the live credential for an in-flight job without re-reading the
     * config's (possibly since-changed) current binding. Both components {@code null} represent an
     * unusable legacy snapshot.
     */
    public record ConnectionRef(
        @Nullable FundingSource scope,
        @Nullable Long connectionId,
        @Nullable Long modelId,
        @Nullable Long workspaceId
    ) {
        /** Backward-compatible shape for legacy snapshots and callers without a catalog model reference. */
        public ConnectionRef(@Nullable FundingSource scope, @Nullable Long connectionId) {
            this(scope, connectionId, null, null);
        }
    }

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
        String apiProtocol,
        LlmAuthMode authMode,
        String upstreamModelId,
        @Nullable String apiKey
    ) {}

    /** Which connection (if any) funds this config's current binding. See {@link ConnectionRef}. */
    @Transactional(readOnly = true)
    public ConnectionRef connectionRef(AgentConfig config) {
        LlmModel instance = config.getInstanceModel();
        if (instance != null) {
            return new ConnectionRef(
                FundingSource.INSTANCE,
                instance.getConnection().getId(),
                instance.getId(),
                config.getWorkspace().getId()
            );
        }
        WorkspaceLlmModel byo = config.getWorkspaceModel();
        if (byo != null) {
            return new ConnectionRef(
                FundingSource.WORKSPACE,
                byo.getConnection().getId(),
                byo.getId(),
                config.getWorkspace().getId()
            );
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
     * credential must come from the same live row. Returns empty when the model or connection is
     * unavailable. The legacy parameters remain only so already-persisted job snapshots deserialize;
     * they are never used for routing or credentials.
     *
     * @param legacyConfigId ignored legacy snapshot field
     * @param legacyApiProtocol ignored legacy snapshot field
     */
    @Transactional(readOnly = true)
    public @Nullable ProxyCredential resolveProxyCredential(
        ConnectionRef ref,
        @Nullable Long legacyConfigId,
        @Nullable String legacyApiProtocol
    ) {
        if (ref.scope() == FundingSource.INSTANCE && ref.connectionId() != null) {
            if (!isUsableInstanceModel(ref)) {
                return null;
            }
            LlmModel model = llmModelRepository.findById(ref.modelId()).orElse(null);
            if (model == null) return null;
            return llmConnectionRepository
                .findById(ref.connectionId())
                .filter(LlmConnection::isEnabled)
                .filter(c -> isSupportedProtocol(c.getApiProtocol()))
                .map(c ->
                    new ProxyCredential(
                        c.getBaseUrl(),
                        c.getApiProtocol(),
                        c.getAuthMode(),
                        model.getUpstreamModelId(),
                        blankToNull(c.getApiKey())
                    )
                )
                .orElse(null);
        }
        if (ref.scope() == FundingSource.WORKSPACE && ref.connectionId() != null) {
            if (!isUsableWorkspaceModel(ref)) {
                return null;
            }
            WorkspaceLlmModel model = workspaceLlmModelRepository
                .findByIdAndWorkspaceId(ref.modelId(), ref.workspaceId())
                .orElse(null);
            if (model == null) return null;
            return workspaceLlmConnectionRepository
                .findById(ref.connectionId())
                .filter(WorkspaceLlmConnection::isEnabled)
                .filter(c -> isSupportedProtocol(c.getApiProtocol()))
                .map(c ->
                    new ProxyCredential(
                        c.getBaseUrl(),
                        c.getApiProtocol(),
                        c.getAuthMode(),
                        model.getUpstreamModelId(),
                        blankToNull(c.getApiKey())
                    )
                )
                .orElse(null);
        }
        return null;
    }

    private boolean isUsableInstanceModel(ConnectionRef ref) {
        if (ref.modelId() == null || ref.workspaceId() == null) {
            return false;
        }
        return llmModelRepository
            .findById(ref.modelId())
            .filter(LlmModel::isEnabled)
            .filter(model -> model.getConnection().getId().equals(ref.connectionId()))
            .filter(
                model ->
                    model.getVisibility() == ModelVisibility.PUBLIC ||
                    grantRepository.existsByIdModelIdAndIdWorkspaceId(model.getId(), ref.workspaceId())
            )
            .isPresent();
    }

    private boolean isUsableWorkspaceModel(ConnectionRef ref) {
        if (ref.modelId() == null || ref.workspaceId() == null) {
            return false;
        }
        return workspaceLlmModelRepository
            .findByIdAndWorkspaceId(ref.modelId(), ref.workspaceId())
            .filter(WorkspaceLlmModel::isEnabled)
            .filter(model -> model.getConnection().getId().equals(ref.connectionId()))
            .isPresent();
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value != null && !value.isBlank() ? value : null;
    }

    private static boolean isSupportedProtocol(@Nullable String apiProtocol) {
        return "openai-completions".equals(apiProtocol) || "openai-responses".equals(apiProtocol);
    }
}
