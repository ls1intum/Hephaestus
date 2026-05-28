package de.tum.cit.aet.hephaestus.integration.scm.domain.repository.events;

/**
 * Synchronously published immediately before {@code repository.delete(...)}. Vendor
 * adapters subscribe to cascade-delete dependents that don't have a DB-level FK
 * (e.g. GitHub Projects V2 polymorphic ownership rows). Listeners must run in the
 * same transaction as the delete.
 */
public record RepositoryAboutToBeDeletedEvent(long repositoryId) {}
