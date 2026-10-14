package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.time.Instant;
import java.util.UUID;

public interface ItemWriter {
    ItemRef upsert(ItemRegistration registration);

    UUID record(ItemEventInput event);

    boolean remove(long workspaceId, long itemId, Instant at);

    boolean restore(long workspaceId, long itemId);
}
