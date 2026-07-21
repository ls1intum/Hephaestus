package de.tum.cit.aet.hephaestus.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.ResolvedLlmModel;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Immutable projection of {@link AgentConfig} frozen at job submission time.
 *
 * <p>Stored as JSONB on {@link de.tum.cit.aet.hephaestus.agent.job.AgentJob#getConfigSnapshot()}.
 * The executor reads this snapshot instead of the live config so that in-flight jobs are not
 * affected by config changes.
 *
 * <h2>Deliberately excluded fields (#1368 slice 5 — runtime switch-over)</h2>
 *
 * <p>Everything here is non-secret, frozen BEHAVIOUR: the wire protocol, the model id, and its
 * capability envelope. The credential itself — and any header material ({@code authHeaderName}/
 * {@code authValuePrefix}/{@code azureApiVersion}) — is deliberately NEVER frozen here.
 * {@link #connectionScope}/{@link #connectionId} instead identify WHICH connection row funds the job,
 * so the LLM proxy can re-resolve the live credential at call time via
 * {@link LlmModelResolver#resolveProxyCredential}, picking up rotation/revocation immediately. A
 * legacy (pre-catalog) config carries {@code connectionScope=null}/{@code connectionId=null}; the
 * proxy then falls back to the live {@code AgentConfig.llmApiKey} via {@link #configId}.
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
 *   <li>{@code maxConcurrentJobs} — concurrency gate read live from AgentConfig so admin
 *       changes take effect immediately</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigSnapshot(
    int schemaVersion,
    Long configId,
    String configName,
    String apiProtocol,
    String baseUrl,
    String upstreamModelId,
    @Nullable String modelVersion,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens,
    boolean supportsReasoning,
    @Nullable String cacheControlFormat,
    @Nullable FundingSource connectionScope,
    @Nullable Long connectionId,
    int timeoutSeconds,
    boolean allowInternet
) {
    /**
     * Current schema version. Bump only for breaking changes (field removal, type change,
     * semantic reinterpretation). Additive nullable fields are forward- AND backward-compatible
     * thanks to {@code @JsonIgnoreProperties(ignoreUnknown = true)} on read and Jackson's
     * default null-fill on missing fields — those do NOT need a bump.
     *
     * <p>v4 (#1368 slice 5) replaced {@code llmProvider}/{@code credentialMode}/{@code llmBaseUrl}/
     * {@code modelName} with the resolver's non-secret behaviour shape + a connection reference — a
     * genuine reshape, not an additive change, so {@link #fromJson} translates v1-v3 payloads
     * explicitly instead of relying on Jackson's default-null fill (see {@link #fromLegacyJson}).
     */
    public static final int SCHEMA_VERSION = 4;

    public ConfigSnapshot {
        Objects.requireNonNull(apiProtocol, "apiProtocol must not be null");
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(upstreamModelId, "upstreamModelId must not be null");
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive, got: " + timeoutSeconds);
        }
    }

    /**
     * Create a snapshot from a live {@link AgentConfig}, resolving its effective (instance / workspace
     * BYO / legacy) model binding via {@link LlmModelResolver}.
     */
    public static ConfigSnapshot from(AgentConfig config, LlmModelResolver resolver) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(resolver, "resolver must not be null");
        ResolvedLlmModel resolved = resolver.resolve(config);
        LlmModelResolver.ConnectionRef ref = resolver.connectionRef(config);
        return new ConfigSnapshot(
            SCHEMA_VERSION,
            config.getId(),
            config.getName(),
            resolved.apiProtocol(),
            resolved.baseUrl(),
            resolved.upstreamModelId(),
            config.getModelVersion(),
            resolved.contextWindow(),
            resolved.maxOutputTokens(),
            resolved.supportsReasoning(),
            resolved.cacheControlFormat(),
            ref.scope(),
            ref.connectionId(),
            config.getTimeoutSeconds(),
            config.isAllowInternet()
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
     * translated via {@link #fromLegacyJson} so a job already in flight during the deploy that
     * introduced v4 keeps running.
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
        if (version < SCHEMA_VERSION) {
            return fromLegacyJson(node);
        }
        return objectMapper.convertValue(node, ConfigSnapshot.class);
    }

    /**
     * Translates a pre-v4 snapshot (llmProvider/credentialMode/llmBaseUrl/modelName) into the v4
     * shape, mirroring the same provider→(protocol, default base URL) rules
     * {@link LlmModelResolver}'s legacy path uses, so an in-flight job dispatched before the v4
     * deploy keeps the exact behaviour it was given at submission. {@code connectionScope}/
     * {@code connectionId} are always null here — legacy snapshots never had a catalog binding by
     * construction, so the proxy falls back to {@code AgentConfig.llmApiKey} via {@code configId}.
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
        long configId = node.path("configId").asLong(0);
        String configName = node.path("configName").asString("");
        int timeoutSeconds = node.path("timeoutSeconds").asInt(600);
        boolean allowInternet = node.path("allowInternet").asBoolean(false);
        return new ConfigSnapshot(
            node.path("schemaVersion").asInt(0),
            configId,
            configName,
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
            timeoutSeconds,
            allowInternet
        );
    }
}
