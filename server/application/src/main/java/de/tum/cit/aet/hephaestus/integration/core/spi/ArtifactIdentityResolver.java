package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.Collection;
import java.util.Map;

/**
 * Turns the ledger's {@code (kind, id)} into something a person recognises — one bean per kind,
 * contributed by the module that owns the mirror.
 *
 * <p>Optional by design: a kind with no resolver still traces, named by its
 * {@link ArtifactDescriptor#displayName()} instead of its title — worse for a reader, but better than
 * making a domain unbindable until somebody also writes its read surface.
 *
 * <p>Callers pass ids already established to belong to the workspace — this is a labelling step, not an
 * authorization one, and must not re-derive tenancy from a mirror whose ownership runs through a monitor
 * mapping rather than a column.
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
