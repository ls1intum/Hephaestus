/**
 * Provider-agnostic git domain model + provider-specific ingest pipelines.
 *
 * <p>Domain entities (PullRequest, Issue, Review, …) live at the top level of each
 * subdomain. Provider implementations (GitHub, GitLab) live under {@code github/} and
 * {@code gitlab/} subdirectories within each subdomain and share a single processor that
 * handles both webhook events and GraphQL sync — entities are upserted idempotently.
 *
 * <p>Cross-module coupling goes through:
 * <ul>
 *   <li>{@link de.tum.cit.aet.hephaestus.integration.spi} — service-provider
 *       interfaces consumed by feature modules</li>
 *   <li>{@link de.tum.cit.aet.hephaestus.integration.events} — {@code DomainEvent}s
 *       published by processors; consumers react in-process</li>
 * </ul>
 *
 * <p>Logging always sanitizes external strings via
 * {@link de.tum.cit.aet.hephaestus.core.LoggingUtils#sanitizeForLog}; DB inserts go
 * through {@link de.tum.cit.aet.hephaestus.gitprovider.common.PostgresStringUtils}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Git Provider",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package de.tum.cit.aet.hephaestus.gitprovider;
