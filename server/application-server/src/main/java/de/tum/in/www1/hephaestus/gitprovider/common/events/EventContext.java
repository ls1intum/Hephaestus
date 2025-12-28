package de.tum.in.www1.hephaestus.gitprovider.common.events;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Immutable context for domain events - safe for async handling.
 */
public record EventContext(
    @NonNull UUID eventId,
    @NonNull Instant occurredAt,
    @Nullable Long workspaceId,
    @Nullable RepositoryRef repository,
    @NonNull Source source,
    @Nullable String webhookAction,
    @NonNull String correlationId
) {
    public enum Source {
        GRAPHQL_SYNC,
        WEBHOOK,
    }

    /**
     * Creates an EventContext from a ProcessingContext.
     */
    public static EventContext from(ProcessingContext ctx) {
        return new EventContext(
            UUID.randomUUID(),
            Instant.now(),
            ctx.workspaceId(),
            ctx.repository() != null ? RepositoryRef.from(ctx.repository()) : null,
            ctx.isWebhook() ? Source.WEBHOOK : Source.GRAPHQL_SYNC,
            ctx.webhookAction(),
            ctx.correlationId()
        );
    }

    /**
     * Creates an EventContext for sync operations.
     */
    public static EventContext forSync(Long workspaceId, RepositoryRef repository) {
        return new EventContext(
            UUID.randomUUID(),
            Instant.now(),
            workspaceId,
            repository,
            Source.GRAPHQL_SYNC,
            null,
            UUID.randomUUID().toString()
        );
    }

    public boolean isWebhook() {
        return source == Source.WEBHOOK;
    }

    public boolean isSync() {
        return source == Source.GRAPHQL_SYNC;
    }
}
