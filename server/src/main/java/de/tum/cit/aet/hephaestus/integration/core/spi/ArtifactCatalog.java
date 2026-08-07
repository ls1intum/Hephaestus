package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.Collection;
import java.util.Optional;

/**
 * Read access to what artifact kinds exist and what can happen to them.
 *
 * <p>A port because the registry that answers it lives in the integration framework, while the module
 * that needs the answer — the practices authoring surface — may depend only on the contract. That
 * boundary is the point: the set of kinds a practice can be written against is now whatever the
 * registered descriptors declare, so a new domain becomes authorable by shipping a descriptor rather
 * than by editing a list in the practices module.
 */
public interface ArtifactCatalog {
    /** Every declared kind, in no guaranteed order. */
    Collection<ArtifactDescriptor> all();

    /** The descriptor for one kind, empty when no module declares it. */
    Optional<ArtifactDescriptor> descriptorFor(ArtifactKind kind);
}
