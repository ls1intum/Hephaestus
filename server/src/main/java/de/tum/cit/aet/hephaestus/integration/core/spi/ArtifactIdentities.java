package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.Collection;
import java.util.Map;

/**
 * Names artifacts for a surface that has only their ledger identity.
 *
 * <p>A port so the practices module can label what it traces without importing a mirror, a vendor, or
 * the integration framework — the same shape and the same reason as {@link SignalCoverage}. The answer
 * is always total: a kind nobody resolves comes back named after its kind rather than missing, because
 * a trace whose rows disappear when a resolver is absent would reintroduce the silence it exists to
 * remove.
 */
public interface ArtifactIdentities {
    /**
     * @return an identity for every requested id, resolved where a resolver exists and
     *         {@link ArtifactIdentity#unresolved} where one does not
     */
    Map<Long, ArtifactIdentity> resolve(long workspaceId, ArtifactKind kind, Collection<Long> artifactIds);
}
