package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;

public record RoleHolder(AboutRole role, PersonIdentity identity, boolean human) {}
