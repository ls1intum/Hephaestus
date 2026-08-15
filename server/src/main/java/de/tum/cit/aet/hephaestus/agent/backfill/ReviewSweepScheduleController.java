package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.agent.backfill.dto.CreateReviewSweepScheduleRequestDTO;
import de.tum.cit.aet.hephaestus.agent.backfill.dto.ReviewSweepScheduleDTO;
import de.tum.cit.aet.hephaestus.agent.backfill.dto.UpdateReviewSweepScheduleRequestDTO;
import de.tum.cit.aet.hephaestus.core.AuditLedger;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Recurring sweeps: the standing instruction to review recent work whether or not anything announced it.
 *
 * <p>Unlike a backfill campaign there is no preflight and no confirmation step. A sweep's window is
 * bounded by rule to the recent past, so what it can cost on any one night is bounded by how much work
 * the workspace actually did — and asking for a click each night would leave a queue nobody clears.
 * Creating the schedule is the authorisation, and it is on the configuration audit trail as one.
 */
@WorkspaceScopedController
@RequestMapping("/practices/sweep-schedules")
@Tag(name = "Practice Review Sweep", description = "Recurring reviews of recent work")
@RequiredArgsConstructor
@Validated
public class ReviewSweepScheduleController {

    private final ReviewSweepScheduleService reviewSweepScheduleService;

    @GetMapping
    @Operation(summary = "List this workspace's sweep schedules")
    @ApiResponse(responseCode = "200", description = "Schedules returned")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<List<ReviewSweepScheduleDTO>> listSweepSchedules(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(reviewSweepScheduleService.list(workspaceContext));
    }

    @PostMapping
    @Operation(
        summary = "Start sweeping a kind of work on a cadence",
        description = "Authorises the recurring spend. The first sweep runs within the hour."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Schedule created",
        content = @Content(schema = @Schema(implementation = ReviewSweepScheduleDTO.class))
    )
    @ApiResponse(
        responseCode = "400",
        description = "The kind cannot be swept, or the lookback is longer than the cadence allows",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @ApiResponse(
        responseCode = "409",
        description = "This workspace already sweeps that kind of work",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "REVIEW_SWEEP_SCHEDULE")
    public ResponseEntity<ReviewSweepScheduleDTO> createSweepSchedule(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody CreateReviewSweepScheduleRequestDTO request
    ) {
        ReviewSweepScheduleDTO schedule = reviewSweepScheduleService.create(workspaceContext, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{scheduleId}")
            .buildAndExpand(schedule.id())
            .toUri();
        return ResponseEntity.created(location).body(schedule);
    }

    @PutMapping("/{scheduleId}")
    @Operation(
        summary = "Replace a sweep schedule's terms",
        description = "Changes the cadence, the window, or whether the sweep runs at all."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Schedule updated",
        content = @Content(schema = @Schema(implementation = ReviewSweepScheduleDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "No such schedule in this workspace",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "REVIEW_SWEEP_SCHEDULE")
    public ResponseEntity<ReviewSweepScheduleDTO> replaceSweepSchedule(
        WorkspaceContext workspaceContext,
        @PathVariable UUID scheduleId,
        @Valid @RequestBody UpdateReviewSweepScheduleRequestDTO request
    ) {
        return ResponseEntity.ok(reviewSweepScheduleService.replace(workspaceContext, scheduleId, request));
    }

    @DeleteMapping("/{scheduleId}")
    @Operation(
        summary = "Stop sweeping",
        description = "Removes the instruction. Campaigns it already opened keep their records."
    )
    @ApiResponse(responseCode = "204", description = "Schedule removed")
    @ApiResponse(
        responseCode = "404",
        description = "No such schedule in this workspace",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "REVIEW_SWEEP_SCHEDULE")
    public ResponseEntity<Void> deleteSweepSchedule(WorkspaceContext workspaceContext, @PathVariable UUID scheduleId) {
        reviewSweepScheduleService.delete(workspaceContext, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
