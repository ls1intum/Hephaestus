package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

@NamedInterface(value = "item-events", propagate = false)
public record ItemEventRecorded(long workspaceId, UUID eventId, long itemId) {}
