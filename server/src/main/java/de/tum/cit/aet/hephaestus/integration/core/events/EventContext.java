package de.tum.cit.aet.hephaestus.integration.core.events;

import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.DataSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.ProcessingContext;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.LazyInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    @NonNull String correlationId,
    @Nullable GitProviderType providerType
) {
    private static final Logger log = LoggerFactory.getLogger(EventContext.class);

    /**
     * Creates an EventContext from a ProcessingContext.
     *
     * <p>Note: The provider type is resolved eagerly here because the ProcessingContext
     * may hold a detached JPA proxy that is inaccessible outside the original session.
     */
    public static EventContext from(ProcessingContext ctx) {
        // TODO: Resolve providerType eagerly in ProcessingContext construction instead
        //  of catching LazyInitializationException here. See ProcessingContext Javadoc.
        GitProviderType resolvedType = null;
        if (ctx.provider() != null) {
            try {
                resolvedType = ctx.provider().getType();
            } catch (LazyInitializationException e) {
                log.debug(
                    "Could not resolve provider type from detached proxy, correlationId={}: {}",
                    ctx.correlationId(),
                    e.getMessage()
                );
            }
        }
        return new EventContext(
            UUID.randomUUID(),
            Instant.now(),
            ctx.scopeId(),
            ctx.repository() != null ? RepositoryRef.from(ctx.repository()) : null,
            ctx.source(),
            ctx.webhookAction(),
            ctx.correlationId(),
            resolvedType
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
            UUID.randomUUID().toString(),
            null
        );
    }

    /**
     * Creates an EventContext for sync operations with a known provider type.
     */
    public static EventContext forSync(Long scopeId, RepositoryRef repository, GitProviderType providerType) {
        return new EventContext(
            UUID.randomUUID(),
            Instant.now(),
            scopeId,
            repository,
            DataSource.GRAPHQL_SYNC,
            null,
            UUID.randomUUID().toString(),
            providerType
        );
    }

    public boolean isWebhook() {
        return source == DataSource.WEBHOOK;
    }

    public boolean isSync() {
        return source == DataSource.GRAPHQL_SYNC;
    }

    public boolean isGitHub() {
        return providerType == GitProviderType.GITHUB;
    }

    public boolean isGitLab() {
        return providerType == GitProviderType.GITLAB;
    }
}
