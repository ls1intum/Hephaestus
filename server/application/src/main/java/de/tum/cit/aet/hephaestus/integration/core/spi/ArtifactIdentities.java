package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.Collection;
import java.util.Map;

/**
 * Names artifacts for a surface that has only their ledger identity.
 *
 * <p>A port so the practices module can label what it traces without importing a mirror, a vendor, or
 * the integration framework.
 */
public interface ArtifactIdentities {
    /**
     * @return an identity for every requested id, {@link ArtifactIdentity#unresolved} where no resolver exists
     */
    Map<Long, ArtifactIdentity> resolve(long workspaceId, ArtifactKind kind, Collection<Long> artifactIds);
}
