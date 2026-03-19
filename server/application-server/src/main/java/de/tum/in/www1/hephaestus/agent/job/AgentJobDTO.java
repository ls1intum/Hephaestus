package de.tum.in.www1.hephaestus.agent.job;

import de.tum.in.www1.hephaestus.agent.AgentJobType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.NonNull;

@Schema(description = "Agent job execution record (job_token intentionally omitted)")
public record AgentJobDTO(
    @NonNull @Schema(description = "Job ID") UUID id,
    @NonNull @Schema(description = "Job type") AgentJobType jobType,
    @NonNull @Schema(description = "Current job status") AgentJobStatus status,
    @Schema(description = "Job metadata (routing/display info)") Object metadata,
    @Schema(description = "Job output (agent results)") Object output,
    @NonNull @Schema(description = "Frozen agent config at submit time") Object configSnapshot,
    @Schema(description = "Docker container ID") String containerId,
    @Schema(description = "Container exit code") Integer exitCode,
    @Schema(description = "Human-readable error message") String errorMessage,
    @Schema(description = "Delivery status (null if no delivery attempted)") DeliveryStatus deliveryStatus,
    @Schema(description = "Git provider comment/note ID for posted feedback") String deliveryCommentId,
    @NonNull @Schema(description = "Number of retry attempts") Integer retryCount,
    @NonNull @Schema(description = "Timestamp when the job was created") Instant createdAt,
    @Schema(description = "Timestamp when the job started running") Instant startedAt,
    @Schema(description = "Timestamp when the job completed") Instant completedAt
) {
    public static AgentJobDTO from(AgentJob job) {
        return new AgentJobDTO(
            job.getId(),
            job.getJobType(),
            job.getStatus(),
            job.getMetadata(),
            job.getOutput(),
            job.getConfigSnapshot(),
            job.getContainerId(),
            job.getExitCode(),
            job.getErrorMessage(),
            job.getDeliveryStatus(),
            job.getDeliveryCommentId(),
            job.getRetryCount(),
            job.getCreatedAt(),
            job.getStartedAt(),
            job.getCompletedAt()
        );
    }
}
