package de.tum.in.www1.hephaestus.gitprovider.common;

/**
 * Represents the source of data being processed in the sync engine.
 * <p>
 * This enum is shared between {@link ProcessingContext} and
 * {@link de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext}
 * to avoid duplication.
 */
public enum DataSource {
    /** Data from scheduled GraphQL synchronization. */
    GRAPHQL_SYNC,
    /** Data from a webhook event via NATS. */
    WEBHOOK,
}
