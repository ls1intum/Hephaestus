package de.tum.cit.aet.hephaestus.integration.scm.common;

/**
 * Represents the source of data being processed in the sync engine.
 * <p>
 * This enum is shared between {@link ProcessingContext} and
 * {@link de.tum.cit.aet.hephaestus.integration.events.EventContext}
 * to avoid duplication.
 */
public enum DataSource {
    /** Data from scheduled GraphQL synchronization. */
    GRAPHQL_SYNC,
    /** Data from scheduled REST API synchronization. */
    REST_SYNC,
    /** Data from a webhook event via NATS. */
    WEBHOOK,
}
