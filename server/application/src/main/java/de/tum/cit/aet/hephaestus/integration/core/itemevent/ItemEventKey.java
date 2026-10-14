package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;

public record ItemEventKey(long workspaceId, long itemId, ItemEventName name, String itemVersion) {
    public ItemEventKey {
        if (workspaceId <= 0 || itemId <= 0 || itemVersion.isBlank() || itemVersion.length() > 1024)
            throw new IllegalArgumentException("Invalid item event key");
    }
}
