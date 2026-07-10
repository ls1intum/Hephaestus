package de.tum.cit.aet.hephaestus.activity;

import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Command object for recording activity events.
 *
 * <p>Replaces the multi-parameter record() method with a cleaner builder pattern.
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
    @NotNull Long targetId
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
        Long targetId
    ) {
        return RecordActivityCommand.builder()
            .workspaceId(workspaceId)
            .eventType(eventType)
            .occurredAt(occurredAt)
            .actor(actor)
            .targetType(targetType)
            .targetId(targetId)
            .build();
    }
}
