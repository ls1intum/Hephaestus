package de.tum.cit.aet.hephaestus.agent.job;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "Observed closed turns from retained runner output, not live or complete execution telemetry")
public record ReviewDiagnosticsDTO(@NonNull List<ReviewPhaseDiagnosticsDTO> phases) {

    public enum ReviewPhaseDTO {
        PRACTICE,
        PUBLIC_REVIEW,
        PRIVATE_FEEDBACK,
        UNKNOWN
    }

    public enum ModelFailureKindDTO {
        HTTP_ERROR,
        CONNECTION_TIMEOUT,
        CONNECTION_ERROR,
        STREAM_INCOMPLETE,
        FINISH_REASON_ERROR,
        STREAM_ERROR_EVENT,
        RESPONSE_FAILED,
        RESPONSE_STATUS_ERROR,
        TOOL_CALL_INCOMPLETE,
        ABORTED,
        UNKNOWN
    }

    public enum ModelFailureSourceDTO {
        ADAPTER,
        MISSING,
        INVALID
    }

    public enum ModelFailurePhaseDTO {
        REQUEST,
        RESPONSE_BODY
    }

    @Schema(description = "One fixed review phase; UNKNOWN includes legacy turns without an explicit recognized phase")
    public record ReviewPhaseDiagnosticsDTO(
            @NonNull ReviewPhaseDTO phase,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Number of retained closed turn records")
            int recordedTurns,

            @Schema(
                    description =
                            "Observed assistant/model calls; absent if any count is unknown or the sum exceeds the bound")
            @Nullable
            Long assistantCalls,

            @Schema(
                    description =
                            "Sum of observed closed turn durations in milliseconds; absent if any duration is unknown or the sum overflows")
            @Nullable
            Long elapsedMs,

            @Schema(description = "Adapter span totals; absent if any turn has absent, partial or invalid span totals")
            @Nullable
            ModelCallTimingDTO modelCallTiming,

            @Schema(
                    description =
                            "Closed failure kinds recorded across these turns; empty means none recorded, absent means a turn has no readable counts")
            @Nullable
            List<ModelFailureCountDTO> modelFailures,

            @Schema(
                    description =
                            "Safe failure of the last model call in the latest closed turn of this phase; absent after recovery or when unknown")
            @Nullable
            TerminalModelFailureDTO finalModelFailure) {}

    public record ModelCallTimingDTO(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Calls with a recorded valid adapter span; does not imply all calls were timed")
            long timedCalls,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Timed calls with a recorded valid SDK response time")
            long respondedCalls,

            @Schema(description = "Sum of adapter spans in milliseconds; absent when no span was recorded") @Nullable
            Long elapsedMs,

            @Schema(
                    description =
                            "Sum of failed or aborted adapter spans in milliseconds; absent when no span was recorded")
            @Nullable
            Long failedElapsedMs,

            @Schema(description = "Largest recorded adapter span in milliseconds; absent when no span was recorded")
            @Nullable
            Long maxElapsedMs,

            @Schema(
                    description =
                            "Sum of request-to-SDK-response times in milliseconds; absent when no response time was recorded")
            @Nullable
            Long responseMs) {}

    public record ModelFailureCountDTO(
            @NonNull ModelFailureKindDTO kind,

            @Schema(
                    description =
                            "Observed failed or aborted calls; absent if a supplied count is invalid or its sum overflows")
            @Nullable
            Long count) {}

    public record TerminalModelFailureDTO(
            @NonNull ModelFailureKindDTO kind,
            @Nullable ModelFailureSourceDTO source,
            @Nullable ModelFailurePhaseDTO phase,

            @Schema(description = "HTTP status from 400 through 599, for HTTP_ERROR only") @Nullable
            Integer status,

            @Schema(description = "Adapter failure time in epoch milliseconds; absent if unknown") @Nullable
            Long at,

            @Schema(description = "Whether the native retry ended unsuccessfully; absent if no valid flag was recorded")
            @Nullable
            Boolean retryEndedUnsuccessfully) {}
}
