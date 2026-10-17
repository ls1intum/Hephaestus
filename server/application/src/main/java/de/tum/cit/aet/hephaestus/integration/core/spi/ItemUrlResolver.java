package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Optional;

/** Resolves a provider address to an active item in the requesting workspace. */
public interface ItemUrlResolver {
    Optional<ItemRef> resolve(long workspaceId, String url);
}
