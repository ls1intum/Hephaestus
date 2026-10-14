package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import java.util.UUID;

public record ItemEventRecorded(long workspaceId, UUID eventId, long itemId) {}
