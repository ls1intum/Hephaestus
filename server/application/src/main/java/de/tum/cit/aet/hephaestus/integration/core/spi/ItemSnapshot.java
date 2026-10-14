package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import java.util.Map;

public record ItemSnapshot(
        ItemRef item,
        ItemVersion version,
        IntegrationRef integration,
        Map<String, String> state,
        List<RoleHolder> roles,
        ItemLabel label) {
    public ItemSnapshot {
        state = Map.copyOf(state);
        roles = List.copyOf(roles);
    }
}
