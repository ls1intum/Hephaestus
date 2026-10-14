package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.UUID;

public record ReviewCompleted(UUID reviewId, long workspaceId) {}
