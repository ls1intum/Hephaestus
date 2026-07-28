package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJob;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobStatus;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobTrigger;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.NonNull;

@Schema(description = "One sync_job row — a single INITIAL/RECONCILIATION/BACKFILL pass for a connection")
public record SyncJobDTO(
    @NonNull @Schema(description = "Job id") Long id,
    @NonNull @Schema(description = "What kind of pass this is") SyncJobType type,
    @NonNull @Schema(description = "What initiated this job") SyncJobTrigger trigger,
    @NonNull @Schema(description = "Job status") SyncJobStatus status,
    @NonNull @Schema(description = "Whether a cooperative cancel was requested") Boolean cancelRequested,
    @Schema(description = "When the job started running") Instant startedAt,
    @Schema(description = "When the job finished (any terminal status)") Instant finishedAt,
    @NonNull @Schema(description = "When the job row was created") Instant createdAt,
    @Schema(description = "Coarse progress: items processed so far") Integer itemsProcessed,
    @Schema(description = "Coarse progress: total items, if known") Integer itemsTotal,
    @Schema(description = "Per-phase progress detail, integration-specific shape") Map<String, Object> progress,
    @Schema(description = "Truncated error summary, set on FAILED") String errorSummary,
    @Schema(description = "Account id of the admin who triggered this job, if MANUAL") Long triggeredByUserId
) {
    public static SyncJobDTO from(SyncJob job) {
        return new SyncJobDTO(
            job.getId(),
            job.getType(),
            job.getTrigger(),
            job.getStatus(),
            job.isCancelRequested(),
            job.getStartedAt(),
            job.getFinishedAt(),
            job.getCreatedAt(),
            job.getItemsProcessed(),
            job.getItemsTotal(),
            job.getProgress(),
            job.getErrorSummary(),
            job.getTriggeredByUserId()
        );
    }
}
