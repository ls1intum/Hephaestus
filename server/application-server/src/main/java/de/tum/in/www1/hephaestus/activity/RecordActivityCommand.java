package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import org.springframework.lang.Nullable;

/**
 * Command object for recording activity events.
 *
 * <p>Replaces the 10-parameter record() method with a cleaner builder pattern.
 * Provides input validation via Bean Validation annotations.
 *
 * <h4>Usage</h4>
 * <pre>{@code
 * var command = RecordActivityCommand.builder()
 *     .workspaceId(123L)
 *     .eventType(ActivityEventType.REVIEW_SUBMITTED)
 *     .occurredAt(Instant.now())
 *     .actor(user)
 *     .repository(repo)
 *     .targetType(ActivityTargetType.REVIEW)
 *     .targetId(456L)
 *     .xp(5.0)
 *     .sourceSystem(SourceSystem.GITHUB)
 *     .build();
 *
 * activityEventService.record(command);
 * }</pre>
 */
@Builder
public record RecordActivityCommand(
    @NotNull Long workspaceId,
    @NotNull ActivityEventType eventType,
    @NotNull Instant occurredAt,
    @Nullable User actor,
    @Nullable Repository repository,
    @NotNull ActivityTargetType targetType,
    @NotNull Long targetId,
    @Min(0) double xp,
    @NotNull SourceSystem sourceSystem,
    @Nullable Map<String, Object> payload
) {
    /**
     * Create a simple command for events without a repository context.
     */
    public static RecordActivityCommand simple(
        Long workspaceId,
        ActivityEventType eventType,
        Instant occurredAt,
        User actor,
        ActivityTargetType targetType,
        Long targetId,
        double xp,
        SourceSystem sourceSystem
    ) {
        return RecordActivityCommand.builder()
            .workspaceId(workspaceId)
            .eventType(eventType)
            .occurredAt(occurredAt)
            .actor(actor)
            .targetType(targetType)
            .targetId(targetId)
            .xp(xp)
            .sourceSystem(sourceSystem)
            .build();
    }
}
