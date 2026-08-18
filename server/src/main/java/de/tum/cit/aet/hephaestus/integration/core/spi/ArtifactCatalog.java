package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.Collection;
import java.util.Optional;

/**
 * Read access to what artifact kinds exist and what can happen to them.
 *
 * <p>A port because the registry that answers it lives in the integration framework while the authoring
 * surface that asks may depend only on the contract. The set of kinds a practice can be written against
 * is therefore whatever the registered descriptors declare: a new domain becomes authorable by shipping
 * a descriptor, not by editing a list in the practices module.
 */
public interface ArtifactCatalog {
    /** Every declared kind, in no guaranteed order. */
    Collection<ArtifactDescriptor> all();

    /** The descriptor for one kind, empty when no module declares it. */
    Optional<ArtifactDescriptor> descriptorFor(ArtifactKind kind);
}
