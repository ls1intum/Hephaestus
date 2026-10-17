package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.UUID;

public record ReviewRequested(UUID reviewId, long workspaceId) {}
