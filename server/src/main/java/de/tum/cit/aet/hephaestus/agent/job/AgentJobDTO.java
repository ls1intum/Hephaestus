package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Schema(description = "Agent job execution record (job_token intentionally omitted)")
public record AgentJobDTO(
    @NonNull @Schema(description = "Job ID") UUID id,
    @NonNull @Schema(description = "Job type") AgentJobType jobType,
    @NonNull @Schema(description = "Current job status") AgentJobStatus status,
    @Schema(description = "Job metadata (routing/display info)") Object metadata,
    @Schema(description = "Job output (agent results)") Object output,
    @NonNull
    @Schema(
        description = "Frozen agent config at submit time (an INSTANCE-scoped connection's baseUrl is redacted to scheme://host; only a WORKSPACE-scoped BYO connection's baseUrl is left intact)"
    )
    Object configSnapshot,
    @Schema(description = "ID of the agent config that ran this job (from the frozen snapshot)") Long configId,
    @Schema(description = "Name of the agent config that ran this job (from the frozen snapshot)") String configName,
    @Schema(description = "Docker container ID") String containerId,
    @Schema(description = "Container exit code") Integer exitCode,
    @Schema(description = "Human-readable error message") String errorMessage,
    @Schema(
        description = "Delivery status: null = not applicable, PENDING = awaiting delivery, DELIVERED = posted, FAILED = delivery error"
    )
    DeliveryStatus deliveryStatus,
    @Schema(description = "Git provider comment/note ID for posted feedback") String deliveryCommentId,
    @NonNull @Schema(description = "Number of retry attempts") Integer retryCount,
    @NonNull @Schema(description = "Timestamp when the job was created") Instant createdAt,
    @Schema(description = "Timestamp when the job started running") Instant startedAt,
    @Schema(description = "Timestamp when the job completed") Instant completedAt,
    @Schema(description = "LLM model used (e.g. gpt-5.4-mini, openai/gpt-oss-120b)") String llmModel,
    @Schema(description = "Model version/snapshot date (e.g. 2026-03-17)") String llmModelVersion,
    @Schema(description = "Total LLM API calls (steps) during execution") Integer llmTotalCalls,
    @Schema(description = "Total input tokens consumed") Integer llmTotalInputTokens,
    @Schema(description = "Total output tokens generated") Integer llmTotalOutputTokens,
    @Schema(description = "Total reasoning/thinking tokens") Integer llmTotalReasoningTokens,
    @Schema(description = "Tokens read from prompt cache") Integer llmCacheReadTokens,
    @Schema(description = "Tokens written to prompt cache") Integer llmCacheWriteTokens,
    @Schema(
        description = "Deprecated, always null (#1368 slice 6): the runner no longer reports cost. See the " +
            "workspace's LLM usage rollup for the authoritative, catalog-derived per-job cost."
    )
    Double llmCostUsd
) {
    public static AgentJobDTO from(AgentJob job) {
        JsonNode snapshot = job.getConfigSnapshot();
        return new AgentJobDTO(
            job.getId(),
            job.getJobType(),
            job.getStatus(),
            job.getMetadata(),
            job.getOutput(),
            redactInstanceBaseUrl(snapshot),
            snapshotLong(snapshot, "configId"),
            snapshotString(snapshot, "configName"),
            job.getContainerId(),
            job.getExitCode(),
            job.getErrorMessage(),
            job.getDeliveryStatus(),
            job.getDeliveryCommentId(),
            job.getRetryCount(),
            job.getCreatedAt(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getLlmModel(),
            job.getLlmModelVersion(),
            job.getLlmTotalCalls(),
            job.getLlmTotalInputTokens(),
            job.getLlmTotalOutputTokens(),
            job.getLlmTotalReasoningTokens(),
            job.getLlmCacheReadTokens(),
            job.getLlmCacheWriteTokens(),
            job.getLlmCostUsd()
        );
    }

    /**
     * A workspace admin (the {@code /agent-jobs} audience — {@link AgentJobController} requires only
     * {@code RequireAtLeastWorkspaceAdmin}) must never see the full path/query detail of a base URL they
     * don't themselves own end-to-end:
     *
     * <ul>
     *   <li>{@code connectionScope=INSTANCE} — owned and configured by the instance admin, potentially
     *       shared across many workspaces; its URL (an internal gateway, a vendor-specific deployment
     *       path, …) is not this workspace admin's data to read.</li>
     *   <li>{@code connectionScope=null} — only possible for a historical snapshot during a rolling
     *       upgrade. It is non-routable, but its former URL is still reduced to host-only so an operator
     *       endpoint path cannot leak while the row remains visible.</li>
     * </ul>
     *
     * <p>{@code connectionScope=WORKSPACE} (BYO) is the workspace's own configuration, so it is left
     * as-is. Reduces the frozen snapshot's {@code baseUrl} to {@code scheme://host} — enough to see
     * which provider a job used without exposing path detail.
     */
    private static Object redactInstanceBaseUrl(JsonNode snapshot) {
        if (!(snapshot instanceof ObjectNode obj) || !obj.has("baseUrl")) {
            return snapshot;
        }
        if ("WORKSPACE".equals(snapshotString(snapshot, "connectionScope"))) {
            return snapshot;
        }
        ObjectNode redacted = obj.deepCopy();
        redacted.put("baseUrl", hostOnly(obj.path("baseUrl").asString(null)));
        return redacted;
    }

    private static String hostOnly(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return scheme != null && host != null ? scheme + "://" + host : "(redacted)";
        } catch (IllegalArgumentException e) {
            return "(redacted)";
        }
    }

    private static Long snapshotLong(JsonNode snapshot, String field) {
        if (snapshot == null || !snapshot.has(field) || snapshot.get(field).isNull()) {
            return null;
        }
        return snapshot.get(field).asLong();
    }

    private static String snapshotString(JsonNode snapshot, String field) {
        if (snapshot == null || !snapshot.has(field) || snapshot.get(field).isNull()) {
            return null;
        }
        return snapshot.get(field).asString();
    }
}
