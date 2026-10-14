package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.time.Instant;
import java.util.UUID;

public interface ItemWriter {
    ItemRef upsert(ItemRegistration registration);

    /** Holds the workspace item lock until the caller transaction ends. Requires an active transaction. */
    void lock(long workspaceId, long itemId);

    UUID record(ItemEventInput event);

    boolean remove(long workspaceId, long itemId, Instant at);

    boolean restore(long workspaceId, long itemId);
}
