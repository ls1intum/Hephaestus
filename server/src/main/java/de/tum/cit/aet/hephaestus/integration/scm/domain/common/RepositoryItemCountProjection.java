package de.tum.cit.aet.hephaestus.integration.scm.domain.common;

/**
 * Shared projection for the "count my rows, grouped by repository, for these repository ids" queries
 * that back the sync-observability per-entity-class breakdown.
 *
 * <p>One shared shape rather than a private copy per repository: the reader assembling the breakdown
 * treats every class identically, and a per-repository projection type would force it to either
 * duplicate the collect-to-map per class or erase them all to {@code Object[]}.
 *
 * <p>Every query returning this must be batched over a collection of repository ids — the read model it
 * serves renders every resource of a connection on one page load, so a per-repository query here is an
 * N+1 by construction.
 */
public interface RepositoryItemCountProjection {
    Long getRepositoryId();
    Long getItemCount();
}
