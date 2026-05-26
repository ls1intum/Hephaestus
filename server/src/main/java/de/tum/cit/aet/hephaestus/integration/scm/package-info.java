/**
 * Source-Control Management (SCM) domain — the provider-agnostic git domain model.
 *
 * <p>This is the "git" half of the {@code integration} framework: the shared, vendor-neutral
 * entity model for code-collaboration platforms (commits, repositories, pull requests,
 * reviews, review threads, review comments, issues, issue comments, discussions, labels,
 * milestones, teams, organizations, users). Vendor adapters (GitHub, GitLab, …) live as
 * siblings under {@code integration/github/} and {@code integration/gitlab/} and write
 * into these tables via a single processor per subdomain — entities are upserted
 * idempotently from both webhook events and historical GraphQL sync.
 *
 * <p>The {@code scm} name pairs with the other vendor-family roots:
 * {@code integration/slack}, {@code integration/outline}, … — each is a domain, not a
 * "providers" container.
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
 * through {@link de.tum.cit.aet.hephaestus.integration.scm.common.PostgresStringUtils}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "SCM",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package de.tum.cit.aet.hephaestus.integration.scm;
