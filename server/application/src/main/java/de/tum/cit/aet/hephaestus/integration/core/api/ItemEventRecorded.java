package de.tum.cit.aet.hephaestus.integration.core.api;

import java.util.UUID;

public record ItemEventRecorded(long workspaceId, UUID eventId, long itemId) {}
