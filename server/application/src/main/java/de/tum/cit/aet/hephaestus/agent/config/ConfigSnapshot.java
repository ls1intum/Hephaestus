package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelBindingSource;
import de.tum.cit.aet.hephaestus.agent.catalog.ReasoningEffort;
import de.tum.cit.aet.hephaestus.agent.catalog.ResolvedLlmModel;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.practice.PracticePiAdapter;
import de.tum.cit.aet.hephaestus.core.llm.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Immutable projection of a workspace's {@link WorkspaceAgentBinding} frozen at job submission time.
 *
 * <p>Stored as JSONB on {@link ReviewJob#getConfigSnapshot()}.
 * The executor reads this snapshot instead of the live binding so that in-flight jobs are not
 * affected by binding changes.
 *
 * <p>SECURITY: everything frozen here is non-secret behaviour. The credential is never frozen —
 * {@link #connectionScope}/{@link #connectionId} only identify which connection row funds the job, so
 * the proxy re-resolves credential AND base URL live via
 * {@link LlmModelResolver#resolveProxyCredential}. The two must travel together: resolving a rotated
 * key while routing on this frozen {@link #baseUrl} would send the new key to a host the connection
 * no longer points at. {@link #baseUrl} is frozen only for non-proxy consumers that need a host
 * before any credential exists.
 */
public record ConfigSnapshot(
        int schemaVersion,
        String apiProtocol,
        String baseUrl,
        String upstreamModelId,
        @Nullable Integer contextWindow,
        @Nullable Integer maxOutputTokens,
        @Nullable ReasoningEffort reasoningEffort,
        @Nullable UsageFundingSource connectionScope,
        @Nullable Long connectionId,
        @Nullable Long modelId,
        @Nullable Long workspaceId,
        int timeoutSeconds,
        boolean allowInternet,
        @Nullable LlmPriceSnapshot priceSnapshot,
        // The slot the job was routed to; null in older rows reads as UNDECLARED.
        @Nullable DataHandlingTier dataHandlingTier,
        // The models for practice scripts bound besides the review's own; null when none is.
        @Nullable Map<ModelKind, FrozenModel> practiceScript) {
    /**
     * Bump only for a reshape (field removal, type change, semantic reinterpretation). Adding a
     * nullable field is compatible both ways and needs no bump.
     */
    public static final int SCHEMA_VERSION = 7;

    public ConfigSnapshot {
        Objects.requireNonNull(apiProtocol, "apiProtocol must not be null");
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(upstreamModelId, "upstreamModelId must not be null");
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive, got: " + timeoutSeconds);
        }
        // Every consumer reads the timeout from here: the container, the token expiry, the runner's budget
        // and the stale-job sweep. Bindings saved below the floor still get a runnable review.
        timeoutSeconds = Math.max(timeoutSeconds, PracticePiAdapter.MIN_TIMEOUT_SECONDS);
    }

    public static ConfigSnapshot from(ModelBindingSource source, LlmModelResolver resolver) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(resolver, "resolver must not be null");
        ResolvedLlmModel resolved = resolver.resolve(source);
        LlmModelResolver.ConnectionRef ref = resolver.connectionRef(source);
        return new ConfigSnapshot(
                SCHEMA_VERSION,
                resolved.apiProtocol(),
                resolved.baseUrl(),
                resolved.upstreamModelId(),
                resolved.contextWindow(),
                resolved.maxOutputTokens(),
                resolved.reasoningEffort(),
                ref.scope(),
                ref.connectionId(),
                ref.modelId(),
                ref.workspaceId(),
                source.getTimeoutSeconds(),
                source.isAllowInternet(),
                null,
                source.getDataHandlingTier(),
                null);
    }

    public ConfigSnapshot withPriceSnapshot(@Nullable LlmPriceSnapshot price) {
        return new ConfigSnapshot(
                schemaVersion,
                apiProtocol,
                baseUrl,
                upstreamModelId,
                contextWindow,
                maxOutputTokens,
                reasoningEffort,
                connectionScope,
                connectionId,
                modelId,
                workspaceId,
                timeoutSeconds,
                allowInternet,
                price,
                dataHandlingTier,
                practiceScript);
    }

    /** The exact direct-model slots frozen by the review, including explicit chat availability. */
    public ConfigSnapshot withPracticeScript(@Nullable Map<ModelKind, FrozenModel> slots) {
        return new ConfigSnapshot(
                schemaVersion,
                apiProtocol,
                baseUrl,
                upstreamModelId,
                contextWindow,
                maxOutputTokens,
                reasoningEffort,
                connectionScope,
                connectionId,
                modelId,
                workspaceId,
                timeoutSeconds,
                allowInternet,
                priceSnapshot,
                dataHandlingTier,
                slots);
    }

    /** The review's own model. */
    public FrozenModel model() {
        return new FrozenModel(
                apiProtocol,
                baseUrl,
                upstreamModelId,
                connectionScope,
                connectionId,
                modelId,
                workspaceId,
                dataHandlingTier,
                reasoningEffort,
                priceSnapshot);
    }

    /** Every direct model that the frozen method permits scripts to call. */
    public Map<ModelKind, FrozenModel> practiceScriptModels() {
        Map<ModelKind, FrozenModel> models = new EnumMap<>(ModelKind.class);
        if (practiceScript != null) {
            models.putAll(practiceScript);
        }
        return models;
    }

    /** The separately bound model for practice scripts of one kind other than chat, or {@code null} when none is frozen. */
    public @Nullable FrozenModel practiceScriptSlot(ModelKind kind) {
        return practiceScript == null ? null : practiceScript.get(kind);
    }

    public JsonNode toJson(ObjectMapper objectMapper) {
        return objectMapper.valueToTree(this);
    }

    /**
     * Deserialize from JSONB, rejecting a newer schema version so a rolling deploy cannot silently
     * misread a row an upgraded node wrote.
     */
    public static ConfigSnapshot fromJson(JsonNode node, ObjectMapper objectMapper) {
        Objects.requireNonNull(node, "node");
        int version = node.path("schemaVersion").asInt(-1);
        if (version != SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported runtime config snapshot version: " + version);
        }
        return objectMapper.convertValue(node, ConfigSnapshot.class);
    }
}
