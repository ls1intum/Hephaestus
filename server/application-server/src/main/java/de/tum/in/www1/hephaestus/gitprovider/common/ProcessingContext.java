package de.tum.in.www1.hephaestus.gitprovider.common;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * Unified context for processing GitHub data from any source (sync or webhook).
 * <p>
 * This context carries all the information needed to process data consistently,
 * regardless of whether it came from a scheduled GraphQL sync or a webhook
 * event.
 */
public record ProcessingContext(
    Long workspaceId,
    Repository repository,
    Instant startedAt,
    String correlationId,
    @Nullable String webhookAction,
    Source source
) {
    /**
     * The source of the data being processed.
     */
    public enum Source {
        /** Data from scheduled GraphQL synchronization. */
        GRAPHQL_SYNC,
        /** Data from a webhook event via NATS. */
        WEBHOOK,
    }

    /**
     * Creates a context for scheduled sync operations.
     */
    public static ProcessingContext forSync(Long workspaceId, Repository repository) {
        return new ProcessingContext(
            workspaceId,
            repository,
            Instant.now(),
            UUID.randomUUID().toString(),
            null,
            Source.GRAPHQL_SYNC
        );
    }

    /**
     * Creates a context for webhook event processing.
     */
    public static ProcessingContext forWebhook(Long workspaceId, Repository repository, String action) {
        return new ProcessingContext(
            workspaceId,
            repository,
            Instant.now(),
            UUID.randomUUID().toString(),
            action,
            Source.WEBHOOK
        );
    }

    /**
     * Checks if this processing was triggered by a webhook.
     */
    public boolean isWebhook() {
        return source == Source.WEBHOOK;
    }

    /**
     * Checks if this processing was triggered by a scheduled sync.
     */
    public boolean isSync() {
        return source == Source.GRAPHQL_SYNC;
    }
}
