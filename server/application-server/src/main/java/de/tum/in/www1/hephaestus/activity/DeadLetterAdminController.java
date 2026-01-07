package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.security.EnsureSuperAdminUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Administrative endpoints for dead letter event management.
 *
 * <p>Provides operational tooling for incident response without requiring
 * direct database access. All endpoints are restricted to admin users.
 *
 * <h3>Usage</h3>
 * <ol>
 *   <li>Monitor pending dead letters via {@code GET /pending}</li>
 *   <li>Investigate failures via {@code GET /{id}}</li>
 *   <li>Retry resolvable issues via {@code POST /{id}/retry}</li>
 *   <li>Discard unfixable issues via {@code POST /{id}/discard}</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/admin/dead-letters")
@RequiredArgsConstructor
@EnsureSuperAdminUser
@Tag(name = "Dead Letter Admin", description = "Administrative operations for failed activity events")
public class DeadLetterAdminController {

    private final DeadLetterEventService deadLetterEventService;

    @GetMapping("/pending")
    @Operation(
        summary = "List pending dead letters",
        description = "Returns all pending dead letters, ordered by creation time (oldest first)"
    )
    @ApiResponse(responseCode = "200", description = "List of pending dead letters")
    public ResponseEntity<List<DeadLetterSummaryDTO>> getPending(
        @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit
    ) {
        List<DeadLetterEvent> pending = deadLetterEventService.findPendingForRetry(limit);
        List<DeadLetterSummaryDTO> summaries = pending.stream().map(this::toSummary).toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get dead letter details",
        description = "Returns full details of a dead letter event for investigation"
    )
    @ApiResponse(responseCode = "200", description = "Dead letter details")
    @ApiResponse(responseCode = "404", description = "Dead letter not found")
    public ResponseEntity<DeadLetterDetailDTO> getById(@PathVariable UUID id) {
        DeadLetterEvent event = deadLetterEventService.findById(id);
        return ResponseEntity.ok(toDetail(event));
    }

    @PostMapping("/{id}/discard")
    @Operation(
        summary = "Discard a dead letter",
        description = "Marks a dead letter as discarded (will not be retried)"
    )
    @ApiResponse(responseCode = "200", description = "Dead letter discarded")
    @ApiResponse(responseCode = "404", description = "Dead letter not found")
    public ResponseEntity<Void> discard(@PathVariable UUID id, @Valid @RequestBody DiscardReasonDTO reason) {
        deadLetterEventService.discard(id, reason.reason());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry a dead letter", description = "Attempts to re-record the failed activity event")
    @ApiResponse(responseCode = "200", description = "Retry result")
    @ApiResponse(responseCode = "404", description = "Dead letter not found")
    public ResponseEntity<RetryResultDTO> retry(@PathVariable UUID id) {
        DeadLetterEventService.RetryResult result = deadLetterEventService.retry(id);
        return ResponseEntity.ok(new RetryResultDTO(result.success(), result.message()));
    }

    @GetMapping("/stats")
    @Operation(
        summary = "Get dead letter statistics",
        description = "Returns counts of dead letters by status and event type"
    )
    @ApiResponse(responseCode = "200", description = "Statistics")
    public ResponseEntity<DeadLetterStatsDTO> getStats() {
        DeadLetterEventService.DeadLetterStats stats = deadLetterEventService.getStats();
        return ResponseEntity.ok(
            new DeadLetterStatsDTO(stats.pending(), stats.resolved(), stats.discarded(), stats.byEventType())
        );
    }

    // DTO conversions

    private DeadLetterSummaryDTO toSummary(DeadLetterEvent event) {
        return new DeadLetterSummaryDTO(
            event.getId(),
            event.getEventType(),
            event.getTargetId(),
            event.getErrorType(),
            event.getCreatedAt(),
            event.getRetryCount()
        );
    }

    private DeadLetterDetailDTO toDetail(DeadLetterEvent event) {
        return new DeadLetterDetailDTO(
            event.getId(),
            event.getWorkspaceId(),
            event.getEventType(),
            event.getTargetType(),
            event.getTargetId(),
            event.getXp(),
            event.getSourceSystem(),
            event.getErrorType(),
            event.getErrorMessage(),
            // Stack trace intentionally omitted for security
            event.getStatus(),
            event.getCreatedAt(),
            event.getResolvedAt(),
            event.getRetryCount()
        );
    }

    // DTOs

    public record DeadLetterSummaryDTO(
        UUID id,
        ActivityEventType eventType,
        Long targetId,
        String errorType,
        Instant createdAt,
        int retryCount
    ) {}

    public record DeadLetterDetailDTO(
        UUID id,
        Long workspaceId,
        ActivityEventType eventType,
        String targetType,
        Long targetId,
        Double xp,
        String sourceSystem,
        String errorType,
        String errorMessage,
        // Stack trace intentionally omitted from API response for security
        DeadLetterEvent.Status status,
        Instant createdAt,
        Instant resolvedAt,
        int retryCount
    ) {}

    public record DiscardReasonDTO(
        @NotBlank(message = "Reason is required") @Size(
            max = 500,
            message = "Reason must be at most 500 characters"
        ) String reason
    ) {}

    public record RetryResultDTO(boolean success, String message) {}

    public record DeadLetterStatsDTO(long pending, long resolved, long discarded, Map<String, Long> byEventType) {}
}
