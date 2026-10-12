package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.practices.review.GeneratedPathReviewDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup.Target;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Schema(description = "Agent job execution record (job_token intentionally omitted)")
public record AgentJobDTO(
        @NonNull @Schema(description = "Job ID") UUID id,
        @NonNull @Schema(description = "Job type") AgentJobType jobType,
        @NonNull @Schema(description = "Current job status") AgentJobStatus status,

        @NonNull @Schema(description = "Work item reviewed by this job")
        ReviewRunTargetDTO target,

        @Schema(description = "Job metadata (routing/display info)") @Nullable
        Object metadata,

        @Schema(
                description =
                        "Raw output is not returned by administrative job reads; use the authorized result and feedback endpoints")
        @Nullable
        Object output,

        @NonNull
        @Schema(
                description = "Why a COMPLETED run produced the results it did. INSUFFICIENT_EVIDENCE means no model "
                        + "ran because required evidence was missing, unreadable, stale, or unauthorized — so no results "
                        + "means nothing was assessed, not that nothing was wrong. COALESCED means no model ran because a "
                        + "completed review had already answered every ready practice on exactly the same code — its "
                        + "answers are listed in answeredPractices, and this run assessed nothing anew. SUPERSEDED "
                        + "means the waiting attempt was replaced by an admitted newer review of the same author's "
                        + "current work, named in coveringJobId, which carries this review's practices and may still "
                        + "be waiting or running; the replacement makes no additional assessment, and earlier attempt "
                        + "history remains. REVIEWED means the model ran against sufficient evidence.")
        ReviewRunOutcome reviewOutcome,

        @Schema(description = "The newer review that replaced this one; set only when reviewOutcome is SUPERSEDED")
        @Nullable
        UUID coveringJobId,

        @NonNull
        @Schema(
                description =
                        "Frozen agent config at submit time (an INSTANCE-scoped connection's baseUrl is redacted to scheme://host; only a WORKSPACE-scoped connection's baseUrl is left intact)")
        Object configSnapshot,

        @Schema(
                description =
                        "Upstream model this job was admitted on, frozen at submit time (e.g. gpt-5.4-mini). Available from submission, unlike llmModel, which the runner reports only once the job has run.")
        @Nullable
        String model,

        @Schema(
                description = "Data handling tier of the slot whose model this job was admitted on, frozen at "
                        + "submit time. Absent when the snapshot names no tier that this server knows.")
        @Nullable
        DataHandlingTier dataHandlingTier,

        @Schema(description = "Container exit code") @Nullable
        Integer exitCode,

        @Schema(description = "Human-readable error message") @Nullable
        String errorMessage,

        @Schema(description = "Why a CANCELLED job was cancelled; available on job detail, absent otherwise") @Nullable
        AgentJobCancellationReason cancellationReason,

        @Schema(
                description = "The id the job's log lines and spans carry, to find them in the logs; available on"
                        + " job detail")
        @Nullable
        String traceId,

        @Schema(description = DeliveryStatus.DESCRIPTION) @Nullable
        DeliveryStatus deliveryStatus,

        @Schema(description = "Git provider comment/note ID for posted feedback") @Nullable
        String deliveryCommentId,

        @NonNull @Schema(description = "Number of retry attempts")
        Integer retryCount,

        @NonNull
        @Schema(
                description = "When this job becomes eligible to be claimed. In the future while the job is "
                        + "waiting — on a retry backoff, or on a hold. Read together with holdReason: a QUEUED job "
                        + "with availableAt in the future is waiting, not starved for workers.")
        Instant availableAt,

        @Schema(
                description = "Why a QUEUED job is waiting rather than eligible, when the reason is one an admin "
                        + "can undo. BUDGET = the payer is over its monthly LLM cap and the job resumes by itself once "
                        + "the cap is raised or the month rolls over. Absent means no such hold — a future availableAt "
                        + "is then an ordinary retry backoff.")
        @Nullable
        String holdReason,

        @NonNull @Schema(description = "Timestamp when the job was created")
        Instant createdAt,

        @Schema(
                description = "When a worker claimed the current attempt and began preparing it. A retry resets it, "
                        + "so it never describes an earlier attempt.")
        @Nullable
        Instant startedAt,

        @Schema(
                description = "When the current attempt finished preparation, immediately before its sandbox was "
                        + "invoked, if it got that far. A retry resets it; absent on runs from before it was recorded.")
        @Nullable
        Instant executionStartedAt,

        @Schema(description = "Timestamp when the job completed") @Nullable
        Instant completedAt,

        @Schema(
                description = "When this review first posted feedback on the work without waiting for an admin's"
                        + " approval; available on review detail, absent until then")
        @Nullable
        Instant firstFeedbackPostedAt,

        @Schema(
                description = "When a developer first read this review's feedback on their practice pages or in a"
                        + " conversation; available on review detail, absent until then")
        @Nullable
        Instant firstFeedbackReadAt,

        @Schema(
                description = "When the reviewed pull request or merge request was merged; available on review detail,"
                        + " absent for other work and for work that is not merged")
        @Nullable
        Instant workMergedAt,

        @Schema(description = "LLM model used (e.g. gpt-5.4-mini, openai/gpt-oss-120b)") @Nullable
        String llmModel,

        @Schema(
                description = "Model version/snapshot date (e.g. 2026-03-17). Only jobs from before the model catalog "
                        + "carry one; absent on everything newer.")
        @Nullable
        String llmModelVersion,

        @Schema(description = "Total LLM API calls (steps) during execution") @Nullable
        Integer llmTotalCalls,

        @Schema(description = "Total input tokens consumed") @Nullable
        Integer llmTotalInputTokens,

        @Schema(description = "Total output tokens generated") @Nullable
        Integer llmTotalOutputTokens,

        @Schema(description = "Total reasoning/thinking tokens") @Nullable
        Integer llmTotalReasoningTokens,

        @Schema(description = "Tokens read from prompt cache") @Nullable
        Integer llmCacheReadTokens,

        @Schema(description = "Tokens written to prompt cache") @Nullable
        Integer llmCacheWriteTokens,

        @Schema(
                description =
                        "Frozen generated-path policy and changed paths marked generated; available on review detail after evidence capture")
        @Nullable
        GeneratedPathReviewDTO generatedPaths,

        @Schema(
                description =
                        "Ready practices this review did not ask because a completed review had already answered them on "
                                + "the same code; available on review detail after evidence capture. Empty when every ready "
                                + "practice was asked.")
        @Nullable
        List<AnsweredPracticeDTO> answeredPractices,

        @Schema(
                description =
                        "When the whole review on the work was accepted. Independent of delivery and job/private completion; absent when no acceptance was recorded.")
        @Nullable
        Instant publicReviewAcceptedAt) {
    /**
     * What a review's detail adds to the job: when it first posted on the work, when a developer first read its
     * feedback, and when its work was merged.
     */
    public record ReviewMoments(
            @Nullable Instant firstFeedbackPostedAt,
            @Nullable Instant firstFeedbackReadAt,
            @Nullable Instant workMergedAt,
            @Nullable Instant publicReviewAcceptedAt) {
        public static final ReviewMoments NONE = new ReviewMoments(null, null, null, null);
    }

    public static AgentJobDTO from(AgentJob job, Target target, ReviewMoments moments) {
        JsonNode snapshot = job.getConfigSnapshot();
        return new AgentJobDTO(
                job.getId(),
                job.getJobType(),
                job.getStatus(),
                ReviewRunTargetDTO.from(target),
                job.getMetadata(),
                null,
                ReviewRunOutcome.fromJobOutput(job.getOutput()),
                ReviewRunOutcome.coveringJobId(job.getOutput()),
                redactInstanceBaseUrl(snapshot),
                snapshotString(snapshot, "upstreamModelId"),
                dataHandlingTier(snapshot),
                job.getExitCode(),
                job.getErrorMessage(),
                job.getCancellationReason(),
                job.getTraceId(),
                job.getDeliveryStatus(),
                job.getDeliveryCommentId(),
                job.getRetryCount(),
                job.getAvailableAt(),
                AgentJob.holdWhileQueued(job.getStatus(), job.getHoldReason()),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getExecutionStartedAt(),
                job.getCompletedAt(),
                moments.firstFeedbackPostedAt(),
                moments.firstFeedbackReadAt(),
                moments.workMergedAt(),
                job.getLlmModel(),
                job.getLlmModelVersion(),
                job.getLlmTotalCalls(),
                job.getLlmTotalInputTokens(),
                job.getLlmTotalOutputTokens(),
                job.getLlmTotalReasoningTokens(),
                job.getLlmCacheReadTokens(),
                job.getLlmCacheWriteTokens(),
                generatedPaths(job.getEvidenceSnapshot()),
                answeredPractices(job.getEvidenceSnapshot()),
                moments.publicReviewAcceptedAt());
    }

    /**
     * The listing's row, which carries every column this record renders and no transcript — the one
     * thing an entity page would have read per row and thrown away. Captured generated-path details
     * are loaded only for the individual review, not for its listing.
     */
    public static AgentJobDTO from(AgentJobRepository.AgentJobListRow row, Target target) {
        JsonNode snapshot = row.getConfigSnapshot();
        return new AgentJobDTO(
                row.getId(),
                row.getJobType(),
                row.getStatus(),
                ReviewRunTargetDTO.from(target),
                row.getMetadata(),
                null,
                ReviewRunOutcome.fromJobOutput(row.getOutput()),
                ReviewRunOutcome.coveringJobId(row.getOutput()),
                redactInstanceBaseUrl(snapshot),
                snapshotString(snapshot, "upstreamModelId"),
                dataHandlingTier(snapshot),
                row.getExitCode(),
                row.getErrorMessage(),
                null,
                null,
                row.getDeliveryStatus(),
                row.getDeliveryCommentId(),
                row.getRetryCount(),
                row.getAvailableAt(),
                AgentJob.holdWhileQueued(row.getStatus(), row.getHoldReason()),
                row.getCreatedAt(),
                row.getStartedAt(),
                row.getExecutionStartedAt(),
                row.getCompletedAt(),
                null,
                null,
                null,
                row.getLlmModel(),
                row.getLlmModelVersion(),
                row.getLlmTotalCalls(),
                row.getLlmTotalInputTokens(),
                row.getLlmTotalOutputTokens(),
                row.getLlmTotalReasoningTokens(),
                row.getLlmCacheReadTokens(),
                row.getLlmCacheWriteTokens(),
                null,
                null,
                row.getPublicReviewAcceptedAt());
    }

    private static @Nullable List<AnsweredPracticeDTO> answeredPractices(@Nullable JsonNode snapshot) {
        if (snapshot == null || !snapshot.has("answeredPractices")) return null;
        return StreamSupport.stream(snapshot.path("answeredPractices").spliterator(), false)
                .map(entry -> new AnsweredPracticeDTO(
                        entry.path("practiceSlug").asString(),
                        entry.path("revisionId").asLong(),
                        UUID.fromString(entry.path("reviewId").asString())))
                .toList();
    }

    private static @Nullable GeneratedPathReviewDTO generatedPaths(@Nullable JsonNode snapshot) {
        if (snapshot == null || !snapshot.has("generatedPaths")) return null;
        var policy = snapshot.path("generatedPaths");
        return new GeneratedPathReviewDTO(
                StreamSupport.stream(policy.path("patterns").spliterator(), false)
                        .map(JsonNode::asString)
                        .toList(),
                StreamSupport.stream(policy.path("paths").spliterator(), false)
                        .map(JsonNode::asString)
                        .toList());
    }

    /**
     * The audience here is a workspace admin, who may see the full {@code baseUrl} only of a
     * {@code WORKSPACE}-scoped connection they configured themselves. Anything else — an INSTANCE
     * connection, or a scope-less snapshot from a rolling upgrade — is cut back to {@code scheme://host}
     * so an operator's gateway or deployment path cannot leak.
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

    /** A value this server does not know, from a newer node during a rolling deploy, reads as absent. */
    private static @Nullable DataHandlingTier dataHandlingTier(JsonNode snapshot) {
        String tier = snapshotString(snapshot, "dataHandlingTier");
        return Arrays.stream(DataHandlingTier.values())
                .filter(value -> value.name().equals(tier))
                .findFirst()
                .orElse(null);
    }

    private static @Nullable String snapshotString(JsonNode snapshot, String field) {
        if (snapshot == null || !snapshot.has(field) || snapshot.get(field).isNull()) {
            return null;
        }
        return snapshot.get(field).asString();
    }
}
