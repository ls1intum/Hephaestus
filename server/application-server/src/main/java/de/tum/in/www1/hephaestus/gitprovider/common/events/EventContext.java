package de.tum.in.www1.hephaestus.gitprovider.common.events;

import de.tum.in.www1.hephaestus.gitprovider.common.DataSource;
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
    @Nullable Long scopeId,
    @Nullable RepositoryRef repository,
    @NonNull DataSource source,
    @Nullable String webhookAction,
    @NonNull String correlationId
) {
    /**
     * Creates an EventContext from a ProcessingContext.
     */
    public static EventContext from(ProcessingContext ctx) {
        return new EventContext(
            UUID.randomUUID(),
            Instant.now(),
            ctx.scopeId(),
            ctx.repository() != null ? RepositoryRef.from(ctx.repository()) : null,
            ctx.source(),
            ctx.webhookAction(),
            ctx.correlationId()
        );
    }

    /**
     * Creates an EventContext for sync operations.
     */
    public static EventContext forSync(Long scopeId, RepositoryRef repository) {
        return new EventContext(
            UUID.randomUUID(),
            Instant.now(),
            scopeId,
            repository,
            DataSource.GRAPHQL_SYNC,
            null,
            UUID.randomUUID().toString()
        );
    }

    public boolean isWebhook() {
        return source == DataSource.WEBHOOK;
    }

    public boolean isSync() {
        return source == DataSource.GRAPHQL_SYNC;
    }
}
