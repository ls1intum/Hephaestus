package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.Collection;
import java.util.Map;

/**
 * Turns the ledger's {@code (kind, id)} into something a person recognises — one bean per kind,
 * contributed by the module that owns the mirror.
 *
 * <p>Optional by design. A kind with no resolver still traces; it is named by its
 * {@link ArtifactDescriptor#displayName()} instead of its title, which is worse for a reader and
 * better than refusing to answer. Making it mandatory would mean a new domain could not become
 * bindable until somebody also wrote its read surface, and the contract's whole claim is that a
 * domain arrives in one module's PR.
 *
 * <p>Callers pass ids they already established belong to the workspace — a resolver is a labelling
 * step, not an authorization one, and must not be asked to re-derive tenancy from a mirror whose
 * ownership runs through a monitor mapping rather than a column.
 */
public interface ArtifactIdentityResolver {
    /** The one kind this resolver labels. */
    ArtifactKind kind();

    /**
     * Labels as many of these ids as still exist.
     *
     * @return identities by id; an id the mirror no longer holds is simply absent, never a null value
     */
    Map<Long, ArtifactIdentity> resolve(long workspaceId, Collection<Long> artifactIds);
}
