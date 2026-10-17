package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import java.util.List;
import java.util.Set;

/** Opaque native mirror ids used only by the owning provider's privacy contributors. */
public interface ProviderPersonLookup {
    Set<String> providerTypes();

    List<Long> mirrorIds(PersonIdentity identity);
}
