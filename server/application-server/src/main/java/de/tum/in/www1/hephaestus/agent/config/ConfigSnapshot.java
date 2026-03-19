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
 *
 * @param schemaVersion snapshot format version (always {@link #SCHEMA_VERSION})
 * @param configId      source config ID (for audit after config deletion)
 * @param configName    source config name (for display after config deletion)
 * @param agentType     agent runtime (CLAUDE_CODE, OPENCODE)
 * @param llmProvider   LLM API provider
 * @param credentialMode authentication mode (PROXY, API_KEY, OAUTH)
 * @param modelName     model name override (null = agent default)
 * @param timeoutSeconds container timeout
 * @param allowInternet  whether the container may reach the public internet
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigSnapshot(
    int schemaVersion,
    Long configId,
    String configName,
    AgentType agentType,
    LlmProvider llmProvider,
    CredentialMode credentialMode,
    @Nullable String modelName,
    int timeoutSeconds,
    boolean allowInternet
) {
    /** Current schema version. Bump when adding/removing fields. */
    public static final int SCHEMA_VERSION = 1;

    public ConfigSnapshot {
        Objects.requireNonNull(agentType, "agentType must not be null");
        Objects.requireNonNull(llmProvider, "llmProvider must not be null");
        Objects.requireNonNull(credentialMode, "credentialMode must not be null");
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive, got: " + timeoutSeconds);
        }
    }

    /**
     * Create a snapshot from a live {@link AgentConfig}.
     */
    public static ConfigSnapshot from(AgentConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        return new ConfigSnapshot(
            SCHEMA_VERSION,
            config.getId(),
            config.getName(),
            config.getAgentType(),
            config.getLlmProvider(),
            config.getCredentialMode(),
            config.getModelName(),
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
        return objectMapper.convertValue(node, ConfigSnapshot.class);
    }
}
