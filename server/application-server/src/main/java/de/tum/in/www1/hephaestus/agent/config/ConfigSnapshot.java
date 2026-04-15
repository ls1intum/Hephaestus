package de.tum.in.www1.hephaestus.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * Immutable projection of {@link AgentConfig} frozen at job submission time.
 *
 * <p>Stored as JSONB on {@link de.tum.in.www1.hephaestus.agent.job.AgentJob#getConfigSnapshot()}.
 * The executor reads this snapshot instead of the live config so that in-flight jobs are not
 * affected by config changes.
 *
 * <h2>Deliberately excluded fields</h2>
 * <ul>
 *   <li>{@code llmApiKey} — secret, stored in its own encrypted column on AgentJob</li>
 *   <li>{@code maxConcurrentJobs} — concurrency gate read live from AgentConfig so admin
 *       changes take effect immediately</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigSnapshot(
    int schemaVersion,
    Long configId,
    String configName,
    Long runnerId,
    String runnerName,
    AgentType agentType,
    LlmProvider llmProvider,
    CredentialMode credentialMode,
    @Nullable String modelName,
    @Nullable String modelVersion,
    int timeoutSeconds,
    boolean allowInternet
) {
    /** Current schema version. Bump when adding/removing fields. */
    public static final int SCHEMA_VERSION = 3;

    public ConfigSnapshot {
        Objects.requireNonNull(agentType, "agentType must not be null");
        Objects.requireNonNull(llmProvider, "llmProvider must not be null");
        Objects.requireNonNull(credentialMode, "credentialMode must not be null");
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive, got: " + timeoutSeconds);
        }
    }

    public ConfigSnapshot(
        int schemaVersion,
        Long configId,
        String configName,
        AgentType agentType,
        LlmProvider llmProvider,
        CredentialMode credentialMode,
        @Nullable String modelName,
        @Nullable String modelVersion,
        int timeoutSeconds,
        boolean allowInternet
    ) {
        this(
            schemaVersion,
            configId,
            configName,
            configId,
            configName,
            agentType,
            llmProvider,
            credentialMode,
            modelName,
            modelVersion,
            timeoutSeconds,
            allowInternet
        );
    }

    /**
     * Create a snapshot from a live {@link AgentConfig}.
     */
    public static ConfigSnapshot from(AgentConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        var runner = config.getRunner();
        return new ConfigSnapshot(
            SCHEMA_VERSION,
            config.getId(),
            config.getName(),
            runner != null && runner.getId() != null ? runner.getId() : config.getId(),
            runner != null && runner.getName() != null ? runner.getName() : config.getName(),
            config.getAgentType(),
            config.getLlmProvider(),
            config.getCredentialMode(),
            config.getModelName(),
            config.getModelVersion(),
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
     * silent data corruption during rolling deploys.
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
        ConfigSnapshot snapshot = objectMapper.convertValue(node, ConfigSnapshot.class);
        if (snapshot.runnerId() != null || snapshot.runnerName() != null) {
            return snapshot;
        }
        return new ConfigSnapshot(
            snapshot.schemaVersion(),
            snapshot.configId(),
            snapshot.configName(),
            snapshot.configId(),
            snapshot.configName(),
            snapshot.agentType(),
            snapshot.llmProvider(),
            snapshot.credentialMode(),
            snapshot.modelName(),
            snapshot.modelVersion(),
            snapshot.timeoutSeconds(),
            snapshot.allowInternet()
        );
    }
}
