package de.tum.cit.aet.hephaestus.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelBindingSource;
import de.tum.cit.aet.hephaestus.agent.catalog.ResolvedLlmModel;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Immutable projection of a workspace's {@link WorkspaceAgentBinding} frozen at job submission time.
 *
 * <p>Stored as JSONB on {@link de.tum.cit.aet.hephaestus.agent.job.AgentJob#getConfigSnapshot()}.
 * The executor reads this snapshot instead of the live binding so that in-flight jobs are not
 * affected by binding changes.
 *
 * <h2>Deliberately excluded fields</h2>
 *
 * <p>Everything here is non-secret, frozen BEHAVIOUR: the wire protocol, the model id, and its
 * capability envelope. The credential itself — and any authentication-header material — is
 * deliberately NEVER frozen here.
 * {@link #connectionScope}/{@link #connectionId} instead identify WHICH connection row funds the job,
 * so the LLM proxy can re-resolve the live credential at call time via
 * {@link LlmModelResolver#resolveProxyCredential}, picking up rotation/revocation immediately. A
 * pre-v4 snapshot carries no connection identity and therefore fails closed at the proxy.
 *
 * <h3>{@link #baseUrl} is split: frozen here, but NOT what the proxy routes on</h3>
 *
 * <p>{@link #baseUrl} is frozen at dispatch and stays that way for non-proxy consumers (e.g. runner
 * config that needs a host to render into the sandbox at build time, before any credential exists). The
 * LLM proxy, however, deliberately does NOT read {@link #baseUrl} — it re-resolves the base URL LIVE,
 * from the same connection row the credential comes from, via
 * {@link LlmModelResolver#resolveProxyCredential}. Routing and credential must travel together: if a
 * connection is repointed to a new host after a job's snapshot was frozen, resolving the credential live
 * while trusting this frozen {@link #baseUrl} would send the connection's NEW (rotated) key to the OLD
 * (stale) host — a split-brain that leaks the new credential to whatever now answers at the old address.
 *
 * <ul>
 *   <li>{@code maxConcurrentJobs} — concurrency gate read live from the binding so admin
 *       changes take effect immediately</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigSnapshot(
    int schemaVersion,
    String apiProtocol,
    String baseUrl,
    String upstreamModelId,
    @Nullable String modelVersion,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens,
    boolean supportsReasoning,
    @Nullable FundingSource connectionScope,
    @Nullable Long connectionId,
    @Nullable Long modelId,
    @Nullable Long workspaceId,
    int timeoutSeconds,
    boolean allowInternet,
    @Nullable LlmPriceSnapshot priceSnapshot
) {
    /**
     * Current schema version. Bump only for breaking changes (field removal, type change,
     * semantic reinterpretation). Additive nullable fields are forward- AND backward-compatible
     * thanks to {@code @JsonIgnoreProperties(ignoreUnknown = true)} on read and Jackson's
     * default null-fill on missing fields — those do NOT need a bump.
     *
     * <p>v4 replaced {@code llmProvider}/{@code credentialMode}/{@code llmBaseUrl}/
     * {@code modelName} with the resolver's non-secret behaviour shape + a connection reference — a
     * genuine reshape, not an additive change, so {@link #fromJson} translates v1-v3 payloads
     * explicitly instead of relying on Jackson's default-null fill (see {@link #fromLegacyJson}).
     *
     * <p>v5 dropped {@code configId}/{@code configName}, the last two fields of the deleted
     * named-agent-config model. Nothing read them, so a persisted v4 row needs no translation — its
     * two extra keys are simply ignored on the way in (see {@link #CATALOG_SHAPE_MIN_VERSION}).
     */
    public static final int SCHEMA_VERSION = 5;

    /**
     * Oldest version whose payload already uses the catalog shape this record maps directly. Versions
     * at or above it deserialize straight through (unknown keys ignored); anything older is a
     * different shape and must go through {@link #fromLegacyJson}.
     *
     * <p>This is the constant that makes "drop a field" cheap and "rename a field" expensive: the gap
     * between it and {@link #SCHEMA_VERSION} is exactly the set of persisted versions that differ from
     * the current record by dropped keys alone. A rename would have to move this floor up and grow a
     * translation step, a removal does not.
     */
    private static final int CATALOG_SHAPE_MIN_VERSION = 4;

    public ConfigSnapshot {
        Objects.requireNonNull(apiProtocol, "apiProtocol must not be null");
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(upstreamModelId, "upstreamModelId must not be null");
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive, got: " + timeoutSeconds);
        }
    }

    /**
     * Create a snapshot from a live {@link ModelBindingSource}, resolving its instance or workspace
     * catalog model via {@link LlmModelResolver}.
     */
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
            null,
            resolved.contextWindow(),
            resolved.maxOutputTokens(),
            resolved.supportsReasoning(),
            ref.scope(),
            ref.connectionId(),
            ref.modelId(),
            ref.workspaceId(),
            source.getTimeoutSeconds(),
            source.isAllowInternet(),
            null
        );
    }

    /** Attaches claim-time accounting while preserving all submit-time behaviour exactly. */
    public ConfigSnapshot withPriceSnapshot(LlmPriceSnapshot price) {
        return new ConfigSnapshot(
            schemaVersion,
            apiProtocol,
            baseUrl,
            upstreamModelId,
            modelVersion,
            contextWindow,
            maxOutputTokens,
            supportsReasoning,
            connectionScope,
            connectionId,
            modelId,
            workspaceId,
            timeoutSeconds,
            allowInternet,
            price
        );
    }

    /**
     * Serialize to {@link JsonNode} for JSONB storage.
     */
    public JsonNode toJson(ObjectMapper objectMapper) {
        return objectMapper.valueToTree(this);
    }

    /**
     * Deserialize from JSONB. Rejects snapshots from a newer schema version to prevent
     * silent data corruption during rolling deploys. Snapshots persisted before v4 (schemaVersion
     * 0-3) use the pre-catalog shape (llmProvider/credentialMode/llmBaseUrl/modelName) and are
     * translated via {@link #fromLegacyJson} only for structural deserialization. Since such a row has
     * no catalog identity, proxy credential resolution rejects it.
     */
    public static ConfigSnapshot fromJson(JsonNode node, ObjectMapper objectMapper) {
        Objects.requireNonNull(node, "node must not be null");
        int version = node.path("schemaVersion").asInt(0);
        if (version > SCHEMA_VERSION) {
            throw new IllegalStateException(
                "ConfigSnapshot schema version %d is newer than supported version %d. Upgrade the application server.".formatted(
                    version,
                    SCHEMA_VERSION
                )
            );
        }
        if (version < CATALOG_SHAPE_MIN_VERSION) {
            return fromLegacyJson(node);
        }
        return objectMapper.convertValue(node, ConfigSnapshot.class);
    }

    /**
     * Translates a pre-v4 snapshot (llmProvider/credentialMode/llmBaseUrl/modelName) into the current
     * shape so historical rows remain readable. {@code connectionScope}/{@code connectionId} are
     * always null, which deliberately makes such a job non-routable at the proxy.
     */
    private static ConfigSnapshot fromLegacyJson(JsonNode node) {
        String provider = node.path("llmProvider").asString("OPENAI");
        String apiProtocol;
        String defaultBaseUrl;
        switch (provider) {
            case "ANTHROPIC" -> {
                apiProtocol = "anthropic-messages";
                defaultBaseUrl = "https://api.anthropic.com";
            }
            case "AZURE_OPENAI" -> {
                apiProtocol = "azure-openai-responses";
                defaultBaseUrl = "";
            }
            default -> {
                apiProtocol = "openai-completions";
                defaultBaseUrl = "https://api.openai.com";
            }
        }
        String legacyBaseUrl = node.path("llmBaseUrl").asString(null);
        String baseUrl = legacyBaseUrl != null && !legacyBaseUrl.isBlank() ? legacyBaseUrl : defaultBaseUrl;
        String modelName = node.path("modelName").asString(null);
        String modelVersion = node.path("modelVersion").asString(null);
        int timeoutSeconds = node.path("timeoutSeconds").asInt(600);
        boolean allowInternet = node.path("allowInternet").asBoolean(false);
        return new ConfigSnapshot(
            node.path("schemaVersion").asInt(0),
            apiProtocol,
            baseUrl,
            modelName != null ? modelName : "",
            modelVersion,
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            timeoutSeconds,
            allowInternet,
            null
        );
    }
}
